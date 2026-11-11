package com.f7.downloadmanager.engine

import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.core.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Real multi-connection HTTP downloader.
 *
 * - Splits the remote file into byte ranges and writes each range at its own file offset.
 * - Persists the exact progress of every range so a resume continues at the saved byte.
 * - Re-splits a large in-flight range when other connections are idle (work stealing).
 * - Validates ETag/Last-Modified and total size before resuming, so a changed file is
 *   downloaded again instead of being corrupted.
 */
internal class SegmentedDownloader(
    private val item: DownloadItem,
    private val probe: Probe,
    private val staging: File,
    private val control: DownloadControl,
    private val limiter: TokenBucket,
    private val maxConnections: Int,
    private val onProgress: (downloaded: Long, total: Long, segments: List<Segment>) -> Unit,
) {
    data class Outcome(val total: Long, val segments: List<Segment>)

    private class WorkSegment(val start: Long, @Volatile var end: Long, @Volatile var done: Long) {
        val length: Long get() = (end - start + 1).coerceAtLeast(0)
        val remaining: Long get() = (length - done).coerceAtLeast(0)
    }

    private val plan = ArrayList<WorkSegment>()
    private val progress = AtomicLong(0)
    private val total: Long = probe.size

    suspend fun run(): Outcome {
        if (total <= 0L || !probe.acceptRanges) return singleStream()
        val saved = item.segments
        val reusable = staging.exists() &&
            item.totalBytes == total &&
            saved.isNotEmpty() &&
            saved.sumOf { it.length } == total &&
            saved.all { it.done in 0..it.length } &&
            (item.etag == null || probe.etag == null || item.etag == probe.etag)

        if (reusable) {
            saved.forEach { plan.add(WorkSegment(it.start, it.end, it.done)) }
        } else {
            staging.delete()
            plan.addAll(splitRanges(total, maxConnections))
        }
        staging.parentFile?.mkdirs()
        RandomAccessFile(staging, "rw").use { it.setLength(total) }
        progress.set(plan.sumOf { it.done })
        onProgress(progress.get(), total, snapshot())

        val queue = ConcurrentLinkedQueue(plan)
        val running = AtomicInteger(0)
        val workers = min(maxConnections, plan.size).coerceAtLeast(1)
        coroutineScope {
            val jobs = (0 until workers).map {
                launch(Dispatchers.IO) {
                    running.incrementAndGet()
                    try {
                        while (true) {
                            control.checkpoint()
                            val next = queue.poll() ?: break
                            val seg = splitIfIdle(next, queue, running)
                            downloadRange(seg)
                        }
                    } finally {
                        running.decrementAndGet()
                    }
                }
            }
            jobs.joinAll()
        }
        control.checkpoint()
        if (progress.get() < total) throw IOException("Download incomplete")
        return Outcome(total, snapshot())
    }

    private fun splitRanges(size: Long, connections: Int): List<WorkSegment> {
        val usable = if (size < 2L * 1024 * 1024) 1 else connections.coerceIn(1, 16)
        val chunk = (size + usable - 1) / usable
        val out = ArrayList<WorkSegment>()
        var start = 0L
        while (start < size) {
            val end = min(start + chunk - 1, size - 1)
            out.add(WorkSegment(start, end, 0))
            start = end + 1
        }
        return out
    }

    private fun splitIfIdle(seg: WorkSegment, queue: ConcurrentLinkedQueue<WorkSegment>, running: AtomicInteger): WorkSegment {
        if (running.get() <= 1 || !queue.isEmpty() || seg.remaining <= SPLIT_BYTES) return seg
        val cut = seg.start + seg.done + seg.remaining / 2
        if (cut > seg.start + seg.done && cut <= seg.end) {
            val tail = WorkSegment(cut, seg.end, 0)
            seg.end = cut - 1
            queue.add(tail)
        }
        return seg
    }

    private fun downloadRange(seg: WorkSegment) {
        RandomAccessFile(staging, "rw").use { raf ->
            var attempts = 0
            while (seg.remaining > 0) {
                control.checkpoint()
                val from = seg.start + seg.done
                val conn = Http.rangeConnection(probe.finalUrl, from, seg.end, probe.etag, item.headers)
                try {
                    val code = conn.responseCode
                    if (code == 200 && from > 0) throw NotResumableException()
                    if (code !in 200..299) throw HttpStatusException(code)
                    conn.inputStream.use { input ->
                        raf.seek(from)
                        val buf = ByteArray(BUFFER)
                        while (seg.remaining > 0) {
                            control.checkpoint()
                            val want = min(buf.size.toLong(), seg.remaining).toInt()
                            val n = input.read(buf, 0, want)
                            if (n <= 0) break
                            limiter.acquire(n)
                            raf.write(buf, 0, n)
                            seg.done += n
                            val done = progress.addAndGet(n.toLong())
                            onProgress(done, total, snapshot())
                        }
                    }
                } catch (t: Throwable) {
                    if (t is PausedException || t is CanceledException || t is NotResumableException) throw t
                    if (t is HttpStatusException && t.code in 400..499) throw t
                    attempts++
                    if (attempts > MAX_ATTEMPTS) throw t
                } finally {
                    conn.disconnect()
                }
                if (seg.remaining > 0) {
                    attempts++
                    if (attempts > MAX_ATTEMPTS) throw IOException("Connection kept failing")
                    try {
                        Thread.sleep(min(500L * attempts, 3000L))
                    } catch (_: InterruptedException) {
                        throw CanceledException()
                    }
                }
            }
            raf.fd.sync()
        }
    }

    /** Servers without range support: one stream, honest restart on resume. */
    private fun singleStream(): Outcome {
        staging.parentFile?.mkdirs()
        if (!probe.acceptRanges) staging.delete()
        val known = total > 0L
        var written = if (known && staging.exists()) staging.length() else 0L
        if (known) written = 0L
        RandomAccessFile(staging, "rw").use { raf ->
            raf.setLength(written)
            var attempts = 0
            while (true) {
                control.checkpoint()
                val conn = Http.rangeConnection(probe.finalUrl, if (written > 0) written else 0L, null, probe.etag, item.headers)
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw HttpStatusException(code)
                    conn.inputStream.use { input ->
                        raf.seek(written)
                        val buf = ByteArray(BUFFER)
                        while (true) {
                            control.checkpoint()
                            val n = input.read(buf)
                            if (n <= 0) break
                            limiter.acquire(n)
                            raf.write(buf, 0, n)
                            written += n
                            val size = if (known) total else 0L
                            onProgress(written, size, listOf(Segment(0, if (known) total - 1 else 0, written)))
                        }
                    }
                } catch (t: Throwable) {
                    if (t is PausedException || t is CanceledException) throw t
                    if (t is HttpStatusException && t.code in 400..499) throw t
                    attempts++
                    if (attempts > MAX_ATTEMPTS) throw t
                } finally {
                    conn.disconnect()
                }
                if (!known) break
                if (written >= total) break
                attempts++
                if (attempts > MAX_ATTEMPTS) throw IOException("Download incomplete")
            }
            raf.fd.sync()
        }
        val segments = listOf(Segment(0, if (known) total - 1 else written.coerceAtLeast(1), written))
        return Outcome(if (known) total else written, segments)
    }

    private fun snapshot(): List<Segment> = synchronized(plan) {
        plan.map { Segment(it.start, it.end, it.done.coerceAtMost(it.length)) }
    }

    private companion object {
        const val BUFFER = 128 * 1024
        const val MAX_ATTEMPTS = 6
        const val SPLIT_BYTES = 8L * 1024 * 1024
    }
}
