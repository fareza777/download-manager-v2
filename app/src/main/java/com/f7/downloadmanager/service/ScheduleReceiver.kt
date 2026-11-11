package com.f7.downloadmanager.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.f7.downloadmanager.graph

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = context.applicationContext.graph.engine
        when (intent.action) {
            ACTION_PAUSE -> engine.pauseAll()
            ACTION_RESUME -> {
                engine.resumeAll()
                DownloadService.start(context)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.f7.downloadmanager.NOTIF_PAUSE"
        const val ACTION_RESUME = "com.f7.downloadmanager.NOTIF_RESUME"
    }
}

/** Starts the queue at the scheduled time using an inexact idle-friendly alarm. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ALARM_ACTION) return
        val app = context.applicationContext
        runCatching {
            app.graph.settings.setScheduleAt(0L)
            app.graph.engine.startQueue()
            DownloadService.start(context)
            Notifier.scheduleStarted(context)
        }
    }
}

object Scheduler {
    const val ALARM_ACTION = "com.f7.downloadmanager.SCHEDULE"

    fun schedule(context: Context, triggerAtMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, triggerAtMillis)
        manager.cancel(pending)
        if (triggerAtMillis <= 0L) return
        val safe = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safe, pending)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, 0L)
        manager.cancel(pending)
    }

    private fun pendingIntent(context: Context, triggerAt: Long): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ALARM_ACTION)
        return PendingIntent.getBroadcast(
            context,
            (triggerAt / 1000L).toInt().coerceAtLeast(1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
