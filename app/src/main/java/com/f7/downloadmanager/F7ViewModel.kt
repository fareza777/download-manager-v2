package com.f7.downloadmanager

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.engine.DownloadEngine
import com.f7.downloadmanager.service.DownloadService
import com.f7.downloadmanager.service.Scheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class F7ViewModel(app: Application) : AndroidViewModel(app) {

    private val graph get() = getApplication<Application>().graph
    private val context: Application get() = getApplication()

    val engine: DownloadEngine get() = graph.engine
    val settings get() = graph.settings
    val items = engine.items
    val speed = engine.totalSpeed

    val pendingShare = graph.pendingShare

    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history = _history.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var lastServiceStart = 0L

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val mbps = engine.totalSpeed.value / (1024f * 1024f)
                _history.value = (_history.value + mbps).takeLast(40)
            }
        }
        viewModelScope.launch {
            items.collect { list ->
                if (list.any { it.isActive || it.status == DStatus.QUEUED }) {
                    val now = System.currentTimeMillis()
                    if (now - lastServiceStart > 5000) {
                        lastServiceStart = now
                        runCatching { DownloadService.start(context) }
                    }
                }
            }
        }
        if (settings.autoResume.value) engine.resumeInterrupted()
    }

    fun addRaw(text: String, category: String?) {
        viewModelScope.launch {
            val links = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (links.isEmpty()) return@launch
            report(engine.add(links, category))
        }
    }

    fun addLink(url: String, headers: Map<String, String> = emptyMap()) {
        viewModelScope.launch { report(engine.add(listOf(url), null, headers)) }
    }

    private fun report(result: DownloadEngine.AddResult) {
        result.magnets.forEach { magnet ->
            val opened = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (!opened) _messages.tryEmit(context.getString(R.string.add_magnet_note))
        }
        when {
            result.added > 0 -> _messages.tryEmit(
                context.resources.getQuantityString(R.plurals.downloads_added, result.added, result.added),
            )
            result.invalid.isNotEmpty() -> _messages.tryEmit(context.getString(R.string.add_invalid_url))
            result.magnets.isEmpty() -> _messages.tryEmit(context.getString(R.string.add_no_valid_urls))
        }
    }

    fun pause(id: Long) {
        engine.pause(id)
        _messages.tryEmit(context.getString(R.string.toast_paused))
    }

    fun resume(id: Long) {
        engine.resume(id)
        _messages.tryEmit(context.getString(R.string.toast_resumed))
    }

    fun retry(id: Long) = engine.retry(id)

    fun cancel(id: Long) = engine.cancel(id)

    fun remove(item: DownloadItem) {
        viewModelScope.launch { engine.remove(item.id) }
    }

    fun deleteFile(item: DownloadItem) {
        viewModelScope.launch {
            engine.deleteFile(item)
            _messages.tryEmit(context.getString(R.string.toast_deleted))
        }
    }

    fun verify(item: DownloadItem) {
        viewModelScope.launch {
            _messages.tryEmit(context.getString(R.string.toast_verify_running))
            val sum = engine.verify(item)
            _messages.tryEmit(
                if (sum != null) context.getString(R.string.toast_verified, sum.take(24))
                else context.getString(R.string.files_verify_failed),
            )
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            engine.clearFinished()
            _messages.tryEmit(context.getString(R.string.toast_deleted))
        }
    }

    fun clearHistory() = clearFinished()

    fun startQueue() {
        engine.startQueue()
        runCatching { DownloadService.start(context) }
    }

    fun pauseAll() = engine.pauseAll()

    fun resumeAll() {
        engine.resumeAll()
        runCatching { DownloadService.start(context) }
    }

    fun setTheme(id: String) = settings.setTheme(id)

    fun setDarkMode(mode: String) = settings.setDarkMode(mode)

    fun setThreads(value: Int) = settings.setThreads(value)

    fun setConcurrent(value: Int) = settings.setConcurrent(value)

    fun setSpeedLimit(kbps: Int) = settings.setSpeedLimitKbps(kbps)

    fun setNotifications(enabled: Boolean) = settings.setNotifications(enabled)

    fun setAutoResume(enabled: Boolean) = settings.setAutoResume(enabled)

    fun setWifiOnly(value: Boolean) = settings.setWifiOnly(value)

    fun setChargingOnly(value: Boolean) = settings.setChargingOnly(value)

    fun setAutoRetry(value: Boolean) = settings.setAutoRetry(value)

    fun setMaxRetries(value: Int) = settings.setMaxRetries(value)

    fun setConfirmRemove(value: Boolean) = settings.setConfirmRemove(value)

    fun setAmbient(value: Boolean) = settings.setAmbient(value)

    fun setProgressNotifications(value: Boolean) = settings.setProgressNotifications(value)

    fun completeOnboarding() = settings.setOnboardingDone(true)

    fun showOnboarding() = settings.setOnboardingDone(false)

    fun setBrowserDesktop(value: Boolean) = settings.setBrowserDesktop(value)

    fun addBrowserHistory(url: String, title: String) = settings.addBrowserHistory(url, title)

    fun toggleBrowserBookmark(url: String, title: String) = settings.toggleBrowserBookmark(url, title)

    fun clearBrowserHistory() = settings.clearBrowserHistory()

    fun batchPause(ids: Set<Long>) = ids.forEach { engine.pause(it) }

    fun batchResume(ids: Set<Long>) {
        ids.forEach { engine.resume(it) }
        runCatching { DownloadService.start(context) }
    }

    fun batchRemove(ids: Set<Long>) {
        viewModelScope.launch { ids.forEach { engine.remove(it) } }
    }

    fun stagingBytes(): Long = graph.storage.stagingBytes()

    fun clearStaging() {
        graph.storage.clearAllStaging()
        _messages.tryEmit(context.getString(R.string.toast_staging_cleared))
    }

    fun setTree(uri: String?, label: String?) {
        settings.setTree(uri, label)
        _messages.tryEmit(context.getString(R.string.toast_saved))
    }

    fun schedule(atMillis: Long) {
        settings.setScheduleAt(if (atMillis > 0) atMillis else 0L)
        if (atMillis > 0) Scheduler.schedule(context, atMillis) else Scheduler.cancel(context)
    }

    fun notify(text: String) {
        _messages.tryEmit(text)
    }

    fun consumeShare() {
        graph.pendingShare.value = null
    }

    fun storageFreeBytes(): Long = graph.storage.freeBytes()
}
