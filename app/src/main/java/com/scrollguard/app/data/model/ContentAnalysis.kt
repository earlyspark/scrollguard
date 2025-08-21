package com.scrollguard.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable

/**
 * Data model representing the analysis result of a piece of content.
 * This is stored in the database to track filtering decisions and user feedback.
 */
@Entity(tableName = "content_analysis")
@TypeConverters(ContentTypeConverter::class)
@Serializable
data class ContentAnalysis(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val contentHash: String = "",
    val content: String = "", // Encrypted or hashed for privacy
    val contentType: ContentType,
    val packageName: String = "",
    
    // Analysis results
    val isProductive: Boolean,
    val confidence: Float,
    val reason: String,
    val processingTimeMs: Int,
    
    // Metadata
    val timestamp: Long,
    val modelVersion: String = "1.0",
    
    // User feedback
    val userFeedback: UserFeedback? = null,
    val userOverride: Boolean = false,
    
    // Analytics
    val sessionId: String = ""
)

/**
 * Enum representing different types of content
 */
@Serializable
enum class ContentType {
    TEXT_POST,
    VIDEO_POST,
    IMAGE_POST,
    COMMENT,
    STORY,
    ADVERTISEMENT,
    LINK_PREVIEW,
    NOTIFICATION,
    UNKNOWN;
    
    companion object {
        fun fromPackageName(packageName: String): ContentType {
            return when {
                packageName.contains("instagram") -> IMAGE_POST
                packageName.contains("tiktok") -> VIDEO_POST
                packageName.contains("twitter") -> TEXT_POST
                packageName.contains("reddit") -> TEXT_POST
                packageName.contains("youtube") -> VIDEO_POST
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Data class representing user feedback on content filtering decisions
 */
@Serializable
data class UserFeedback(
    val feedbackType: FeedbackType,
    val timestamp: Long,
    val comment: String? = null
)


/**
 * Type converter for Room database to handle enum and complex types
 */
class ContentTypeConverter {
    
    @TypeConverter
    fun fromContentType(contentType: ContentType): String {
        return contentType.name
    }
    
    @TypeConverter
    fun toContentType(contentType: String): ContentType {
        return try {
            ContentType.valueOf(contentType)
        } catch (e: IllegalArgumentException) {
            ContentType.UNKNOWN
        }
    }
    
    @TypeConverter
    fun fromUserFeedback(userFeedback: UserFeedback?): String? {
        return userFeedback?.let { 
            "${it.feedbackType.name}|${it.timestamp}|${it.comment ?: ""}"
        }
    }
    
    @TypeConverter
    fun toUserFeedback(userFeedback: String?): UserFeedback? {
        return userFeedback?.let { feedback ->
            val parts = feedback.split("|", limit = 3)
            if (parts.size >= 2) {
                try {
                    UserFeedback(
                        feedbackType = FeedbackType.valueOf(parts[0]),
                        timestamp = parts[1].toLongOrNull() ?: 0L,
                        comment = if (parts.size > 2 && parts[2].isNotEmpty()) parts[2] else null
                    )
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else null
        }
    }
    
    @TypeConverter
    fun fromContentTypeMap(map: Map<ContentType, Int>): String {
        return map.entries.joinToString(",") { "${it.key.name}:${it.value}" }
    }
    
    @TypeConverter
    fun toContentTypeMap(mapString: String): Map<ContentType, Int> {
        return if (mapString.isEmpty()) {
            emptyMap()
        } else {
            mapString.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    try {
                        ContentType.valueOf(parts[0]) to parts[1].toInt()
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }.toMap()
        }
    }
}