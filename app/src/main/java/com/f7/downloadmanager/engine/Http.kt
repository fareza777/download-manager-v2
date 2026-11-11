package com.f7.downloadmanager.engine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

class HttpStatusException(val code: Int) : IOException("HTTP $code")
class NotResumableException : IOException("Server does not support range requests")
class NetworkUnavailableException : IOException("No connection")

data class Probe(
    val url: String,
    val finalUrl: String,
    val size: Long,
    val acceptRanges: Boolean,
    val mime: String?,
    val suggestedName: String?,
    val etag: String?,
    val lastModified: String?,
)

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36 F7/1.0"

    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 30_000
    private const val MAX_REDIRECTS = 8

    /** Size-only probe. Uses a one-byte range request so files are never pulled twice. */
    fun probe(url: String, headers: Map<String, String> = emptyMap()): Probe {
        var current = url
        var redirects = 0
        while (true) {
            if (redirects > MAX_REDIRECTS) throw IOException("Too many redirects")
            val conn = open(current)
            headers.forEach { (k, v) -> if (k.isNotBlank()) conn.setRequestProperty(k, v) }
            conn.setRequestProperty("Range", "bytes=0-0")
            conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code in 300..399) {
                val target = conn.getHeaderField("Location").orEmpty()
                conn.disconnect()
                if (target.isBlank()) throw HttpStatusException(code)
                current = URL(URL(current), target).toString()
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpStatusException(code)
            }
            val rangeHeader = conn.getHeaderField("Content-Range")
            val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            var size = -1L
            val accepts = code == 206
            if (rangeHeader != null) {
                val total = rangeHeader.substringAfter('/', "").trim()
                size = total.toLongOrNull() ?: -1L
            }
            if (size <= 0L) size = if (code == 200) contentLength else -1L

            val dispositionName = parseDisposition(conn.getHeaderField("Content-Disposition"))
            val mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            val etag = conn.getHeaderField("ETag")
            val modified = conn.getHeaderField("Last-Modified")
            conn.disconnect()
            return Probe(
                url = url,
                finalUrl = current,
                size = size,
                acceptRanges = accepts,
                mime = mime,
                suggestedName = dispositionName ?: nameFromUrl(current),
                etag = etag,
                lastModified = modified,
            )
        }
    }

    fun rangeConnection(
        url: String,
        start: Long,
        endInclusive: Long?,
        etag: String?,
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        val conn = open(url)
        headers.forEach { (k, v) -> if (k.isNotBlank()) conn.setRequestProperty(k, v) }
        val range = if (endInclusive != null && endInclusive >= start) "bytes=$start-$endInclusive" else "bytes=$start-"
        conn.setRequestProperty("Range", range)
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (start > 0 && !etag.isNullOrBlank()) conn.setRequestProperty("If-Range", etag)
        return conn
    }

    fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.useCaches = false
        return conn
    }

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        var current = url
        var redirects = 0
        while (true) {
            if (redirects > MAX_REDIRECTS) throw IOException("Too many redirects")
            val conn = open(current)
            headers.forEach { (k, v) -> if (k.isNotBlank()) conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code in 300..399) {
                val target = conn.getHeaderField("Location").orEmpty()
                conn.disconnect()
                if (target.isBlank()) throw HttpStatusException(code)
                current = URL(URL(current), target).toString()
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpStatusException(code)
            }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        var current = url
        var redirects = 0
        while (true) {
            if (redirects > MAX_REDIRECTS) throw IOException("Too many redirects")
            val conn = open(current)
            headers.forEach { (k, v) -> if (k.isNotBlank()) conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code in 300..399) {
                val target = conn.getHeaderField("Location").orEmpty()
                conn.disconnect()
                if (target.isBlank()) throw HttpStatusException(code)
                current = URL(URL(current), target).toString()
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpStatusException(code)
            }
            return conn.inputStream.use { it.readBytes() }
        }
    }

    fun resolve(base: String, relative: String): String {
        return try {
            URL(URL(base), relative).toString()
        } catch (_: Exception) {
            relative
        }
    }

    fun isSupported(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    fun isMagnet(url: String): Boolean = url.trim().startsWith("magnet:", ignoreCase = true)

    fun isHls(url: String, mime: String? = null): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        val m = (mime ?: "").lowercase(Locale.ROOT)
        return path.endsWith(".m3u8") || m.contains("mpegurl")
    }

    fun nameFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/', "")
        if (last.isBlank()) return null
        return sanitize(URLDecoder.decode(last, "UTF-8"))
    }

    fun sanitize(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trim('.')
        return if (cleaned.isBlank()) "download" else cleaned.take(180)
    }

    /**
     * Header names and values arriving from the browser are untrusted: drop anything that could
     * inject a second header or smuggle a newline into the request.
     */
    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> =
        headers.entries.mapNotNull { (rawName, rawValue) ->
            val name = rawName.trim()
            val value = rawValue.replace("\r", "").replace("\n", "").trim()
            val valid = name.isNotEmpty() &&
                value.isNotEmpty() &&
                name.length <= 64 &&
                name.matches(Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))
            if (valid) name to value else null
        }.toMap()

    private fun parseDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val star = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)
        if (star != null) {
            return runCatching { sanitize(URLDecoder.decode(star.groupValues[1], "UTF-8")) }.getOrNull()
        }
        val plain = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)
        return plain?.groupValues?.get(1)?.let { sanitize(it) }
    }
}
