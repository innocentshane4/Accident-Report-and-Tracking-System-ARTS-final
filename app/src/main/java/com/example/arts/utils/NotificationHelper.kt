package com.example.arts.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.arts.R
import com.example.arts.activities.MainActivity

/**
 * Creates the app's notification channel and shows system notifications for
 * incoming FCM pushes. Android 8+ requires every notification to belong to a
 * channel, which must exist before the first notification is shown.
 */
object NotificationHelper {

    const val CHANNEL_ID = "arts_alerts"
    private var nextNotificationId = 1000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ARTS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incident updates, dispatch confirmations, and emergency alerts"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Shows a system notification. Tapping it opens MainActivity; if an
     * incidentId is supplied it is passed along as an extra.
     */
    fun show(context: Context, title: String, body: String, incidentId: String? = null) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (incidentId != null) putExtra("incidentId", incidentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // FIX-pattern: guard against a revoked POST_NOTIFICATIONS permission on
        // Android 13+. Calling notify() without checking first throws a
        // SecurityException if the user denied the permission, which would
        // crash the app the same way the earlier Android 14/15 issues did.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(context).notify(nextNotificationId++, notification)
            } catch (e: SecurityException) {
                // Permission was revoked between the check and the call — safe to ignore.
            }
        }
    }
}
