package com.f7.downloadmanager.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.core.Segment

/**
 * Small hand-written SQLite store. No annotation processor, no reflection:
 * every download row is a plain column set that survives process death.
 */
class DownloadStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                finalUrl TEXT,
                fileName TEXT NOT NULL,
                mime TEXT,
                total INTEGER NOT NULL DEFAULT 0,
                downloaded INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                error TEXT,
                category TEXT NOT NULL,
                segments TEXT NOT NULL DEFAULT '',
                etag TEXT,
                lastModified TEXT,
                acceptRanges INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                location TEXT,
                isHls INTEGER NOT NULL DEFAULT 0,
                checksum TEXT,
                retries INTEGER NOT NULL DEFAULT 0,
                headers TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_status ON $TABLE(status)")
        db.execSQL("CREATE INDEX idx_created ON $TABLE(createdAt)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first public schema.
    }

    fun all(): List<DownloadItem> {
        val out = ArrayList<DownloadItem>()
        readableDatabase.query(TABLE, null, null, null, null, null, "createdAt DESC").use { c ->
            while (c.moveToNext()) out.add(c.toItem())
        }
        return out
    }

    fun insert(item: DownloadItem): Long {
        val values = item.toValues(includeId = false)
        return writableDatabase.insert(TABLE, null, values)
    }

    fun update(item: DownloadItem) {
        writableDatabase.update(TABLE, item.toValues(includeId = false), "id = ?", arrayOf(item.id.toString()))
    }

    fun updateProgress(id: Long, downloaded: Long, total: Long, segments: String, status: String = "DOWNLOADING") {
        val values = ContentValues().apply {
            put("downloaded", downloaded)
            put("total", total)
            put("segments", segments)
            put("status", status)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    fun clearFinished() {
        // CANCELED belongs here too: the engine drops those rows from memory, so leaving them
        // in the database made them reappear after a process restart.
        writableDatabase.delete(
            TABLE,
            "status IN (?, ?, ?)",
            arrayOf(DStatus.COMPLETED.name, DStatus.FAILED.name, DStatus.CANCELED.name),
        )
    }

    private fun android.database.Cursor.toItem(): DownloadItem = DownloadItem(
        id = getLong(getColumnIndexOrThrow("id")),
        url = getString(getColumnIndexOrThrow("url")),
        finalUrl = getString(getColumnIndexOrThrow("finalUrl")),
        fileName = getString(getColumnIndexOrThrow("fileName")),
        mime = getString(getColumnIndexOrThrow("mime")),
        totalBytes = getLong(getColumnIndexOrThrow("total")),
        downloadedBytes = getLong(getColumnIndexOrThrow("downloaded")),
        status = runCatching { DStatus.valueOf(getString(getColumnIndexOrThrow("status"))) }.getOrDefault(DStatus.PAUSED),
        error = getString(getColumnIndexOrThrow("error")),
        category = getString(getColumnIndexOrThrow("category")) ?: "other",
        segments = decodeSegments(getString(getColumnIndexOrThrow("segments"))),
        etag = getString(getColumnIndexOrThrow("etag")),
        lastModified = getString(getColumnIndexOrThrow("lastModified")),
        acceptRanges = getInt(getColumnIndexOrThrow("acceptRanges")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        completedAt = getLong(getColumnIndexOrThrow("completedAt")).takeIf { !isNull(getColumnIndexOrThrow("completedAt")) },
        location = getString(getColumnIndexOrThrow("location")),
        isHls = getInt(getColumnIndexOrThrow("isHls")) == 1,
        checksum = getString(getColumnIndexOrThrow("checksum")),
        retries = getInt(getColumnIndexOrThrow("retries")),
        headers = decodeHeaders(getString(getColumnIndexOrThrow("headers"))),
    )

    private fun DownloadItem.toValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("url", url)
        put("finalUrl", finalUrl)
        put("fileName", fileName)
        put("mime", mime)
        put("total", totalBytes)
        put("downloaded", downloadedBytes)
        put("status", status.name)
        put("error", error)
        put("category", category)
        put("segments", encodeSegments(segments))
        put("etag", etag)
        put("lastModified", lastModified)
        put("acceptRanges", if (acceptRanges) 1 else 0)
        put("createdAt", createdAt)
        put("completedAt", completedAt)
        put("location", location)
        put("isHls", if (isHls) 1 else 0)
        put("checksum", checksum)
        put("retries", retries)
        put("headers", encodeHeaders(headers))
    }

    companion object {
        private const val DB_NAME = "f7.db"
        private const val DB_VERSION = 1
        private const val TABLE = "downloads"

        fun encodeSegments(segments: List<Segment>): String =
            segments.joinToString(";") { "${it.start},${it.end},${it.done}" }

        fun decodeSegments(raw: String?): List<Segment> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(";").mapNotNull { part ->
                val bits = part.split(",")
                if (bits.size < 3) return@mapNotNull null
                val start = bits[0].toLongOrNull() ?: return@mapNotNull null
                val end = bits[1].toLongOrNull() ?: return@mapNotNull null
                val done = bits[2].toLongOrNull() ?: return@mapNotNull null
                Segment(start, end, done)
            }
        }

        fun encodeHeaders(headers: Map<String, String>): String =
            headers.entries.joinToString("\n") { "${it.key}\t${it.value}" }

        fun decodeHeaders(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.lines().mapNotNull { line ->
                val idx = line.indexOf('\t')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }.toMap()
        }
    }
}
