package com.scrollguard.app.data.dao

import androidx.room.*
import com.scrollguard.app.data.model.UserPreferences
import com.scrollguard.app.data.model.FilterStrictness
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserPreferences entities.
 * Provides methods to interact with user preference data in the database.
 */
@Dao
interface PreferencesDao {

    /**
     * Insert or update user preferences
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(preferences: UserPreferences)

    /**
     * Update existing preferences
     */
    @Update
    suspend fun updatePreferences(preferences: UserPreferences)

    /**
     * Get user preferences by ID
     */
    @Query("SELECT * FROM user_preferences WHERE id = :id")
    suspend fun getPreferencesById(id: String): UserPreferences?

    /**
     * Get default user preferences
     */
    @Query("SELECT * FROM user_preferences WHERE id = 'default'")
    suspend fun getDefaultPreferences(): UserPreferences?

    /**
     * Get user preferences as Flow for reactive updates
     */
    @Query("SELECT * FROM user_preferences WHERE id = :id")
    fun getPreferencesFlow(id: String = "default"): Flow<UserPreferences?>

    /**
     * Check if filtering is enabled
     */
    @Query("SELECT filteringEnabled FROM user_preferences WHERE id = 'default'")
    suspend fun isFilteringEnabled(): Boolean?

    /**
     * Update filtering enabled status
     */
    @Query("UPDATE user_preferences SET filteringEnabled = :enabled, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateFilteringEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Get filter strictness level
     */
    @Query("SELECT filterStrictness FROM user_preferences WHERE id = 'default'")
    suspend fun getFilterStrictness(): FilterStrictness?

    /**
     * Update filter strictness
     */
    @Query("UPDATE user_preferences SET filterStrictness = :strictness, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateFilterStrictness(strictness: FilterStrictness, timestamp: Long = System.currentTimeMillis())

    /**
     * Get confidence threshold
     */
    @Query("SELECT confidenceThreshold FROM user_preferences WHERE id = 'default'")
    suspend fun getConfidenceThreshold(): Float?

    /**
     * Update confidence threshold
     */
    @Query("UPDATE user_preferences SET confidenceThreshold = :threshold, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateConfidenceThreshold(threshold: Float, timestamp: Long = System.currentTimeMillis())

    /**
     * Get enabled apps
     */
    @Query("SELECT enabledApps FROM user_preferences WHERE id = 'default'")
    suspend fun getEnabledApps(): String?

    /**
     * Update enabled apps
     */
    @Query("UPDATE user_preferences SET enabledApps = :apps, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateEnabledApps(apps: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get disabled apps
     */
    @Query("SELECT disabledApps FROM user_preferences WHERE id = 'default'")
    suspend fun getDisabledApps(): String?

    /**
     * Update disabled apps
     */
    @Query("UPDATE user_preferences SET disabledApps = :apps, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateDisabledApps(apps: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Check if analytics is enabled
     */
    @Query("SELECT analyticsEnabled FROM user_preferences WHERE id = 'default'")
    suspend fun isAnalyticsEnabled(): Boolean?

    /**
     * Update analytics enabled status
     */
    @Query("UPDATE user_preferences SET analyticsEnabled = :enabled, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateAnalyticsEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Get custom keywords
     */
    @Query("SELECT customKeywords FROM user_preferences WHERE id = 'default'")
    suspend fun getCustomKeywords(): String?

    /**
     * Update custom keywords
     */
    @Query("UPDATE user_preferences SET customKeywords = :keywords, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateCustomKeywords(keywords: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get whitelist keywords
     */
    @Query("SELECT whitelistKeywords FROM user_preferences WHERE id = 'default'")
    suspend fun getWhitelistKeywords(): String?

    /**
     * Update whitelist keywords
     */
    @Query("UPDATE user_preferences SET whitelistKeywords = :keywords, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateWhitelistKeywords(keywords: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get blacklist keywords
     */
    @Query("SELECT blacklistKeywords FROM user_preferences WHERE id = 'default'")
    suspend fun getBlacklistKeywords(): String?

    /**
     * Update blacklist keywords
     */
    @Query("UPDATE user_preferences SET blacklistKeywords = :keywords, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateBlacklistKeywords(keywords: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Check if learning is enabled
     */
    @Query("SELECT enableLearning FROM user_preferences WHERE id = 'default'")
    suspend fun isLearningEnabled(): Boolean?

    /**
     * Update learning enabled status
     */
    @Query("UPDATE user_preferences SET enableLearning = :enabled, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateLearningEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Get overlay opacity
     */
    @Query("SELECT overlayOpacity FROM user_preferences WHERE id = 'default'")
    suspend fun getOverlayOpacity(): Float?

    /**
     * Update overlay opacity
     */
    @Query("UPDATE user_preferences SET overlayOpacity = :opacity, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateOverlayOpacity(opacity: Float, timestamp: Long = System.currentTimeMillis())

    /**
     * Get data retention days
     */
    @Query("SELECT dataRetentionDays FROM user_preferences WHERE id = 'default'")
    suspend fun getDataRetentionDays(): Int?

    /**
     * Update data retention days
     */
    @Query("UPDATE user_preferences SET dataRetentionDays = :days, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updateDataRetentionDays(days: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Update content type filtering preferences
     */
    @Query("""
        UPDATE user_preferences 
        SET filterVideos = :videos, 
            filterImages = :images, 
            filterText = :text, 
            filterAds = :ads,
            lastModified = :timestamp 
        WHERE id = 'default'
    """)
    suspend fun updateContentTypeFiltering(
        videos: Boolean,
        images: Boolean,
        text: Boolean,
        ads: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Update performance settings
     */
    @Query("""
        UPDATE user_preferences 
        SET maxConcurrentAnalyses = :maxAnalyses,
            processingDelayMs = :delayMs,
            cacheSize = :cacheSize,
            lastModified = :timestamp
        WHERE id = 'default'
    """)
    suspend fun updatePerformanceSettings(
        maxAnalyses: Int,
        delayMs: Long,
        cacheSize: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Update UI preferences
     */
    @Query("""
        UPDATE user_preferences 
        SET showFilteredCount = :showCount,
            showConfidenceScore = :showConfidence,
            overlayOpacity = :opacity,
            lastModified = :timestamp
        WHERE id = 'default'
    """)
    suspend fun updateUIPreferences(
        showCount: Boolean,
        showConfidence: Boolean,
        opacity: Float,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Reset preferences to default values
     */
    @Query("DELETE FROM user_preferences WHERE id = 'default'")
    suspend fun resetToDefaults()

    /**
     * Get last modified timestamp
     */
    @Query("SELECT lastModified FROM user_preferences WHERE id = 'default'")
    suspend fun getLastModified(): Long?

    /**
     * Export preferences (for backup/restore)
     */
    @Query("SELECT * FROM user_preferences")
    suspend fun exportPreferences(): List<UserPreferences>

    /**
     * Delete all preferences
     */
    @Query("DELETE FROM user_preferences")
    suspend fun deleteAllPreferences()

    /**
     * Get preferences version
     */
    @Query("SELECT version FROM user_preferences WHERE id = 'default'")
    suspend fun getPreferencesVersion(): Int?

    /**
     * Update preferences version
     */
    @Query("UPDATE user_preferences SET version = :version, lastModified = :timestamp WHERE id = 'default'")
    suspend fun updatePreferencesVersion(version: Int, timestamp: Long = System.currentTimeMillis())
}