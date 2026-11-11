package com.f7.downloadmanager.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DStatus { QUEUED, CONNECTING, DOWNLOADING, PAUSED, SAVING, COMPLETED, FAILED, CANCELED }

/** Immutable view of one byte range of a segmented download. */
data class Segment(val start: Long, val end: Long, val done: Long = 0) {
    val length: Long get() = (end - start + 1).coerceAtLeast(0)
    val remaining: Long get() = (length - done).coerceAtLeast(0)
}

data class DownloadItem(
    val id: Long = 0L,
    val url: String,
    val finalUrl: String? = null,
    val fileName: String,
    val mime: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DStatus = DStatus.QUEUED,
    val error: String? = null,
    val category: String = Categories.OTHER,
    val segments: List<Segment> = emptyList(),
    val etag: String? = null,
    val lastModified: String? = null,
    val acceptRanges: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val location: String? = null,
    val isHls: Boolean = false,
    val checksum: String? = null,
    val retries: Int = 0,
    val speed: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val isActive: Boolean get() = status == DStatus.DOWNLOADING || status == DStatus.CONNECTING || status == DStatus.SAVING
    val isFinished: Boolean get() = status == DStatus.COMPLETED
    val isOpenable: Boolean get() = location != null
}

object Categories {
    const val VIDEO = "video"
    const val AUDIO = "audio"
    const val IMAGE = "image"
    const val DOCUMENT = "document"
    const val ARCHIVE = "archive"
    const val APK = "apk"
    const val OTHER = "other"

    val all = listOf(VIDEO, AUDIO, IMAGE, DOCUMENT, ARCHIVE, APK, OTHER)

    fun from(name: String?, mime: String?): String {
        val m = (mime ?: "").lowercase(Locale.ROOT)
        val ext = (name ?: "").substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            m.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "ts", "m4v", "flv") -> VIDEO
            m.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus") -> AUDIO
            m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heic") -> IMAGE
            m.startsWith("text/") || ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv") -> DOCUMENT
            ext in setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2") -> ARCHIVE
            ext == "apk" || ext == "apks" || ext == "xapk" -> APK
            else -> OTHER
        }
    }
}

object Fmt {
    private val sizes = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 B"
        var v = value.toDouble()
        var i = 0
        while (v >= 1024.0 && i < sizes.lastIndex) {
            v /= 1024.0
            i++
        }
        return if (i == 0) "${v.toLong()} ${sizes[i]}"
        else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, sizes[i])
    }

    fun speed(bps: Long): String = if (bps <= 0L) "0 B" else bytes(bps)

    fun eta(remainingBytes: Long, bps: Long): String? {
        if (bps <= 0L || remainingBytes <= 0L) return null
        val seconds = remainingBytes / bps
        return when {
            seconds < 60 -> "${seconds.coerceAtLeast(1)}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    fun date(millis: Long?): String {
        if (millis == null || millis <= 0L) return "—"
        return SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }

    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"
}
