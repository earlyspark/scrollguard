package com.scrollguard.app.ui.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.scrollguard.app.R
import com.scrollguard.app.ui.activity.MainActivity
import com.scrollguard.app.ui.activity.SettingsActivity

/**
 * Helper class for managing app notifications.
 * Creates and manages system notifications for service status and alerts.
 */
object NotificationHelper {

    private const val CHANNEL_SERVICE = "service_status"
    private const val CHANNEL_DOWNLOAD = "model_downloads"
    private const val CHANNEL_ALERTS = "alerts"

    private const val NOTIFICATION_SERVICE = 1001
    private const val NOTIFICATION_DOWNLOAD = 1002
    private const val NOTIFICATION_ERROR = 1003

    /**
     * Create notification channels for Android O+
     */
    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Service status channel
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_service_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        // Model download channel
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            context.getString(R.string.notification_channel_download),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_download_description)
            setShowBadge(true)
        }

        // Alerts channel
        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important alerts and error messages"
            setShowBadge(true)
            enableVibration(true)
            enableLights(true)
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(downloadChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }

    /**
     * Create service status notification
     */
    fun createServiceNotification(context: Context, isActive: Boolean): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isActive) {
            context.getString(R.string.notification_filtering_active)
        } else {
            context.getString(R.string.notification_filtering_paused)
        }

        val icon = if (isActive) {
            R.drawable.ic_shield
        } else {
            R.drawable.ic_eye_off
        }

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText("Tap to open ScrollGuard")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }

    /**
     * Show model download progress notification
     */
    fun showDownloadProgressNotification(context: Context, progress: Int, max: Int = 100) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.notification_model_downloading))
            .setContentText("$progress%")
            .setProgress(max, progress, false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(NOTIFICATION_DOWNLOAD, notification)
    }

    /**
     * Show model download complete notification
     */
    fun showDownloadCompleteNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.notification_model_ready))
            .setContentText("ScrollGuard is ready to filter content")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_DOWNLOAD, notification)
    }

    /**
     * Show error notification
     */
    fun showErrorNotification(context: Context, title: String, message: String, actionIntent: Intent? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Add action if provided
        actionIntent?.let { intent ->
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(NOTIFICATION_ERROR, builder.build())
    }

    /**
     * Show accessibility permission required notification
     */
    fun showAccessibilityPermissionNotification(context: Context) {
        val intent = Intent(context, SettingsActivity::class.java)
        showErrorNotification(
            context,
            "Permission Required",
            "ScrollGuard needs accessibility permission to filter content. Tap to open settings.",
            intent
        )
    }

    /**
     * Cancel download notification
     */
    fun cancelDownloadNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_DOWNLOAD)
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}