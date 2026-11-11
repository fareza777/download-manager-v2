package com.f7.downloadmanager.engine

import android.content.Context
import android.os.SystemClock
import com.f7.downloadmanager.R
import com.f7.downloadmanager.core.Categories
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.core.Segment
import com.f7.downloadmanager.data.DownloadStore
import com.f7.downloadmanager.data.Settings
import com.f7.downloadmanager.data.Storage
import com.f7.downloadmanager.service.Notifier
import com.f7.downloadmanager.util.Checksum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

private const val MIN_FREE_BYTES = 128L * 1024 * 1024

/**
 * Owns the queue, the running tasks and the persisted state. The engine is a process
 * singleton so the activity, the foreground service and the notification all observe the
 * same live progress.
 */
class DownloadEngine(
    private val ctx: Context,
    private val store: DownloadStore,
    private val settings: Settings,
    private val storage: Storage,
    private val constraints: Constraints,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()
    private val _totalSpeed = MutableStateFlow(0L)
    val totalSpeed: StateFlow<Long> = _totalSpeed.asStateFlow()
    val loaded = MutableStateFlow(false)

    private val live = ConcurrentHashMap<Long, DownloadItem>()
    private val tasks = ConcurrentHashMap<Long, Task>()
    private val interruptedIds = mutableSetOf<Long>()
    private val lastDbWrite = ConcurrentHashMap<Long, Long>()
    private val networkPaused = ConcurrentHashMap.newKeySet<Long>()
    private var waitingNetwork = false
    private val limiter = TokenBucket()
    private var scheduler: Job? = null
    private var ticker: Job? = null
    private var lastPublish = 0L

    private class Task(val control: DownloadControl, val job: Job)

    data class AddResult(val added: Int, val magnets: List<String>, val invalid: List<String>)

    init {
        limiter.rate = settings.speedLimitKbps.value * 1024L
        scope.launch {
            settings.speedLimitKbps.collect { limiter.rate = it * 1024L }
        }
        constraints.watch { online, _ ->
            scope.launch { if (online) onNetworkBack() else onNetworkLost() }
        }
        scope.launch { restore() }
    }

    private suspend fun restore() = withContext(Dispatchers.IO) {
        val saved = runCatching { store.all() }.getOrDefault(emptyList())
        saved.forEach { item ->
            val wasRunning = item.status == DStatus.DOWNLOADING ||
                item.status == DStatus.CONNECTING ||
                item.status == DStatus.QUEUED ||
                item.status == DStatus.SAVING
            if (wasRunning) interruptedIds.add(item.id)
            live[item.id] = if (wasRunning) item.copy(status = DStatus.PAUSED, speed = 0) else item.copy(speed = 0)
        }
        loaded.value = true
        publish(force = true)
    }

    /** Called when the UI (or service) is in the foreground and the setting allows it. */
    fun resumeInterrupted() {
        if (interruptedIds.isEmpty()) return
        interruptedIds.toList().forEach { id ->
            val item = live[id] ?: return@forEach
            if (item.status == DStatus.PAUSED) live[id] = item.copy(status = DStatus.QUEUED, error = null)
        }
        interruptedIds.clear()
        publish(force = true)
        persistAsync(live.values.toList())
        startQueue()
    }

    suspend fun add(
        links: List<String>,
        category: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): AddResult {
        val magnets = ArrayList<String>()
        val invalid = ArrayList<String>()
        var added = 0
        val safeHeaders = Http.sanitizeHeaders(headers)
        val cleaned = links.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        for (link in cleaned) {
            when {
                Http.isMagnet(link) -> magnets.add(link)
                !Http.isSupported(link) -> invalid.add(link)
                else -> {
                    val duplicate = live.values.any { it.url == link && it.status != DStatus.CANCELED }
                    if (duplicate) continue
                    val name = Http.nameFromUrl(link) ?: "download"
                    val item = DownloadItem(
                        url = link,
                        fileName = name,
                        category = category ?: Categories.from(name, null),
                        status = DStatus.QUEUED,
                        isHls = Http.isHls(link),
                        headers = safeHeaders,
                    )
                    val id = withContext(Dispatchers.IO) { store.insert(item) }
                    live[id] = item.copy(id = id)
                    added++
                }
            }
        }
        if (added > 0) {
            publish(force = true)
            startQueue()
        }
        return AddResult(added, magnets, invalid)
    }

    fun startQueue() {
        if (scheduler?.isActive == true) return
        if (tasks.isEmpty() && live.values.none { it.status == DStatus.QUEUED }) return
        scheduler = scope.launch {
            while (isActive) {
                val blocked = blockedReason()
                if (blocked != null) {
                    markWaiting(blocked)
                    delay(2000)
                    continue
                }
                clearWaiting()
                val max = settings.concurrent.value
                var guard = 0
                while (tasks.size < max && guard++ < 8) {
                    val next = live.values
                        .filter { it.status == DStatus.QUEUED }
                        .minByOrNull { it.createdAt }
                        ?: break
                    launchTask(next)
                }
                if (tasks.isEmpty() && live.values.none { it.status == DStatus.QUEUED }) break
                delay(350)
            }
            scheduler = null
            publish(force = true)
        }
        ensureTicker()
    }

    private fun launchTask(item: DownloadItem) {
        if (tasks.containsKey(item.id)) return
        val control = DownloadControl()
        val started = item.copy(status = DStatus.CONNECTING, error = null, speed = 0)
        live[item.id] = started
        publish(force = true)
        persistNow(started)
        val job = scope.launch {
            try {
                execute(started, control)
            } catch (_: PausedException) {
                updateItem(item.id) { current ->
                    if (current.status == DStatus.QUEUED) current
                    else current.copy(status = DStatus.PAUSED, speed = 0)
                }
            } catch (_: CanceledException) {
                updateItem(item.id) { it.copy(status = DStatus.CANCELED, speed = 0) }
                storage.clearStaging(item.id)
            } catch (_: CancellationException) {
                // task was cancelled by removal
            } catch (t: Throwable) {
                val message = describe(t)
                val failed = updateItem(item.id) { it.copy(status = DStatus.FAILED, error = message, speed = 0, retries = it.retries + 1) }
                val current = live[item.id]
                if (current != null && settings.notifications.value) Notifier.failed(ctx, current)
                if (failed != null && settings.autoRetry.value && failed.retries <= settings.maxRetries.value) {
                    scheduleRetry(item.id, failed.retries)
                }
            } finally {
                tasks.remove(item.id)
                publish(force = true)
                lastDbWrite.remove(item.id)
            }
        }
        tasks[item.id] = Task(control, job)
    }

    private suspend fun execute(start: DownloadItem, control: DownloadControl) {
        control.checkpoint()
        val probe = Http.probe(start.url, start.headers)
        control.checkpoint()
        if (probe.size > 0 && storage.freeBytes() < probe.size + MIN_FREE_BYTES) {
            throw IOException(ctx.getString(R.string.err_no_space))
        }

        val hls = start.isHls || Http.isHls(probe.finalUrl, probe.mime)
        val name = chooseName(start, probe, hls)
        var item = start.copy(
            finalUrl = probe.finalUrl,
            mime = probe.mime ?: start.mime,
            acceptRanges = probe.acceptRanges,
            etag = probe.etag,
            lastModified = probe.lastModified,
            fileName = name,
            category = if (start.category == Categories.OTHER) Categories.from(name, probe.mime) else start.category,
            totalBytes = if (hls) start.totalBytes else probe.size.coerceAtLeast(0),
            isHls = hls,
        )
        live[item.id] = item
        publish(force = true)
        persistNow(item)

        if (hls) {
            val staging = storage.stagingFile(item.id)
            val skip = resumeSegmentCount(item)
            item = item.copy(segments = listOf(Segment(0, skip.end, skip.done)))
            live[item.id] = item
            val outcome = HlsDownloader(item, staging, control, limiter) { bytes, done, total ->
                onHlsProgress(item.id, bytes, done, total)
            }.run()
            val finalItem = live[item.id] ?: item
            finishAndSave(finalItem.copy(totalBytes = outcome.segments.toLong()), staging, outcome.name, outcome.mime)
        } else {
            val staging = storage.stagingFile(item.id)
            val outcome = SegmentedDownloader(
                item = item,
                probe = probe,
                staging = staging,
                control = control,
                limiter = limiter,
                maxConnections = settings.threads.value,
                onProgress = { done, total, segments -> onByteProgress(item.id, done, total, segments) },
            ).run()
            val current = live[item.id] ?: item
            finishAndSave(current.copy(totalBytes = outcome.total, downloadedBytes = outcome.total, segments = outcome.segments), staging, item.fileName, item.mime ?: "application/octet-stream")
        }
    }

    private data class Skip(val done: Long, val end: Long)

    private fun resumeSegmentCount(item: DownloadItem): Skip {
        val seg = item.segments.firstOrNull() ?: return Skip(0, -1)
        if (seg.done <= 0 || seg.done > seg.end + 1) return Skip(0, -1)
        return Skip(seg.done, seg.end)
    }

    private suspend fun finishAndSave(base: DownloadItem, staging: File, name: String, mime: String) {
        val stagingSize = staging.length().coerceAtLeast(1L)
        val saving = (live[base.id] ?: base).copy(
            status = DStatus.SAVING,
            speed = 0,
            downloadedBytes = 0L,
            totalBytes = stagingSize,
            error = null,
        )
        live[base.id] = saving
        publish(force = true)
        persistNow(saving)
        val dest = withContext(Dispatchers.IO) {
            storage.save(staging, name, mime) { copied, total ->
                onSaveProgress(base.id, copied, total)
            }
        }
        val realSize = storage.sizeOf(dest.uri ?: dest.path) ?: base.totalBytes
        val done = (live[base.id] ?: base).copy(
            fileName = dest.displayName,
            location = dest.uri ?: dest.path,
            status = DStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            downloadedBytes = realSize,
            totalBytes = realSize,
            error = null,
            speed = 0,
        )
        live[done.id] = done
        persistNow(done)
        publish(force = true)
        runCatching { staging.delete() }
        interruptedIds.remove(done.id)
        if (settings.notifications.value) Notifier.completed(ctx, done)
        maybeChecksum(done)
    }

    private fun maybeChecksum(item: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            val sum = Checksum.sha256(storage, item.location) ?: return@launch
            val current = live[item.id] ?: return@launch
            val updated = current.copy(checksum = sum)
            live[item.id] = updated
            persistNow(updated)
            publish(force = true)
        }
    }

    private fun chooseName(item: DownloadItem, probe: Probe, hls: Boolean): String {
        var name = item.fileName
        val suggested = probe.suggestedName
        val looksGeneric = name.isBlank() || name == "download" || !name.contains('.')
        if (looksGeneric && !suggested.isNullOrBlank()) name = suggested
        if (hls) {
            val withoutQuery = name.substringBefore('?')
            name = when {
                withoutQuery.endsWith(".mp4", true) || withoutQuery.endsWith(".ts", true) -> withoutQuery
                withoutQuery.contains('.') -> withoutQuery.substringBeforeLast('.') + ".mp4"
                else -> "$withoutQuery.mp4"
            }
        }
        return Http.sanitize(name)
    }

    private fun onByteProgress(id: Long, done: Long, total: Long, segments: List<Segment>) {
        val item = live[id] ?: return
        live[id] = item.copy(
            downloadedBytes = done,
            totalBytes = if (total > 0) total else item.totalBytes,
            segments = segments,
            status = DStatus.DOWNLOADING,
        )
        persistProgressThrottled(id, done, if (total > 0) total else item.totalBytes, segments)
        publish()
    }

    private fun onHlsProgress(id: Long, bytes: Long, done: Int, total: Int) {
        val item = live[id] ?: return
        val segments = listOf(Segment(0, (total - 1).toLong(), done.toLong()))
        live[id] = item.copy(
            downloadedBytes = bytes,
            totalBytes = 0L,
            segments = segments,
            status = DStatus.DOWNLOADING,
        )
        persistProgressThrottled(id, bytes, 0L, segments)
        publish()
    }

    private fun persistProgressThrottled(id: Long, done: Long, total: Long, segments: List<Segment>) {
        val now = SystemClock.elapsedRealtime()
        val last = lastDbWrite[id] ?: 0L
        if (now - last < 900) return
        lastDbWrite[id] = now
        scope.launch(Dispatchers.IO) {
            runCatching { store.updateProgress(id, done, total, DownloadStore.encodeSegments(segments)) }
        }
    }

    fun pause(id: Long) {
        if (live[id]?.status == DStatus.SAVING) return
        tasks[id]?.control?.paused = true
        updateItem(id) { it.copy(status = DStatus.PAUSED, speed = 0) }
        persistAsync(live[id])
    }

    fun resume(id: Long) {
        val item = live[id] ?: return
        if (item.status == DStatus.COMPLETED) return
        tasks[id]?.let { task -> task.control.paused = false }
        updateItem(id) { it.copy(status = DStatus.QUEUED, error = null, speed = 0) }
        persistAsync(live[id])
        startQueue()
    }

    fun retry(id: Long) {
        val item = live[id] ?: return
        tasks[id]?.let {
            it.control.canceled = true
            it.job.cancel()
            tasks.remove(id)
        }
        val reset = item.copy(
            status = DStatus.QUEUED,
            error = null,
            retries = 0,
            speed = 0,
        )
        live[id] = reset
        persistNow(reset)
        publish(force = true)
        startQueue()
    }

    fun cancel(id: Long) {
        val item = live[id] ?: return
        tasks[id]?.control?.canceled = true
        tasks[id]?.job?.cancel()
        tasks.remove(id)
        storage.clearStaging(id)
        updateItem(id) { it.copy(status = DStatus.CANCELED, speed = 0, segments = emptyList()) }
        persistAsync(live[id])
        publish(force = true)
    }

    suspend fun remove(id: Long) {
        tasks[id]?.control?.canceled = true
        tasks[id]?.job?.cancel()
        tasks.remove(id)
        storage.clearStaging(id)
        live.remove(id)
        withContext(Dispatchers.IO) { store.delete(id) }
        publish(force = true)
    }

    suspend fun deleteFile(item: DownloadItem) {
        withContext(Dispatchers.IO) { storage.delete(item.location) }
        val updated = (live[item.id] ?: item).copy(location = null, checksum = null)
        live[item.id] = updated
        persistNow(updated)
        publish(force = true)
    }

    suspend fun verify(item: DownloadItem): String? {
        val sum = withContext(Dispatchers.IO) { Checksum.sha256(storage, item.location) } ?: return null
        val current = live[item.id] ?: item
        val updated = current.copy(checksum = sum)
        live[item.id] = updated
        persistNow(updated)
        publish(force = true)
        return sum
    }

    suspend fun clearFinished() {
        val finished = live.values.filter { it.status == DStatus.COMPLETED || it.status == DStatus.FAILED || it.status == DStatus.CANCELED }
        finished.forEach { live.remove(it.id) }
        withContext(Dispatchers.IO) { store.clearFinished() }
        publish(force = true)
    }

    fun pauseAll() {
        live.keys.toList().forEach { id ->
            val item = live[id] ?: return@forEach
            if (item.status == DStatus.QUEUED || item.status == DStatus.CONNECTING || item.status == DStatus.DOWNLOADING) pause(id)
        }
    }

    fun resumeAll() {
        live.values.filter { it.status == DStatus.PAUSED || it.status == DStatus.FAILED }.forEach { resume(it.id) }
    }

    fun prioritize(id: Long) {
        val item = live[id] ?: return
        if (item.status == DStatus.QUEUED) {
            val moved = item.copy(createdAt = System.currentTimeMillis() + 1)
            live[id] = moved
            persistAsync(moved)
            publish(force = true)
        }
    }

    fun isIdle(): Boolean = tasks.isEmpty() && live.values.none { it.status == DStatus.QUEUED || it.isActive }

    fun hasQueued(): Boolean = live.values.any { it.status == DStatus.QUEUED }

    private fun updateItem(id: Long, transform: (DownloadItem) -> DownloadItem): DownloadItem? {
        val item = live[id] ?: return null
        val updated = transform(item)
        live[id] = updated
        publish(force = true)
        return updated
    }

    private fun publish(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 220) return
        lastPublish = now
        _items.value = live.values.sortedByDescending { it.createdAt }
    }

    private fun persistAsync(item: DownloadItem?) {
        if (item == null) return
        scope.launch(Dispatchers.IO) { runCatching { store.update(item) } }
    }

    private fun persistAsync(items: List<DownloadItem>) {
        scope.launch(Dispatchers.IO) { items.forEach { runCatching { store.update(it) } } }
    }

    private fun persistNow(item: DownloadItem) {
        scope.launch(Dispatchers.IO) { runCatching { store.update(item) } }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            val previous = HashMap<Long, Long>()
            var lastTime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val seconds = ((now - lastTime).coerceAtLeast(1)) / 1000.0
                lastTime = now
                var aggregate = 0L
                tasks.keys.toList().forEach { id ->
                    val item = live[id] ?: return@forEach
                    val before = previous[id] ?: item.downloadedBytes
                    val delta = (item.downloadedBytes - before).coerceAtLeast(0L)
                    val speed = (delta / seconds).toLong()
                    if (speed != item.speed) live[id] = item.copy(speed = speed)
                    aggregate += speed
                    previous[id] = item.downloadedBytes
                }
                previous.keys.retainAll(tasks.keys)
                _totalSpeed.value = aggregate
                publish(force = true)
                if (tasks.isEmpty()) break
            }
            _totalSpeed.value = 0
            publish(force = true)
            ticker = null
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is HttpStatusException -> ctx.getString(R.string.err_http, t.code)
        is UnknownHostException, is SocketTimeoutException, is NetworkUnavailableException -> ctx.getString(R.string.err_network)
        is NotResumableException -> ctx.getString(R.string.err_not_resumable)
        is HlsDrmException -> ctx.getString(R.string.err_hls_drm)
        is HlsEmptyException -> ctx.getString(R.string.err_hls_empty)
        is IOException -> t.message?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.err_network)
        else -> ctx.getString(R.string.err_unknown)
    }

    private fun blockedReason(): String? {
        if (settings.chargingOnly.value && !constraints.isCharging()) {
            return ctx.getString(R.string.err_waiting_charge)
        }
        if (!constraints.isOnline()) return ctx.getString(R.string.err_waiting_network)
        if (settings.wifiOnly.value && constraints.isMetered()) return ctx.getString(R.string.err_waiting_wifi)
        return null
    }

    private fun markWaiting(reason: String) {
        val changed = live.values.filter { it.status == DStatus.QUEUED && it.error != reason }
        if (changed.isEmpty()) return
        changed.forEach { live[it.id] = it.copy(error = reason) }
        publish(force = true)
    }

    private fun clearWaiting() {
        val reasons = setOf(
            ctx.getString(R.string.err_waiting_wifi),
            ctx.getString(R.string.err_waiting_charge),
            ctx.getString(R.string.err_waiting_network),
        )
        val changed = live.values.filter { it.status == DStatus.QUEUED && it.error in reasons }
        if (changed.isEmpty()) return
        changed.forEach { live[it.id] = it.copy(error = null) }
        publish(force = true)
    }

    private fun scheduleRetry(id: Long, attempt: Int) {
        scope.launch {
            val backoff = (4000L * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(120_000L)
            delay(backoff)
            val item = live[id] ?: return@launch
            if (item.status != DStatus.FAILED || item.retries != attempt) return@launch
            if (!settings.autoRetry.value) return@launch
            live[id] = item.copy(status = DStatus.QUEUED)
            persistNow(live[id] ?: item)
            publish(force = true)
            startQueue()
        }
    }

    private fun onNetworkLost() {
        if (waitingNetwork) return
        waitingNetwork = true
        val reason = ctx.getString(R.string.err_network_lost)
        live.values.filter { it.isActive && it.status != DStatus.SAVING }.forEach { item ->
            networkPaused.add(item.id)
            tasks[item.id]?.control?.paused = true
            live[item.id] = item.copy(status = DStatus.PAUSED, speed = 0, error = reason)
        }
        publish(force = true)
    }

    private fun onNetworkBack() {
        if (!waitingNetwork) return
        waitingNetwork = false
        val ids = networkPaused.toList()
        networkPaused.clear()
        ids.forEach { id ->
            val item = live[id] ?: return@forEach
            if (item.status == DStatus.PAUSED) {
                live[id] = item.copy(status = DStatus.QUEUED, error = null)
            }
        }
        if (ids.isNotEmpty()) {
            persistAsync(live.values.toList())
            publish(force = true)
            startQueue()
        }
    }

    private fun onSaveProgress(id: Long, copied: Long, total: Long) {
        val item = live[id] ?: return
        live[id] = item.copy(
            downloadedBytes = copied,
            totalBytes = if (total > 0) total else item.totalBytes,
        )
        publish()
    }

    fun shutdown() {
        scope.cancel()
    }
}
