package com.scrollguard.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Data model representing a filtering session.
 * Tracks user activity and filtering effectiveness over time.
 */
@Entity(tableName = "filter_sessions")
@Serializable
data class FilterSession(
    @PrimaryKey
    val sessionId: String,
    
    // Session timing
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long = 0,
    
    // App context
    val packageName: String,
    val appVersion: String? = null,
    
    // Content statistics
    val totalContentSeen: Int = 0,
    val totalContentFiltered: Int = 0,
    val contentByType: Map<ContentType, Int> = emptyMap(),
    
    // Performance metrics
    val averageProcessingTimeMs: Float = 0f,
    val maxProcessingTimeMs: Int = 0,
    val totalProcessingTimeMs: Long = 0,
    
    // User interaction
    val userFeedbackCount: Int = 0,
    val falsePositiveCount: Int = 0,
    val falseNegativeCount: Int = 0,
    val manualOverrideCount: Int = 0,
    
    // Effectiveness metrics
    val estimatedTimeSavedMs: Long = 0,
    val userSatisfactionScore: Float? = null,
    
    // Technical metrics
    val memoryUsageMB: Float = 0f,
    val batteryDrainPercent: Float = 0f,
    val errorCount: Int = 0,
    
    // Device context
    val deviceModel: String = "",
    val androidVersion: Int = 0,
    val appVersionCode: Int = 0
) {
    
    /**
     * Calculate filtering effectiveness as a percentage
     */
    fun getFilteringEffectiveness(): Float {
        return if (totalContentSeen > 0) {
            (totalContentFiltered.toFloat() / totalContentSeen) * 100f
        } else 0f
    }
    
    /**
     * Calculate user feedback accuracy
     */
    fun getFeedbackAccuracy(): Float {
        val totalFeedback = userFeedbackCount
        return if (totalFeedback > 0) {
            val correctFeedback = totalFeedback - falsePositiveCount - falseNegativeCount
            (correctFeedback.toFloat() / totalFeedback) * 100f
        } else 100f
    }
    
    /**
     * Calculate average time saved per filtered item
     */
    fun getAverageTimeSavedPerFilter(): Float {
        return if (totalContentFiltered > 0) {
            estimatedTimeSavedMs.toFloat() / totalContentFiltered
        } else 0f
    }
    
    /**
     * Check if session is currently active
     */
    fun isActive(): Boolean = endTime == null
    
    /**
     * Get session duration in minutes
     */
    fun getDurationMinutes(): Float {
        val duration = endTime?.let { it - startTime } ?: (System.currentTimeMillis() - startTime)
        return duration / (1000f * 60f)
    }
}

/**
 * Data class for aggregated session statistics
 */
@Serializable
data class SessionStatistics(
    val totalSessions: Int,
    val totalDurationMs: Long,
    val totalContentFiltered: Int,
    val totalTimeSavedMs: Long,
    val averageFilteringEffectiveness: Float,
    val averageUserSatisfaction: Float,
    val mostActiveApp: String,
    val peakUsageHour: Int
)

/**
 * Data class for daily summary statistics
 */
@Entity(tableName = "daily_summaries")
@Serializable
data class DailySummary(
    @PrimaryKey
    val date: String, // YYYY-MM-DD format
    
    val totalSessions: Int,
    val totalDurationMs: Long,
    val totalContentFiltered: Int,
    val totalTimeSavedMs: Long,
    
    val mostFilteredApp: String,
    val averageConfidence: Float,
    val userFeedbackCount: Int,
    val filterAccuracy: Float,
    
    val createdAt: Long = System.currentTimeMillis()
) {
    
    /**
     * Get total duration in hours
     */
    fun getDurationHours(): Float {
        return totalDurationMs / (1000f * 60f * 60f)
    }
    
    /**
     * Get time saved in minutes
     */
    fun getTimeSavedMinutes(): Float {
        return totalTimeSavedMs / (1000f * 60f)
    }
}