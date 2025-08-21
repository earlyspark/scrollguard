package com.scrollguard.app.data.dao

import androidx.room.*
import com.scrollguard.app.data.model.FilterSession
import com.scrollguard.app.data.model.DailySummary
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FilterSession and DailySummary entities.
 * Provides methods to interact with session and summary data in the database.
 */
@Dao
interface SessionDao {

    // FilterSession operations

    /**
     * Insert a new filter session
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FilterSession)

    /**
     * Update an existing session
     */
    @Update
    suspend fun updateSession(session: FilterSession)

    /**
     * Delete a session
     */
    @Delete
    suspend fun deleteSession(session: FilterSession)

    /**
     * Get session by ID
     */
    @Query("SELECT * FROM filter_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): FilterSession?

    /**
     * Get all sessions for a specific package
     */
    @Query("SELECT * FROM filter_sessions WHERE packageName = :packageName ORDER BY startTime DESC")
    fun getSessionsByPackage(packageName: String): Flow<List<FilterSession>>

    /**
     * Get active sessions (no end time)
     */
    @Query("SELECT * FROM filter_sessions WHERE endTime IS NULL ORDER BY startTime DESC")
    suspend fun getActiveSessions(): List<FilterSession>

    /**
     * Get recent sessions (last 24 hours)
     */
    @Query("SELECT * FROM filter_sessions WHERE startTime > :since ORDER BY startTime DESC")
    fun getRecentSessions(since: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000): Flow<List<FilterSession>>

    /**
     * Get sessions within a time range
     */
    @Query("SELECT * FROM filter_sessions WHERE startTime BETWEEN :startTime AND :endTime ORDER BY startTime DESC")
    suspend fun getSessionsInTimeRange(startTime: Long, endTime: Long): List<FilterSession>

    /**
     * End an active session
     */
    @Query("UPDATE filter_sessions SET endTime = :endTime, durationMs = :durationMs WHERE sessionId = :sessionId")
    suspend fun endSession(sessionId: String, endTime: Long, durationMs: Long)

    /**
     * Update session statistics
     */
    @Query("""
        UPDATE filter_sessions 
        SET totalContentSeen = :contentSeen,
            totalContentFiltered = :contentFiltered,
            averageProcessingTimeMs = :avgProcessingTime,
            maxProcessingTimeMs = :maxProcessingTime,
            totalProcessingTimeMs = :totalProcessingTime,
            userFeedbackCount = :feedbackCount,
            falsePositiveCount = :falsePositives,
            falseNegativeCount = :falseNegatives,
            manualOverrideCount = :manualOverrides,
            estimatedTimeSavedMs = :timeSaved
        WHERE sessionId = :sessionId
    """)
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
    )

    /**
     * Update session performance metrics
     */
    @Query("""
        UPDATE filter_sessions 
        SET memoryUsageMB = :memoryUsage,
            batteryDrainPercent = :batteryDrain,
            errorCount = :errorCount
        WHERE sessionId = :sessionId
    """)
    suspend fun updateSessionPerformance(
        sessionId: String,
        memoryUsage: Float,
        batteryDrain: Float,
        errorCount: Int
    )

    /**
     * Get total session count
     */
    @Query("SELECT COUNT(*) FROM filter_sessions")
    suspend fun getTotalSessionCount(): Int

    /**
     * Get session statistics for a time period
     */
    @Query("""
        SELECT 
            COUNT(*) as totalSessions,
            SUM(durationMs) as totalDurationMs,
            SUM(totalContentFiltered) as totalFiltered,
            SUM(estimatedTimeSavedMs) as totalTimeSaved,
            AVG(CAST(totalContentFiltered AS FLOAT) / NULLIF(totalContentSeen, 0) * 100) as avgEffectiveness,
            AVG(userSatisfactionScore) as avgSatisfaction
        FROM filter_sessions 
        WHERE startTime BETWEEN :startTime AND :endTime AND endTime IS NOT NULL
    """)
    suspend fun getSessionStatistics(startTime: Long, endTime: Long): SessionStatsResult?

    /**
     * Get most active app in time period
     */
    @Query("""
        SELECT packageName, COUNT(*) as sessionCount
        FROM filter_sessions 
        WHERE startTime BETWEEN :startTime AND :endTime
        GROUP BY packageName
        ORDER BY sessionCount DESC
        LIMIT 1
    """)
    suspend fun getMostActiveApp(startTime: Long, endTime: Long): AppSessionCount?

    /**
     * Get session distribution by hour
     */
    @Query("""
        SELECT 
            strftime('%H', datetime(startTime/1000, 'unixepoch')) as hour,
            COUNT(*) as sessionCount
        FROM filter_sessions 
        WHERE startTime BETWEEN :startTime AND :endTime
        GROUP BY hour
        ORDER BY sessionCount DESC
        LIMIT 1
    """)
    suspend fun getPeakUsageHour(startTime: Long, endTime: Long): HourSessionCount?

    /**
     * Delete old sessions (data retention)
     */
    @Query("DELETE FROM filter_sessions WHERE startTime < :cutoffTime")
    suspend fun deleteOldSessions(cutoffTime: Long): Int

    // DailySummary operations

    /**
     * Insert or update daily summary
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummary(summary: DailySummary)

    /**
     * Get daily summary by date
     */
    @Query("SELECT * FROM daily_summaries WHERE date = :date")
    suspend fun getDailySummary(date: String): DailySummary?

    /**
     * Get daily summaries in date range
     */
    @Query("SELECT * FROM daily_summaries WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getDailySummariesInRange(startDate: String, endDate: String): List<DailySummary>

    /**
     * Get recent daily summaries
     */
    @Query("SELECT * FROM daily_summaries ORDER BY date DESC LIMIT :limit")
    fun getRecentDailySummaries(limit: Int = 30): Flow<List<DailySummary>>

    /**
     * Update daily summary statistics
     */
    @Query("""
        UPDATE daily_summaries 
        SET totalSessions = :sessions,
            totalDurationMs = :duration,
            totalContentFiltered = :filtered,
            totalTimeSavedMs = :timeSaved,
            mostFilteredApp = :mostFilteredApp,
            averageConfidence = :avgConfidence,
            userFeedbackCount = :feedbackCount,
            filterAccuracy = :accuracy
        WHERE date = :date
    """)
    suspend fun updateDailySummary(
        date: String,
        sessions: Int,
        duration: Long,
        filtered: Int,
        timeSaved: Long,
        mostFilteredApp: String,
        avgConfidence: Float,
        feedbackCount: Int,
        accuracy: Float
    )

    /**
     * Get weekly summary statistics
     */
    @Query("""
        SELECT 
            SUM(totalSessions) as totalSessions,
            SUM(totalDurationMs) as totalDurationMs,
            SUM(totalContentFiltered) as totalContentFiltered,
            SUM(totalTimeSavedMs) as totalTimeSavedMs,
            AVG(averageConfidence) as avgConfidence,
            AVG(filterAccuracy) as avgAccuracy
        FROM daily_summaries 
        WHERE date BETWEEN :startDate AND :endDate
    """)
    suspend fun getWeeklySummary(startDate: String, endDate: String): WeeklySummaryResult?

    /**
     * Get monthly summary statistics
     */
    @Query("""
        SELECT 
            SUM(totalSessions) as totalSessions,
            SUM(totalDurationMs) as totalDurationMs,
            SUM(totalContentFiltered) as totalContentFiltered,
            SUM(totalTimeSavedMs) as totalTimeSavedMs,
            AVG(averageConfidence) as avgConfidence,
            AVG(filterAccuracy) as avgAccuracy
        FROM daily_summaries 
        WHERE date LIKE :monthPrefix || '%'
    """)
    suspend fun getMonthlySummary(monthPrefix: String): WeeklySummaryResult? // YYYY-MM format

    /**
     * Delete old daily summaries
     */
    @Query("DELETE FROM daily_summaries WHERE date < :cutoffDate")
    suspend fun deleteOldDailySummaries(cutoffDate: String): Int

    /**
     * Export session data for analytics
     */
    @Query("""
        SELECT sessionId, startTime, endTime, durationMs, packageName,
               totalContentSeen, totalContentFiltered, averageProcessingTimeMs,
               userFeedbackCount, falsePositiveCount, falseNegativeCount,
               estimatedTimeSavedMs, userSatisfactionScore
        FROM filter_sessions 
        WHERE startTime BETWEEN :startTime AND :endTime
        ORDER BY startTime ASC
    """)
    suspend fun exportSessionData(startTime: Long, endTime: Long): List<SessionExportData>
}

/**
 * Data classes for query results
 */
data class SessionStatsResult(
    val totalSessions: Int,
    val totalDurationMs: Long,
    val totalFiltered: Int,
    val totalTimeSaved: Long,
    val avgEffectiveness: Float,
    val avgSatisfaction: Float
)

data class AppSessionCount(
    val packageName: String,
    val sessionCount: Int
)

data class HourSessionCount(
    val hour: String,
    val sessionCount: Int
)

data class WeeklySummaryResult(
    val totalSessions: Int,
    val totalDurationMs: Long,
    val totalContentFiltered: Int,
    val totalTimeSavedMs: Long,
    val avgConfidence: Float,
    val avgAccuracy: Float
)

data class SessionExportData(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMs: Long,
    val packageName: String,
    val totalContentSeen: Int,
    val totalContentFiltered: Int,
    val averageProcessingTimeMs: Float,
    val userFeedbackCount: Int,
    val falsePositiveCount: Int,
    val falseNegativeCount: Int,
    val estimatedTimeSavedMs: Long,
    val userSatisfactionScore: Float?
)