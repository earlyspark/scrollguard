package com.scrollguard.app.data.repository

import com.scrollguard.app.data.dao.ContentDao
import com.scrollguard.app.data.dao.SessionDao
import com.scrollguard.app.data.model.ContentAnalysis
import com.scrollguard.app.data.model.ContentType
import com.scrollguard.app.data.model.FilterSession
import com.scrollguard.app.data.model.UserFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

/**
 * Repository for managing content analysis data.
 * Provides a clean interface for content-related database operations.
 */
class ContentRepository(
    private val contentDao: ContentDao,
    private val sessionDao: SessionDao
) {

    /**
     * Save a content analysis result
     */
    suspend fun saveContentAnalysis(analysis: ContentAnalysis): Long = withContext(Dispatchers.IO) {
        try {
            contentDao.insertContentAnalysis(analysis)
        } catch (e: Exception) {
            Timber.e(e, "Error saving content analysis")
            -1L
        }
    }

    /**
     * Get content analysis by hash (for cache lookup)
     */
    suspend fun getContentAnalysisByHash(contentHash: String): ContentAnalysis? = withContext(Dispatchers.IO) {
        try {
            contentDao.getContentAnalysisByHash(contentHash)
        } catch (e: Exception) {
            Timber.e(e, "Error getting content analysis by hash")
            null
        }
    }

    /**
     * Get content analyses for a specific app
     */
    fun getContentAnalysesForApp(packageName: String): Flow<List<ContentAnalysis>> {
        return contentDao.getContentAnalysesByPackage(packageName)
    }

    /**
     * Get recent content analyses
     */
    fun getRecentContentAnalyses(since: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000): Flow<List<ContentAnalysis>> {
        return contentDao.getRecentContentAnalyses(since)
    }

    /**
     * Get content analyses with user feedback
     */
    fun getContentAnalysesWithFeedback(): Flow<List<ContentAnalysis>> {
        return contentDao.getContentAnalysesWithFeedback()
    }

    /**
     * Update user feedback for content analysis
     */
    suspend fun updateUserFeedback(analysisId: Long, feedback: UserFeedback) = withContext(Dispatchers.IO) {
        try {
            val feedbackString = "${feedback.feedbackType.name}|${feedback.timestamp}|${feedback.comment ?: ""}"
            contentDao.updateUserFeedback(analysisId, feedbackString)
        } catch (e: Exception) {
            Timber.e(e, "Error updating user feedback")
        }
    }

    /**
     * Mark content as overridden by user
     */
    suspend fun markContentAsOverridden(analysisId: Long) = withContext(Dispatchers.IO) {
        try {
            contentDao.updateUserOverride(analysisId, true)
        } catch (e: Exception) {
            Timber.e(e, "Error marking content as overridden")
        }
    }

    /**
     * Get content statistics for a time period
     */
    suspend fun getContentStatistics(since: Long = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000): ContentStatistics = withContext(Dispatchers.IO) {
        try {
            val totalCount = contentDao.getTotalContentCount()
            val filteredCount = contentDao.getFilteredContentCount()
            val averageConfidence = contentDao.getAverageConfidence(since) ?: 0.0f
            val averageProcessingTime = contentDao.getAverageProcessingTime(since) ?: 0.0f
            val statsByPackage = contentDao.getContentStatsByPackage(since)
            val typeDistribution = contentDao.getContentTypeDistribution(since)

            ContentStatistics(
                totalContentAnalyzed = totalCount,
                contentFiltered = filteredCount,
                averageConfidence = averageConfidence,
                averageProcessingTimeMs = averageProcessingTime,
                statsByPackage = statsByPackage,
                contentTypeDistribution = typeDistribution
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting content statistics")
            ContentStatistics()
        }
    }

    /**
     * Get daily content statistics
     */
    suspend fun getDailyStatistics(days: Int = 7) = withContext(Dispatchers.IO) {
        try {
            val since = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            contentDao.getDailyContentStats(since)
        } catch (e: Exception) {
            Timber.e(e, "Error getting daily statistics")
            emptyList()
        }
    }

    /**
     * Search content analyses by reason/category
     */
    suspend fun searchContentAnalyses(searchTerm: String, limit: Int = 50) = withContext(Dispatchers.IO) {
        try {
            contentDao.searchContentAnalysesByReason(searchTerm, limit)
        } catch (e: Exception) {
            Timber.e(e, "Error searching content analyses")
            emptyList()
        }
    }

    /**
     * Clean up old content analyses based on retention policy
     */
    suspend fun cleanupOldAnalyses(retentionDays: Int = 30): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000L
            contentDao.deleteOldContentAnalyses(cutoffTime)
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old analyses")
            0
        }
    }

    /**
     * Export content analyses for backup
     */
    suspend fun exportContentAnalyses(startTime: Long, endTime: Long) = withContext(Dispatchers.IO) {
        try {
            contentDao.getContentAnalysesForExport(startTime, endTime)
        } catch (e: Exception) {
            Timber.e(e, "Error exporting content analyses")
            emptyList()
        }
    }

    /**
     * Delete content analyses for a specific app
     */
    suspend fun deleteContentForApp(packageName: String): Int = withContext(Dispatchers.IO) {
        try {
            contentDao.deleteContentAnalysesByPackage(packageName)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting content for app")
            0
        }
    }

    // Session management methods

    /**
     * Start a new filtering session
     */
    suspend fun startFilteringSession(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val sessionId = "session_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val session = FilterSession(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                packageName = packageName,
                deviceModel = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.SDK_INT,
                appVersionCode = 1 // Would be retrieved from BuildConfig
            )
            
            sessionDao.insertSession(session)
            sessionId
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting filtering session")
            ""
        }
    }

    /**
     * End a filtering session
     */
    suspend fun endFilteringSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val endTime = System.currentTimeMillis()
            val session = sessionDao.getSessionById(sessionId)
            
            if (session != null) {
                val duration = endTime - session.startTime
                sessionDao.endSession(sessionId, endTime, duration)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error ending filtering session")
        }
    }

    /**
     * Update session statistics
     */
    suspend fun updateSessionStats(
        sessionId: String,
        contentSeen: Int,
        contentFiltered: Int,
        avgProcessingTime: Float,
        maxProcessingTime: Int,
        totalProcessingTime: Long,
        feedbackCount: Int,
        falsePositives: Int,
        falseNegatives: Int,
        manualOverrides: Int,
        timeSaved: Long
    ) = withContext(Dispatchers.IO) {
        try {
            sessionDao.updateSessionStats(
                sessionId = sessionId,
                contentSeen = contentSeen,
                contentFiltered = contentFiltered,
                avgProcessingTime = avgProcessingTime,
                maxProcessingTime = maxProcessingTime,
                totalProcessingTime = totalProcessingTime,
                feedbackCount = feedbackCount,
                falsePositives = falsePositives,
                falseNegatives = falseNegatives,
                manualOverrides = manualOverrides,
                timeSaved = timeSaved
            )
        } catch (e: Exception) {
            Timber.e(e, "Error updating session stats")
        }
    }

    /**
     * Get session statistics for a time period
     */
    suspend fun getSessionStatistics(startTime: Long, endTime: Long) = withContext(Dispatchers.IO) {
        try {
            sessionDao.getSessionStatistics(startTime, endTime)
        } catch (e: Exception) {
            Timber.e(e, "Error getting session statistics")
            null
        }
    }

    /**
     * Get recent sessions
     */
    fun getRecentSessions(since: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000): Flow<List<FilterSession>> {
        return sessionDao.getRecentSessions(since)
    }

    /**
     * Clean up old sessions
     */
    suspend fun cleanupOldSessions(retentionDays: Int = 30): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000L
            sessionDao.deleteOldSessions(cutoffTime)
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old sessions")
            0
        }
    }
}

/**
 * Data class for content statistics summary
 */
data class ContentStatistics(
    val totalContentAnalyzed: Int = 0,
    val contentFiltered: Int = 0,
    val averageConfidence: Float = 0.0f,
    val averageProcessingTimeMs: Float = 0.0f,
    val statsByPackage: List<com.scrollguard.app.data.dao.ContentStatsResult> = emptyList(),
    val contentTypeDistribution: List<com.scrollguard.app.data.dao.ContentTypeCount> = emptyList()
) {
    val filteringEffectiveness: Float
        get() = if (totalContentAnalyzed > 0) {
            (contentFiltered.toFloat() / totalContentAnalyzed) * 100f
        } else 0f
        
    val timeSavedEstimate: Long
        get() = contentFiltered * 30L * 1000L // Estimate 30 seconds saved per filtered item
}