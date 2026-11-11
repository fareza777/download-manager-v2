package com.f7.downloadmanager.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.f7.downloadmanager.engine.Http
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Destinations are created through the Storage Access Framework when the user picked a
 * folder, through MediaStore on Android 10+, and through app-private external storage as
 * a last resort. No broad storage permission is ever requested.
 */
class Storage(private val ctx: Context, private val settings: Settings) {

    data class Dest(val uri: String?, val path: String?, val displayName: String)

    fun stagingDir(): File {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "staging").apply { mkdirs() }
    }

    fun stagingFile(id: Long): File = File(stagingDir(), "f7-$id.part")

    fun clearStaging(id: Long) {
        runCatching { stagingFile(id).delete() }
    }

    fun save(staging: File, desiredName: String, mime: String, onProgress: (Long, Long) -> Unit): Dest {
        if (!staging.exists()) throw IOException("Staging file is missing")
        val name = Http.sanitize(desiredName).ifBlank { "download" }
        val safeMime = mime.ifBlank { "application/octet-stream" }
        val total = staging.length()

        val tree = settings.treeUri.value
        if (tree != null) {
            try {
                return saveToTree(Uri.parse(tree), staging, name, safeMime, total, onProgress)
            } catch (_: Exception) {
                // Folder may have been removed. Fall through to a working destination.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return saveToMediaStore(staging, name, safeMime, total, onProgress)
            } catch (_: Exception) {
                // Fall through.
            }
        }
        return saveToAppDir(staging, name, total, onProgress)
    }

    private fun saveToTree(
        treeUri: Uri,
        staging: File,
        name: String,
        mime: String,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ): Dest {
        val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: throw IOException("Folder unavailable")
        val unique = uniqueTreeName(tree, name)
        var doc = tree.findFile(unique) ?: tree.createFile(mime, unique) ?: throw IOException("Cannot create file")
        if (doc.name != unique) runCatching { doc.renameTo(unique) }
        val pfd = ctx.contentResolver.openFileDescriptor(doc.uri, "wt") ?: throw IOException("Cannot open file")
        FileOutputStream(pfd.fileDescriptor).use { out ->
            copy(staging, out, total, onProgress)
            out.flush()
            out.fd.sync()
        }
        pfd.close()
        return Dest(doc.uri.toString(), null, doc.name ?: unique)
    }

    private fun saveToMediaStore(
        staging: File,
        name: String,
        mime: String,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ): Dest {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = Environment.DIRECTORY_DOWNLOADS + "/F7"
        val finalName = uniqueMediaName(collection, relative, name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output")
            out.use { copy(staging, it, total, onProgress) }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return Dest(uri.toString(), null, finalName)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun saveToAppDir(
        staging: File,
        name: String,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ): Dest {
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Downloads").apply { mkdirs() }
        val unique = uniqueFile(dir, name)
        val target = File(dir, unique)
        target.outputStream().use { copy(staging, it, total, onProgress) }
        return Dest(null, target.absolutePath, unique)
    }

    private fun copy(source: File, out: OutputStream, total: Long, onProgress: (Long, Long) -> Unit) {
        source.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            var copied = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                copied += n
                onProgress(copied, total)
            }
        }
    }

    private fun uniqueFile(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    private fun uniqueTreeName(tree: DocumentFile, name: String): String {
        if (tree.findFile(name) == null) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (tree.findFile(candidate) == null) return candidate
            i++
        }
    }

    private fun uniqueMediaName(collection: Uri, relative: String, name: String): String {
        val names = HashSet<String>()
        runCatching {
            ctx.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$relative%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { names.add(it) }
            }
        }
        if (name !in names) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (candidate !in names) return candidate
            i++
        }
    }

    fun stagingBytes(): Long = runCatching {
        stagingDir().listFiles()?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)

    fun clearAllStaging() {
        runCatching { stagingDir().listFiles()?.forEach { it.delete() } }
    }

    fun exists(location: String?): Boolean {
        if (location.isNullOrBlank()) return false
        return if (location.startsWith("content://")) {
            runCatching {
                ctx.contentResolver.openFileDescriptor(Uri.parse(location), "r")?.use { true } ?: false
            }.getOrDefault(false)
        } else {
            File(location).exists()
        }
    }

    fun sizeOf(location: String?): Long? {
        if (location.isNullOrBlank()) return null
        return if (location.startsWith("content://")) {
            runCatching {
                ctx.contentResolver.query(Uri.parse(location), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                }
            }.getOrNull()
        } else {
            File(location).length().takeIf { it > 0 }
        }
    }

    fun delete(location: String?): Boolean {
        if (location.isNullOrBlank()) return false
        return if (location.startsWith("content://")) {
            runCatching { ctx.contentResolver.delete(Uri.parse(location), null, null) > 0 }.getOrDefault(false)
        } else {
            runCatching { File(location).delete() }.getOrDefault(false)
        }
    }

    fun freeBytes(): Long = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        stat.availableBytes
    }.getOrDefault(0L)

    fun openInput(location: String) = if (location.startsWith("content://")) {
        ctx.contentResolver.openInputStream(Uri.parse(location))
    } else {
        File(location).inputStream()
    }
}
