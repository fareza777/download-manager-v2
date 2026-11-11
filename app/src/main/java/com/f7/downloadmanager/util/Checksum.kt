package com.f7.downloadmanager.util

import android.content.Context
import com.f7.downloadmanager.data.Storage
import java.security.MessageDigest

object Checksum {
    fun sha256(storage: Storage, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            storage.openInput(location)?.use { input ->
                val buf = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    @Suppress("unused")
    fun sha256(context: Context, bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
