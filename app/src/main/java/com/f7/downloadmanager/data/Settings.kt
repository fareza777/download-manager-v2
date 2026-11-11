package com.f7.downloadmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("f7_settings", Context.MODE_PRIVATE)

    val theme = stringSetting("theme", "JADE")
    val darkMode = stringSetting("dark_mode", "light")
    val language = stringSetting("language", "system")
    val treeUri = stringSetting("tree_uri", null)
    val treeLabel = stringSetting("tree_label", null)
    val browserHome = stringSetting("browser_home", "https://duckduckgo.com/")

    val threads = intSetting("threads", 8)
    val concurrent = intSetting("concurrent", 2)
    val speedLimitKbps = intSetting("speed_limit", 0)
    val autoResume = boolSetting("auto_resume", true)
    val notifications = boolSetting("notifications", true)
    val scheduleAt = longSetting("schedule_at", 0L)

    // Transfers
    val wifiOnly = boolSetting("wifi_only", false)
    val chargingOnly = boolSetting("charging_only", false)
    val autoRetry = boolSetting("auto_retry", true)
    val maxRetries = intSetting("max_retries", 3)
    val progressNotifications = boolSetting("progress_notifications", true)

    // Behaviour
    val confirmRemove = boolSetting("confirm_remove", true)
    val ambient = boolSetting("ambient", true)
    val onboardingDone = boolSetting("onboarding_done", false)

    // Browser
    val browserDesktop = boolSetting("browser_desktop", false)
    val browserHistory = stringSetting("browser_history", "")
    val browserBookmarks = stringSetting("browser_bookmarks", "")

    val threadsFlow: StateFlow<Int> get() = threads
    val concurrentFlow: StateFlow<Int> get() = concurrent
    val speedLimitFlow: StateFlow<Int> get() = speedLimitKbps

    fun setTheme(id: String) = putString("theme", theme, id)

    fun setDarkMode(mode: String) = putString("dark_mode", darkMode, mode)

    fun setLanguage(code: String) = putString("language", language, code)

    fun setThreads(value: Int) {
        val v = value.coerceIn(1, 16)
        threads.value = v
        prefs.edit().putInt("threads", v).apply()
    }

    fun setConcurrent(value: Int) {
        val v = value.coerceIn(1, 4)
        concurrent.value = v
        prefs.edit().putInt("concurrent", v).apply()
    }

    fun setSpeedLimitKbps(value: Int) {
        val v = value.coerceAtLeast(0)
        speedLimitKbps.value = v
        prefs.edit().putInt("speed_limit", v).apply()
    }

    fun setAutoResume(value: Boolean) = putBoolean("auto_resume", autoResume, value)

    fun setNotifications(value: Boolean) = putBoolean("notifications", notifications, value)

    fun setProgressNotifications(value: Boolean) = putBoolean("progress_notifications", progressNotifications, value)

    fun setWifiOnly(value: Boolean) = putBoolean("wifi_only", wifiOnly, value)

    fun setChargingOnly(value: Boolean) = putBoolean("charging_only", chargingOnly, value)

    fun setAutoRetry(value: Boolean) = putBoolean("auto_retry", autoRetry, value)

    fun setMaxRetries(value: Int) {
        val v = value.coerceIn(0, 10)
        maxRetries.value = v
        prefs.edit().putInt("max_retries", v).apply()
    }

    fun setConfirmRemove(value: Boolean) = putBoolean("confirm_remove", confirmRemove, value)

    fun setAmbient(value: Boolean) = putBoolean("ambient", ambient, value)

    fun setOnboardingDone(value: Boolean) = putBoolean("onboarding_done", onboardingDone, value)

    fun setBrowserDesktop(value: Boolean) = putBoolean("browser_desktop", browserDesktop, value)

    /** Most recent first, de-duplicated, capped at 20 entries. */
    fun addBrowserHistory(url: String, title: String) {
        if (url.isBlank()) return
        val entry = "$url\t${title.ifBlank { url }}"
        val lines = browserHistory.value.orEmpty().lines().filter { it.isNotBlank() }
        val next = (listOf(entry) + lines.filterNot { it.substringBefore('\t') == url }).take(20)
        setBrowserList(browserHistory, "browser_history", next)
    }

    fun toggleBrowserBookmark(url: String, title: String) {
        if (url.isBlank()) return
        val lines = browserBookmarks.value.orEmpty().lines().filter { it.isNotBlank() }
        val exists = lines.any { it.substringBefore('\t') == url }
        val next = if (exists) {
            lines.filterNot { it.substringBefore('\t') == url }
        } else {
            (listOf("$url\t${title.ifBlank { url }}") + lines).take(40)
        }
        setBrowserList(browserBookmarks, "browser_bookmarks", next)
    }

    fun clearBrowserHistory() = setBrowserList(browserHistory, "browser_history", emptyList())

    private fun setBrowserList(flow: MutableStateFlow<String?>, key: String, lines: List<String>) {
        val value = lines.joinToString("\n")
        flow.value = value
        prefs.edit().putString(key, value).apply()
    }

    fun setTree(uri: String?, label: String?) {
        treeUri.value = uri
        treeLabel.value = label
        prefs.edit().putString("tree_uri", uri).putString("tree_label", label).apply()
    }

    fun setScheduleAt(value: Long) {
        scheduleAt.value = value
        prefs.edit().putLong("schedule_at", value).apply()
    }

    fun setBrowserHome(value: String) {
        browserHome.value = value
        prefs.edit().putString("browser_home", value).apply()
    }

    private fun putString(key: String, flow: MutableStateFlow<String?>, value: String) {
        flow.value = value
        prefs.edit().putString(key, value).apply()
    }

    private fun putBoolean(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) {
        flow.value = value
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun stringSetting(key: String, def: String?): MutableStateFlow<String?> =
        MutableStateFlow(prefs.getString(key, def))

    private fun intSetting(key: String, def: Int): MutableStateFlow<Int> =
        MutableStateFlow(prefs.getInt(key, def))

    private fun longSetting(key: String, def: Long): MutableStateFlow<Long> =
        MutableStateFlow(prefs.getLong(key, def))

    private fun boolSetting(key: String, def: Boolean): MutableStateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(key, def))
}
