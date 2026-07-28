package io.github.websocketrecorder.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

internal class RecorderNotifications(
    private val context: Context,
    private val enabled: Boolean,
) {
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastNotificationAt = AtomicLong()

    init {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Recorder",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows captured WebSocket activity"
                    setShowBadge(false)
                },
            )
        }
    }

    fun onEventStored(eventId: Long, count: Long, summary: String) {
        if (!enabled || !canPostNotifications()) return
        val now = SystemClock.elapsedRealtime()
        val previous = lastNotificationAt.get()
        if (now - previous < MIN_NOTIFICATION_INTERVAL_MS ||
            !lastNotificationAt.compareAndSet(previous, now)
        ) return

        val openInspector = PendingIntent.getActivity(
            context,
            0,
            RecorderActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("WebSocket Recorder")
            .setContentText(summary)
            .setSubText("$count captured events")
            .setContentIntent(openInspector)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "websocket_recorder"
        const val NOTIFICATION_ID = 0x5753
        const val MIN_NOTIFICATION_INTERVAL_MS = 750L
    }
}
