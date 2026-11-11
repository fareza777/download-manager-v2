package com.f7.downloadmanager.engine

/** Cooperative control shared by the engine and every worker of one download. */
class DownloadControl {
    @Volatile var paused: Boolean = false
    @Volatile var canceled: Boolean = false

    fun checkpoint() {
        if (canceled) throw CanceledException()
        if (paused) throw PausedException()
    }
}

class PausedException : Exception("paused")
class CanceledException : Exception("canceled")
class HlsDrmException : Exception("drm")
class HlsEmptyException : Exception("empty playlist")

/**
 * Global rate limiter shared by every connection of every download.
 * rate is bytes per second; 0 means unlimited.
 */
class TokenBucket {
    @Volatile var rate: Long = 0L

    private val lock = Object()
    private var tokens: Double = 0.0
    private var lastNanos: Long = System.nanoTime()

    fun acquire(bytes: Int) {
        val r = rate
        if (r <= 0L || bytes <= 0) return
        synchronized(lock) {
            while (true) {
                val now = System.nanoTime()
                val elapsed = (now - lastNanos) / 1_000_000_000.0
                lastNanos = now
                tokens = (tokens + elapsed * r).coerceAtMost(r.toDouble())
                if (tokens >= bytes) {
                    tokens -= bytes
                    return
                }
                val needMs = (((bytes - tokens) / r) * 1000.0).toLong().coerceIn(1L, 250L)
                try {
                    lock.wait(needMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }
}
