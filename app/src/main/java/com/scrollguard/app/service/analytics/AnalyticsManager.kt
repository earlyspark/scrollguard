package com.scrollguard.app.service.analytics

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Privacy-safe analytics manager for ScrollGuard.
 * Handles user consent, data collection, and ensures no personal content is logged.
 */
class AnalyticsManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "scrollguard_analytics"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        private const val KEY_USER_CONSENT_GIVEN = "user_consent_given"
        private const val KEY_LAST_CONSENT_DATE = "last_consent_date"
        private const val KEY_SESSION_ID = "current_session_id"
        
        // Privacy safeguards
        private const val MAX_EVENT_QUEUE_SIZE = 100
        private const val MAX_STRING_LENGTH = 100
        private const val BATCH_SIZE = 10
    }

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var encryptedPrefs: SharedPreferences
    private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventQueue = ConcurrentLinkedQueue<AnalyticsEvent>()
    private val queueMutex = Mutex()
    
    private var isInitialized = false
    private var currentSessionId: String = generateSessionId()

    data class AnalyticsEvent(
        val name: String,
        val parameters: Map<String, Any>,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Initialize the analytics manager
     */
    fun initialize() {
        if (isInitialized) return
        
        try {
            // Initialize Firebase Analytics
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            
            // Initialize encrypted preferences
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            // Set analytics collection based on user consent
            val consentGiven = encryptedPrefs.getBoolean(KEY_USER_CONSENT_GIVEN, false)
            firebaseAnalytics.setAnalyticsCollectionEnabled(consentGiven)
            
            // Generate new session ID
            currentSessionId = generateSessionId()
            encryptedPrefs.edit().putString(KEY_SESSION_ID, currentSessionId).apply()
            
            isInitialized = true
            Timber.d("Analytics manager initialized, consent: $consentGiven")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize analytics manager")
        }
    }

    /**
     * Check if user has given analytics consent
     */
    fun hasUserConsent(): Boolean {
        return if (::encryptedPrefs.isInitialized) {
            encryptedPrefs.getBoolean(KEY_USER_CONSENT_GIVEN, false)
        } else false
    }

    /**
     * Set user consent for analytics
     */
    fun setUserConsent(granted: Boolean) {
        if (!isInitialized) return
        
        encryptedPrefs.edit()
            .putBoolean(KEY_USER_CONSENT_GIVEN, granted)
            .putBoolean(KEY_ANALYTICS_ENABLED, granted)
            .putLong(KEY_LAST_CONSENT_DATE, System.currentTimeMillis())
            .apply()
        
        firebaseAnalytics.setAnalyticsCollectionEnabled(granted)
        
        Timber.d("Analytics consent set to: $granted")
        
        // Log consent event
        if (granted) {
            logEvent("analytics_consent_granted") {
                param("timestamp", System.currentTimeMillis())
            }
        }
    }

    /**
     * Check if analytics is currently enabled
     */
    fun isAnalyticsEnabled(): Boolean {
        return if (::encryptedPrefs.isInitialized) {
            encryptedPrefs.getBoolean(KEY_ANALYTICS_ENABLED, false)
        } else false
    }

    /**
     * Log an analytics event with privacy safeguards
     */
    fun logEvent(eventName: String, parameterBuilder: ParameterBuilder.() -> Unit = {}) {
        if (!isInitialized || !isAnalyticsEnabled()) return
        
        analyticsScope.launch {
            try {
                val builder = ParameterBuilder()
                builder.parameterBuilder()
                
                val sanitizedParams = sanitizeParameters(builder.parameters)
                val event = AnalyticsEvent(
                    name = sanitizeEventName(eventName),
                    parameters = sanitizedParams
                )
                
                queueEvent(event)
                
            } catch (e: Exception) {
                Timber.e(e, "Error logging analytics event: $eventName")
            }
        }
    }

    /**
     * Log content filtering event (privacy-safe)
     */
    fun logContentFiltered(
        packageName: String,
        contentType: String,
        isProductive: Boolean,
        confidence: Float,
        processingTimeMs: Int
    ) {
        logEvent("content_filtered") {
            param("app_category", sanitizePackageName(packageName))
            param("content_type", contentType)
            param("is_productive", isProductive)
            param("confidence_bucket", bucketizeConfidence(confidence))
            param("processing_time_bucket", bucketizeProcessingTime(processingTimeMs))
            param("session_id", currentSessionId)
        }
    }

    /**
     * Log user feedback event (privacy-safe)
     */
    fun logUserFeedback(
        feedbackType: String,
        originalClassification: Boolean,
        userCorrection: Boolean?
    ) {
        logEvent("user_feedback") {
            param("feedback_type", feedbackType)
            param("original_classification", if (originalClassification) "productive" else "unproductive")
            param("user_correction", userCorrection?.let { if (it) "productive" else "unproductive" } ?: "none")
            param("accuracy", if (userCorrection == null) "correct" else "incorrect")
        }
    }

    /**
     * Log performance metrics
     */
    fun logPerformanceMetrics(
        memoryUsageMB: Float,
        batteryDrainPercent: Float,
        processingQueueSize: Int
    ) {
        logEvent("performance_metrics") {
            param("memory_usage_bucket", bucketizeMemoryUsage(memoryUsageMB))
            param("battery_drain_bucket", bucketizeBatteryDrain(batteryDrainPercent))
            param("queue_size_bucket", bucketizeQueueSize(processingQueueSize))
        }
    }

    /**
     * Log accessibility service health
     */
    fun logAccessibilityServiceHealth(
        isActive: Boolean,
        errorCount: Int,
        uptimeHours: Float
    ) {
        logEvent("accessibility_service_health") {
            param("is_active", isActive)
            param("error_count_bucket", bucketizeErrorCount(errorCount))
            param("uptime_bucket", bucketizeUptime(uptimeHours))
        }
    }

    /**
     * Get current session ID
     */
    fun getCurrentSessionId(): String = currentSessionId

    /**
     * Start new session
     */
    fun startNewSession() {
        currentSessionId = generateSessionId()
        if (::encryptedPrefs.isInitialized) {
            encryptedPrefs.edit().putString(KEY_SESSION_ID, currentSessionId).apply()
        }
        
        logEvent("session_start") {
            param("session_id", currentSessionId)
        }
    }

    /**
     * End current session
     */
    fun endCurrentSession() {
        logEvent("session_end") {
            param("session_id", currentSessionId)
            param("session_duration", System.currentTimeMillis())
        }
        
        // Process any remaining queued events
        processEventQueue()
    }

    /**
     * Cleanup analytics manager
     */
    fun cleanup() {
        processEventQueue() // Process any remaining events
        isInitialized = false
        Timber.d("Analytics manager cleaned up")
    }

    // Private helper methods

    private suspend fun queueEvent(event: AnalyticsEvent) {
        queueMutex.withLock {
            if (eventQueue.size >= MAX_EVENT_QUEUE_SIZE) {
                eventQueue.poll() // Remove oldest event
            }
            eventQueue.offer(event)
            
            // Process queue if it reaches batch size
            if (eventQueue.size >= BATCH_SIZE) {
                processEventQueue()
            }
        }
    }

    private fun processEventQueue() {
        analyticsScope.launch {
            queueMutex.withLock {
                val eventsToProcess = mutableListOf<AnalyticsEvent>()
                repeat(minOf(BATCH_SIZE, eventQueue.size)) {
                    eventQueue.poll()?.let { eventsToProcess.add(it) }
                }
                
                eventsToProcess.forEach { event ->
                    try {
                        firebaseAnalytics.logEvent(event.name) {
                            event.parameters.forEach { (key, value) ->
                                when (value) {
                                    is String -> param(key, value)
                                    is Long -> param(key, value)
                                    is Double -> param(key, value)
                                    is Boolean -> param(key, if (value) 1L else 0L)
                                    is Int -> param(key, value.toLong())
                                    is Float -> param(key, value.toDouble())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error sending analytics event: ${event.name}")
                    }
                }
            }
        }
    }

    private fun sanitizeEventName(eventName: String): String {
        return eventName.take(40) // Firebase limit
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .lowercase()
    }

    private fun sanitizeParameters(params: Map<String, Any>): Map<String, Any> {
        return params.mapKeys { (key, _) ->
            key.take(40).replace(Regex("[^a-zA-Z0-9_]"), "_")
        }.mapValues { (_, value) ->
            when (value) {
                is String -> sanitizeStringValue(value)
                else -> value
            }
        }
    }

    private fun sanitizeStringValue(value: String): String {
        return value.take(MAX_STRING_LENGTH)
            .replace(Regex("[^a-zA-Z0-9_\\s\\-]"), "")
            .trim()
    }

    private fun sanitizePackageName(packageName: String): String {
        return when {
            packageName.contains("instagram") -> "social_photo"
            packageName.contains("tiktok") || packageName.contains("musically") -> "social_video"
            packageName.contains("twitter") -> "social_microblog"
            packageName.contains("reddit") -> "social_forum"
            packageName.contains("youtube") -> "video_platform"
            packageName.contains("facebook") -> "social_general"
            else -> "other"
        }
    }

    private fun bucketizeConfidence(confidence: Float): String {
        return when {
            confidence >= 0.9f -> "very_high"
            confidence >= 0.7f -> "high"
            confidence >= 0.5f -> "medium"
            confidence >= 0.3f -> "low"
            else -> "very_low"
        }
    }

    private fun bucketizeProcessingTime(timeMs: Int): String {
        return when {
            timeMs < 50 -> "very_fast"
            timeMs < 200 -> "fast"
            timeMs < 500 -> "medium"
            timeMs < 1000 -> "slow"
            else -> "very_slow"
        }
    }

    private fun bucketizeMemoryUsage(memoryMB: Float): String {
        return when {
            memoryMB < 100 -> "low"
            memoryMB < 300 -> "medium"
            memoryMB < 500 -> "high"
            else -> "very_high"
        }
    }

    private fun bucketizeBatteryDrain(drainPercent: Float): String {
        return when {
            drainPercent < 1.0f -> "minimal"
            drainPercent < 3.0f -> "low"
            drainPercent < 5.0f -> "medium"
            drainPercent < 10.0f -> "high"
            else -> "excessive"
        }
    }

    private fun bucketizeQueueSize(size: Int): String {
        return when {
            size < 5 -> "small"
            size < 20 -> "medium"
            size < 50 -> "large"
            else -> "very_large"
        }
    }

    private fun bucketizeErrorCount(errors: Int): String {
        return when {
            errors == 0 -> "none"
            errors < 3 -> "few"
            errors < 10 -> "some"
            else -> "many"
        }
    }

    private fun bucketizeUptime(hours: Float): String {
        return when {
            hours < 1 -> "short"
            hours < 8 -> "medium"
            hours < 24 -> "long"
            else -> "very_long"
        }
    }

    private fun generateSessionId(): String {
        return "sg_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Parameter builder for analytics events
     */
    class ParameterBuilder {
        val parameters = mutableMapOf<String, Any>()

        fun param(key: String, value: String) {
            parameters[key] = value
        }

        fun param(key: String, value: Long) {
            parameters[key] = value
        }

        fun param(key: String, value: Double) {
            parameters[key] = value
        }

        fun param(key: String, value: Boolean) {
            parameters[key] = value
        }

        fun param(key: String, value: Int) {
            parameters[key] = value
        }

        fun param(key: String, value: Float) {
            parameters[key] = value
        }
    }
}