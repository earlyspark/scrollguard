package com.scrollguard.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable

/**
 * Data model representing user preferences and settings.
 * This is stored in the database to persist user configuration.
 */
@Entity(tableName = "user_preferences")
@TypeConverters(PreferencesTypeConverter::class)
@Serializable
data class UserPreferences(
    @PrimaryKey
    val id: String = "default",
    
    // Filtering settings
    val filteringEnabled: Boolean = true,
    val filterStrictness: FilterStrictness = FilterStrictness.MEDIUM,
    val confidenceThreshold: Float = 0.7f,
    
    // Content type preferences
    val filterVideos: Boolean = true,
    val filterImages: Boolean = true,
    val filterText: Boolean = true,
    val filterAds: Boolean = true,
    
    // App-specific settings
    val enabledApps: Set<String> = emptySet(),
    val disabledApps: Set<String> = emptySet(),
    
    // Custom categories
    val customKeywords: Set<String> = emptySet(),
    val whitelistKeywords: Set<String> = emptySet(),
    val blacklistKeywords: Set<String> = emptySet(),
    
    // Learning preferences
    val enableLearning: Boolean = true,
    val adaptToFeedback: Boolean = true,
    
    // Privacy settings
    val analyticsEnabled: Boolean = false,
    val dataRetentionDays: Int = 30,
    
    // UI preferences
    val showFilteredCount: Boolean = true,
    val showConfidenceScore: Boolean = false,
    val overlayOpacity: Float = 0.8f,
    
    // Performance settings
    val maxConcurrentAnalyses: Int = 3,
    val processingDelayMs: Long = 100L,
    val cacheSize: Int = 1000,
    
    // Metadata
    val lastModified: Long = System.currentTimeMillis(),
    val version: Int = 1
)

/**
 * Enum representing different filter strictness levels
 */
@Serializable
enum class FilterStrictness(val threshold: Float, val description: String) {
    LOW(0.5f, "Relaxed filtering - only obvious unproductive content"),
    MEDIUM(0.7f, "Balanced filtering - moderate content screening"),
    HIGH(0.9f, "Strict filtering - aggressive content blocking"),
    CUSTOM(0.0f, "User-defined threshold");
    
    companion object {
        fun fromThreshold(threshold: Float): FilterStrictness {
            return when {
                threshold <= 0.5f -> LOW
                threshold <= 0.7f -> MEDIUM
                threshold <= 0.9f -> HIGH
                else -> CUSTOM
            }
        }
    }
}

/**
 * Data class for notification preferences
 */
@Serializable
data class NotificationPreferences(
    val enabled: Boolean = true,
    val showFilterCount: Boolean = true,
    val showTimeSaved: Boolean = true,
    val quietHours: TimeRange? = null,
    val minimumIntervalMinutes: Int = 60
)

/**
 * Data class for time range (used for quiet hours, etc.)
 */
@Serializable
data class TimeRange(
    val startHour: Int, // 0-23
    val startMinute: Int, // 0-59
    val endHour: Int, // 0-23
    val endMinute: Int // 0-59
) {
    fun isCurrentTimeInRange(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        
        val currentTimeMinutes = currentHour * 60 + currentMinute
        val startTimeMinutes = startHour * 60 + startMinute
        val endTimeMinutes = endHour * 60 + endMinute
        
        return if (startTimeMinutes <= endTimeMinutes) {
            // Same day range
            currentTimeMinutes in startTimeMinutes..endTimeMinutes
        } else {
            // Overnight range
            currentTimeMinutes >= startTimeMinutes || currentTimeMinutes <= endTimeMinutes
        }
    }
}

/**
 * Type converter for Room database to handle complex types
 */
class PreferencesTypeConverter {
    
    @TypeConverter
    fun fromFilterStrictness(strictness: FilterStrictness): String {
        return strictness.name
    }
    
    @TypeConverter
    fun toFilterStrictness(strictness: String): FilterStrictness {
        return try {
            FilterStrictness.valueOf(strictness)
        } catch (e: IllegalArgumentException) {
            FilterStrictness.MEDIUM
        }
    }
    
    @TypeConverter
    fun fromStringSet(value: Set<String>): String {
        return value.joinToString(",")
    }
    
    @TypeConverter
    fun toStringSet(value: String): Set<String> {
        return if (value.isEmpty()) {
            emptySet()
        } else {
            value.split(",").toSet()
        }
    }
    
    @TypeConverter
    fun fromNotificationPreferences(prefs: NotificationPreferences?): String? {
        return prefs?.let { 
            "${it.enabled}|${it.showFilterCount}|${it.showTimeSaved}|${it.minimumIntervalMinutes}|${it.quietHours?.let { range -> "${range.startHour},${range.startMinute},${range.endHour},${range.endMinute}" } ?: ""}"
        }
    }
    
    @TypeConverter
    fun toNotificationPreferences(prefs: String?): NotificationPreferences? {
        return prefs?.let { prefsStr ->
            val parts = prefsStr.split("|")
            if (parts.size >= 4) {
                val quietHours = if (parts.size > 4 && parts[4].isNotEmpty()) {
                    val timeParts = parts[4].split(",")
                    if (timeParts.size == 4) {
                        TimeRange(
                            startHour = timeParts[0].toIntOrNull() ?: 0,
                            startMinute = timeParts[1].toIntOrNull() ?: 0,
                            endHour = timeParts[2].toIntOrNull() ?: 0,
                            endMinute = timeParts[3].toIntOrNull() ?: 0
                        )
                    } else null
                } else null
                
                NotificationPreferences(
                    enabled = parts[0].toBooleanStrictOrNull() ?: true,
                    showFilterCount = parts[1].toBooleanStrictOrNull() ?: true,
                    showTimeSaved = parts[2].toBooleanStrictOrNull() ?: true,
                    minimumIntervalMinutes = parts[3].toIntOrNull() ?: 60,
                    quietHours = quietHours
                )
            } else null
        }
    }
}