package com.scrollguard.app.data.dao

import androidx.room.*
import com.scrollguard.app.data.model.ContentAnalysis
import com.scrollguard.app.data.model.ContentType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ContentAnalysis entities.
 * Provides methods to interact with content analysis data in the database.
 */
@Dao
interface ContentDao {

    /**
     * Insert a new content analysis
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentAnalysis(analysis: ContentAnalysis): Long

    /**
     * Insert multiple content analyses
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentAnalyses(analyses: List<ContentAnalysis>)

    /**
     * Update an existing content analysis
     */
    @Update
    suspend fun updateContentAnalysis(analysis: ContentAnalysis)

    /**
     * Delete a content analysis
     */
    @Delete
    suspend fun deleteContentAnalysis(analysis: ContentAnalysis)

    /**
     * Get content analysis by ID
     */
    @Query("SELECT * FROM content_analysis WHERE id = :id")
    suspend fun getContentAnalysisById(id: Long): ContentAnalysis?

    /**
     * Get content analysis by content hash
     */
    @Query("SELECT * FROM content_analysis WHERE contentHash = :contentHash")
    suspend fun getContentAnalysisByHash(contentHash: String): ContentAnalysis?

    /**
     * Get all content analyses for a specific package
     */
    @Query("SELECT * FROM content_analysis WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getContentAnalysesByPackage(packageName: String): Flow<List<ContentAnalysis>>

    /**
     * Get content analyses by productivity status
     */
    @Query("SELECT * FROM content_analysis WHERE isProductive = :isProductive ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getContentAnalysesByProductivity(isProductive: Boolean, limit: Int = 100): List<ContentAnalysis>

    /**
     * Get content analyses within a time range
     */
    @Query("SELECT * FROM content_analysis WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getContentAnalysesInTimeRange(startTime: Long, endTime: Long): List<ContentAnalysis>

    /**
     * Get content analyses by content type
     */
    @Query("SELECT * FROM content_analysis WHERE contentType = :contentType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getContentAnalysesByType(contentType: ContentType, limit: Int = 100): List<ContentAnalysis>

    /**
     * Get content analyses with user feedback
     */
    @Query("SELECT * FROM content_analysis WHERE userFeedback IS NOT NULL ORDER BY timestamp DESC")
    fun getContentAnalysesWithFeedback(): Flow<List<ContentAnalysis>>

    /**
     * Get content analyses with user overrides
     */
    @Query("SELECT * FROM content_analysis WHERE userOverride = 1 ORDER BY timestamp DESC")
    fun getContentAnalysesWithOverrides(): Flow<List<ContentAnalysis>>

    /**
     * Get recent content analyses (last 24 hours)
     */
    @Query("SELECT * FROM content_analysis WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getRecentContentAnalyses(since: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000): Flow<List<ContentAnalysis>>

    /**
     * Count total content analyses
     */
    @Query("SELECT COUNT(*) FROM content_analysis")
    suspend fun getTotalContentCount(): Int

    /**
     * Count filtered content (unproductive)
     */
    @Query("SELECT COUNT(*) FROM content_analysis WHERE isProductive = 0")
    suspend fun getFilteredContentCount(): Int

    /**
     * Count content by package
     */
    @Query("SELECT COUNT(*) FROM content_analysis WHERE packageName = :packageName")
    suspend fun getContentCountByPackage(packageName: String): Int

    /**
     * Get average confidence score
     */
    @Query("SELECT AVG(confidence) FROM content_analysis WHERE timestamp > :since")
    suspend fun getAverageConfidence(since: Long): Float?

    /**
     * Get average processing time
     */
    @Query("SELECT AVG(processingTimeMs) FROM content_analysis WHERE timestamp > :since")
    suspend fun getAverageProcessingTime(since: Long): Float?

    /**
     * Get content statistics by package
     */
    @Query("""
        SELECT packageName, 
               COUNT(*) as total,
               SUM(CASE WHEN isProductive = 0 THEN 1 ELSE 0 END) as filtered,
               AVG(confidence) as avgConfidence,
               AVG(processingTimeMs) as avgProcessingTime
        FROM content_analysis 
        WHERE timestamp > :since 
        GROUP BY packageName
    """)
    suspend fun getContentStatsByPackage(since: Long): List<ContentStatsResult>

    /**
     * Get daily content statistics
     */
    @Query("""
        SELECT DATE(timestamp/1000, 'unixepoch') as date,
               COUNT(*) as total,
               SUM(CASE WHEN isProductive = 0 THEN 1 ELSE 0 END) as filtered,
               AVG(confidence) as avgConfidence
        FROM content_analysis 
        WHERE timestamp > :since
        GROUP BY DATE(timestamp/1000, 'unixepoch')
        ORDER BY date DESC
    """)
    suspend fun getDailyContentStats(since: Long): List<DailyContentStats>

    /**
     * Get content type distribution
     */
    @Query("""
        SELECT contentType, COUNT(*) as count
        FROM content_analysis 
        WHERE timestamp > :since
        GROUP BY contentType
    """)
    suspend fun getContentTypeDistribution(since: Long): List<ContentTypeCount>

    /**
     * Delete old content analyses (data retention)
     */
    @Query("DELETE FROM content_analysis WHERE timestamp < :cutoffTime")
    suspend fun deleteOldContentAnalyses(cutoffTime: Long): Int

    /**
     * Delete content analyses for a specific package
     */
    @Query("DELETE FROM content_analysis WHERE packageName = :packageName")
    suspend fun deleteContentAnalysesByPackage(packageName: String): Int

    /**
     * Get content analyses for export (without sensitive data)
     */
    @Query("""
        SELECT id, contentHash, content, contentType, packageName, isProductive, 
               confidence, reason, processingTimeMs, timestamp, modelVersion,
               userFeedback, userOverride, sessionId
        FROM content_analysis 
        WHERE timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp ASC
    """)
    suspend fun getContentAnalysesForExport(startTime: Long, endTime: Long): List<ContentAnalysis>

    /**
     * Update user feedback for content analysis
     */
    @Query("UPDATE content_analysis SET userFeedback = :feedback WHERE id = :id")
    suspend fun updateUserFeedback(id: Long, feedback: String?)

    /**
     * Update user override status
     */
    @Query("UPDATE content_analysis SET userOverride = :override WHERE id = :id")
    suspend fun updateUserOverride(id: Long, override: Boolean)

    /**
     * Search content analyses by reason
     */
    @Query("SELECT * FROM content_analysis WHERE reason LIKE '%' || :searchTerm || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchContentAnalysesByReason(searchTerm: String, limit: Int = 50): List<ContentAnalysis>
}

/**
 * Data class for content statistics results
 */
data class ContentStatsResult(
    val packageName: String,
    val total: Int,
    val filtered: Int,
    val avgConfidence: Float,
    val avgProcessingTime: Float
)

/**
 * Data class for daily content statistics
 */
data class DailyContentStats(
    val date: String,
    val total: Int,
    val filtered: Int,
    val avgConfidence: Float
)

/**
 * Data class for content type distribution
 */
data class ContentTypeCount(
    val contentType: ContentType,
    val count: Int
)