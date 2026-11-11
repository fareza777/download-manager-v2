package com.f7.downloadmanager.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.data.Settings
import com.f7.downloadmanager.engine.DownloadEngine
import com.f7.downloadmanager.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the process alive with a data-sync foreground service while downloads run, and
 * mirrors engine progress into the notification. The service stops itself as soon as the
 * queue is empty, so it never sits in the background without work.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: DownloadEngine
    private lateinit var settings: Settings
    private var collector: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeAcquiredAt = 0L
    private var lastNotificationAt = 0L

    override fun onCreate() {
        super.onCreate()
        engine = applicationContext.graph.engine
        settings = applicationContext.graph.settings
        Notifier.ensureChannel(this)
        val notification = Notifier.buildForeground(this, engine.items.value, engine.totalSpeed.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifier.FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifier.FOREGROUND_ID, notification)
        }
        collector = scope.launch {
            combine(engine.items, engine.totalSpeed) { items, speed -> items to speed }.collect { (items, speed) ->
                val active = items.any { it.isActive || it.status == DStatus.QUEUED }
                if (!active) {
                    setWake(false)
                    delay(900)
                    if (engine.isIdle()) {
                        stopForegroundCompat()
                        stopSelf()
                    }
                    return@collect
                }
                setWake(true)
                val now = System.currentTimeMillis()
                val interval = if (settings.progressNotifications.value) 700L else 5000L
                if (now - lastNotificationAt < interval && speed > 0) return@collect
                lastNotificationAt = now
                runCatching {
                    val manager = getSystemService(android.app.NotificationManager::class.java)
                    manager?.notify(Notifier.FOREGROUND_ID, Notifier.buildForeground(this@DownloadService, items, speed))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> engine.pauseAll()
            ACTION_RESUME_ALL -> engine.resumeAll()
            else -> engine.startQueue()
        }
        return START_NOT_STICKY
    }

    private fun setWake(on: Boolean) {
        if (on) {
            val now = SystemClock.elapsedRealtime()
            if (wakeLock?.isHeld == true) {
                // A 30 minute lock used to expire silently on long transfers; renew before that.
                if (now - wakeAcquiredAt < RENEW_AFTER_MS) return
                runCatching { wakeLock?.release() }
                wakeLock = null
            }
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "f7:download").apply {
                setReferenceCounted(false)
                acquire(LOCK_TIMEOUT_MS)
            }
            wakeAcquiredAt = now
        } else {
            runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
            wakeLock = null
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        collector?.cancel()
        scope.cancel()
        setWake(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.f7.downloadmanager.START"
        const val ACTION_PAUSE_ALL = "com.f7.downloadmanager.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.f7.downloadmanager.RESUME_ALL"
        private const val RENEW_AFTER_MS = 25 * 60 * 1000L
        private const val LOCK_TIMEOUT_MS = 60 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, DownloadService::class.java).setAction(action)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
