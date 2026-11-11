package com.f7.downloadmanager.engine

import com.f7.downloadmanager.core.DownloadItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Non-DRM HLS downloader. Fetches the media playlist, downloads every segment in order,
 * decrypts AES-128 segments when a key is present, and joins the result into one playable
 * file (fragmented MP4 when an EXT-X-MAP init segment exists, otherwise MPEG-TS).
 * SAMPLE-AES / DRM protected streams are rejected instead of producing a broken file.
 *
 * Progress is reported in real bytes plus the segment counters, so the UI can show a
 * meaningful speed and an honest "12 / 240 segments" readout.
 */
internal class HlsDownloader(
    private val item: DownloadItem,
    private val staging: File,
    private val control: DownloadControl,
    private val limiter: TokenBucket,
    private val onProgress: (bytes: Long, doneSegments: Int, totalSegments: Int) -> Unit,
) {
    data class Outcome(val file: File, val name: String, val mime: String, val segments: Int)

    private data class HlsKey(val method: String, val uri: String, val iv: ByteArray?)
    private data class PlaylistSegment(val uri: String, val key: HlsKey?, val sequence: Long)

    fun run(): Outcome {
        var mediaUrl = item.url
        var text = Http.getText(mediaUrl, item.headers)
        var guard = 0
        while (text.contains("#EXT-X-STREAM-INF") && guard++ < 3) {
            val variants = parseVariants(text, mediaUrl)
            val best = variants.maxByOrNull { it.first }?.second ?: break
            mediaUrl = best
            text = Http.getText(mediaUrl, item.headers)
        }

        val segments = ArrayList<PlaylistSegment>()
        var initUri: String? = null
        var hasMap = false
        var currentKey: HlsKey? = null
        var sequence = 0L

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    initUri = attr(line, "URI")?.let { Http.resolve(mediaUrl, it) }
                    hasMap = true
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    when (val method = attr(line, "METHOD")?.uppercase() ?: "NONE") {
                        "NONE" -> currentKey = null
                        "AES-128" -> {
                            val uri = attr(line, "URI") ?: throw IOException("Key without URI")
                            val iv = attr(line, "IV")?.removePrefix("0x")?.removePrefix("0X")?.let { hexToBytes(it) }
                            currentKey = HlsKey(method, Http.resolve(mediaUrl, uri), iv)
                        }
                        else -> throw HlsDrmException()
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    segments.add(PlaylistSegment(Http.resolve(mediaUrl, line), currentKey, sequence))
                    sequence++
                }
            }
        }
        if (segments.isEmpty()) throw HlsEmptyException()

        val pseudo = item.segments.firstOrNull()
        var resumeAt = if (pseudo != null && pseudo.end + 1 == segments.size.toLong() && pseudo.done < segments.size) {
            pseudo.done.toInt()
        } else {
            0
        }

        val extension = if (hasMap) "mp4" else "ts"
        val out = File(staging.parentFile, "hls-${item.id}.$extension")
        // Never resume into a file that is gone or empty: that used to silently drop the head
        // of the video. If the output is missing we start over from segment zero.
        if (!out.exists() || out.length() <= 0L) {
            out.delete()
            resumeAt = 0
        }

        val keyCache = HashMap<String, ByteArray>()
        val total = segments.size
        var done = resumeAt
        var bytes = if (resumeAt > 0) out.length() else 0L
        onProgress(bytes, done, total)

        FileOutputStream(out, true).use { os ->
            if (resumeAt == 0 && initUri != null) {
                control.checkpoint()
                val init = fetch(initUri, null)
                os.write(init)
                bytes += init.size
            }
            for (index in resumeAt until total) {
                control.checkpoint()
                val seg = segments[index]
                val data = fetch(seg.uri, seg.key, seg.sequence, keyCache)
                os.write(data)
                bytes += data.size
                done = index + 1
                onProgress(bytes, done, total)
            }
            os.flush()
            os.fd.sync()
        }

        val base = item.fileName.substringBeforeLast('.', item.fileName).ifBlank { "video" }
        val name = "$base.$extension"
        return Outcome(out, name, if (hasMap) "video/mp4" else "video/mp2t", total)
    }

    private fun fetch(
        uri: String,
        key: HlsKey?,
        sequence: Long = 0L,
        cache: MutableMap<String, ByteArray> = HashMap(),
    ): ByteArray {
        val conn = Http.open(uri)
        item.headers.forEach { (k, v) -> if (k.isNotBlank()) conn.setRequestProperty(k, v) }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            val expected = conn.contentLengthLong
            val buffer = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    control.checkpoint()
                    val n = input.read(chunk)
                    if (n <= 0) break
                    limiter.acquire(n)
                    buffer.write(chunk, 0, n)
                }
            }
            val raw = buffer.toByteArray()
            if (expected > 0 && raw.size.toLong() != expected) throw IOException("Short segment")
            if (key == null) return raw
            val keyBytes = cache.getOrPut(key.uri) { Http.getBytes(key.uri, item.headers) }
            return decrypt(raw, keyBytes, key.iv ?: ivForSequence(sequence))
        } finally {
            conn.disconnect()
        }
    }

    private fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = SecretKeySpec(key, "AES")
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, spec, IvParameterSpec(iv))
            cipher.doFinal(data)
        } catch (_: Exception) {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, spec, IvParameterSpec(iv))
            cipher.doFinal(data)
        }
    }

    private fun ivForSequence(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (i in 15 downTo 8) {
            iv[i] = (value and 0xFF).toByte()
            value = value shr 8
        }
        return iv
    }

    private fun parseVariants(text: String, base: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        val lines = text.lines()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val bandwidth = attr(line, "BANDWIDTH")?.toLongOrNull() ?: 0L
                val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                if (next != null) out.add(bandwidth to Http.resolve(base, next.trim()))
            }
        }
        return out
    }

    private fun attr(line: String, name: String): String? {
        val regex = Regex("$name=(\"[^\"]*\"|[^,]*)")
        val match = regex.find(line) ?: return null
        return match.groupValues[1].trim().trim('"')
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
