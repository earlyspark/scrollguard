package com.scrollguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.ui.activity.MainActivity
import timber.log.Timber

/**
 * Foreground service for background LLM inference processing.
 * This service ensures the app can continue processing content even when minimized.
 */
class LLMInferenceService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "scrollguard_inference"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_NAME = "Content Filtering"
        private const val CHANNEL_DESCRIPTION = "Background content analysis and filtering"
    }

    private lateinit var app: ScrollGuardApplication
    private val binder = LLMInferenceBinder()

    inner class LLMInferenceBinder : Binder() {
        fun getService(): LLMInferenceService = this@LLMInferenceService
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("LLMInferenceService created")
        
        app = application as ScrollGuardApplication
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("LLMInferenceService started")
        
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("LLMInferenceService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateNotification(filteredCount: Int, timesSaved: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ScrollGuard Active")
            .setContentText("Filtered $filteredCount items • $timesSaved saved")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}