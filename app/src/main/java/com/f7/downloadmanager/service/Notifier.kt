package com.f7.downloadmanager.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.f7.downloadmanager.MainActivity
import com.f7.downloadmanager.R
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.core.Fmt

object Notifier {
    const val CHANNEL_ID = "downloads"
    const val FOREGROUND_ID = 7101

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.channel_downloads_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildForeground(ctx: Context, items: List<DownloadItem>, speed: Long): Notification {
        ensureChannel(ctx)
        val active = items.filter { it.isActive || it.status == DStatus.QUEUED }
        val running = active.count { it.isActive }
        val single = active.size == 1
        val first = active.firstOrNull()
        val title = when {
            single && first != null -> first.fileName
            active.isNotEmpty() -> ctx.resources.getQuantityString(R.plurals.downloads_running, active.size, active.size)
            else -> ctx.getString(R.string.app_name)
        }
        val totalBytes = active.sumOf { it.totalBytes.coerceAtLeast(0) }
        val doneBytes = active.sumOf { it.downloadedBytes.coerceAtLeast(0) }
        val weighted = if (totalBytes > 0) (doneBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else if (single) (first?.progress?.times(100)?.toInt() ?: 0) else 0
        val saving = active.firstOrNull { it.status == DStatus.SAVING }
        val detail = buildString {
            if (saving != null) {
                append(ctx.getString(R.string.notif_saving))
                if (saving.totalBytes > 0) {
                    append("  ·  ")
                    append(Fmt.percent(saving.progress))
                }
            } else if (single && first != null) {
                append(Fmt.percent(first.progress))
                if (first.totalBytes > 0) {
                    append("  ·  ")
                    append(Fmt.bytes(first.totalBytes))
                }
                val eta = if (first.isHls) null else Fmt.eta(first.totalBytes - first.downloadedBytes, speed)
                if (eta != null) {
                    append("  ·  ")
                    append(ctx.getString(R.string.eta_left, eta))
                }
            } else if (active.isNotEmpty()) {
                append(running)
                append(" / ")
                append(active.size)
            } else {
                append(ctx.getString(R.string.status_connecting))
            }
            if (speed > 0) {
                append("  ·  ")
                append(Fmt.speed(speed))
                append("/s")
            }
        }
        val pauseIntent = PendingIntent.getBroadcast(
            ctx,
            1,
            Intent(ctx, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val resumeIntent = PendingIntent.getBroadcast(
            ctx,
            2,
            Intent(ctx, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, weighted.coerceIn(0, 100), totalBytes <= 0 && active.isNotEmpty())
            .addAction(0, ctx.getString(R.string.notif_pause_all), pauseIntent)
            .addAction(0, ctx.getString(R.string.notif_resume_all), resumeIntent)
        return builder.build()
    }

    fun completed(ctx: Context, item: DownloadItem) {
        post(ctx, item.id.toInt() + 7200, ctx.getString(R.string.notif_complete_title), ctx.getString(R.string.notif_complete_body, item.fileName))
    }

    fun failed(ctx: Context, item: DownloadItem) {
        post(ctx, item.id.toInt() + 8200, ctx.getString(R.string.notif_failed_title), item.fileName)
    }

    fun scheduleStarted(ctx: Context) {
        post(ctx, 9100, ctx.getString(R.string.schedule_notif_title), ctx.getString(R.string.schedule_notif_body))
    }

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannel(ctx)
        val contentIntent = PendingIntent.getActivity(
            ctx,
            id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, notification) }
    }
}
