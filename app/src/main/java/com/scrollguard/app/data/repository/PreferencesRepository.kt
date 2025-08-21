package com.scrollguard.app.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.scrollguard.app.data.dao.PreferencesDao
import com.scrollguard.app.data.model.FilterStrictness
import com.scrollguard.app.data.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Repository for managing user preferences and settings.
 * Provides encrypted storage for sensitive preferences and database storage for app settings.
 */
class PreferencesRepository(
    private val context: Context,
    private val preferencesDao: PreferencesDao
) {

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "scrollguard_secure_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ACCESSIBILITY_PERMISSION_GRANTED = "accessibility_granted"
        private const val KEY_ANALYTICS_CONSENT = "analytics_consent"
        private const val KEY_LAST_MODEL_UPDATE = "last_model_update"
    }

    // Encrypted shared preferences for sensitive data
    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Error creating encrypted preferences, falling back to regular preferences")
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Get user preferences as Flow for reactive updates
     */
    fun getUserPreferences(): Flow<UserPreferences> {
        return preferencesDao.getPreferencesFlow().map { preferences ->
            preferences ?: getDefaultPreferences()
        }
    }

    /**
     * Get user preferences (one-time)
     */
    suspend fun getUserPreferencesOnce(): UserPreferences = withContext(Dispatchers.IO) {
        try {
            preferencesDao.getDefaultPreferences() ?: getDefaultPreferences()
        } catch (e: Exception) {
            Timber.e(e, "Error getting user preferences")
            getDefaultPreferences()
        }
    }

    /**
     * Update user preferences
     */
    suspend fun updateUserPreferences(preferences: UserPreferences) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.insertOrUpdatePreferences(
                preferences.copy(
                    lastModified = System.currentTimeMillis(),
                    version = preferences.version + 1
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error updating user preferences")
        }
    }

    /**
     * Update filtering enabled status
     */
    suspend fun setFilteringEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateFilteringEnabled(enabled)
        } catch (e: Exception) {
            Timber.e(e, "Error updating filtering enabled status")
        }
    }

    /**
     * Check if filtering is enabled
     */
    suspend fun isFilteringEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            preferencesDao.isFilteringEnabled() ?: true
        } catch (e: Exception) {
            Timber.e(e, "Error checking filtering enabled status")
            true
        }
    }

    /**
     * Update filter strictness
     */
    suspend fun setFilterStrictness(strictness: FilterStrictness) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateFilterStrictness(strictness)
        } catch (e: Exception) {
            Timber.e(e, "Error updating filter strictness")
        }
    }

    /**
     * Get filter strictness
     */
    suspend fun getFilterStrictness(): FilterStrictness = withContext(Dispatchers.IO) {
        try {
            preferencesDao.getFilterStrictness() ?: FilterStrictness.MEDIUM
        } catch (e: Exception) {
            Timber.e(e, "Error getting filter strictness")
            FilterStrictness.MEDIUM
        }
    }

    /**
     * Update confidence threshold
     */
    suspend fun setConfidenceThreshold(threshold: Float) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateConfidenceThreshold(threshold.coerceIn(0.0f, 1.0f))
        } catch (e: Exception) {
            Timber.e(e, "Error updating confidence threshold")
        }
    }

    /**
     * Get confidence threshold
     */
    suspend fun getConfidenceThreshold(): Float = withContext(Dispatchers.IO) {
        try {
            preferencesDao.getConfidenceThreshold() ?: 0.7f
        } catch (e: Exception) {
            Timber.e(e, "Error getting confidence threshold")
            0.7f
        }
    }

    /**
     * Update enabled apps
     */
    suspend fun setEnabledApps(apps: Set<String>) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateEnabledApps(apps.joinToString(","))
        } catch (e: Exception) {
            Timber.e(e, "Error updating enabled apps")
        }
    }

    /**
     * Get enabled apps
     */
    suspend fun getEnabledApps(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val appsString = preferencesDao.getEnabledApps()
            if (appsString.isNullOrEmpty()) {
                getDefaultEnabledApps()
            } else {
                appsString.split(",").toSet()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting enabled apps")
            getDefaultEnabledApps()
        }
    }

    /**
     * Update analytics consent
     */
    suspend fun setAnalyticsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateAnalyticsEnabled(enabled)
            // Also store in encrypted preferences for immediate access
            encryptedPrefs.edit().putBoolean(KEY_ANALYTICS_CONSENT, enabled).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error updating analytics enabled status")
        }
    }

    /**
     * Check if analytics is enabled
     */
    suspend fun isAnalyticsEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check encrypted preferences first for immediate access
            if (encryptedPrefs.contains(KEY_ANALYTICS_CONSENT)) {
                return@withContext encryptedPrefs.getBoolean(KEY_ANALYTICS_CONSENT, false)
            }
            preferencesDao.isAnalyticsEnabled() ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error checking analytics enabled status")
            false
        }
    }

    /**
     * Update custom keywords
     */
    suspend fun setCustomKeywords(keywords: Set<String>) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateCustomKeywords(keywords.joinToString(","))
        } catch (e: Exception) {
            Timber.e(e, "Error updating custom keywords")
        }
    }

    /**
     * Get custom keywords
     */
    suspend fun getCustomKeywords(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val keywordsString = preferencesDao.getCustomKeywords()
            if (keywordsString.isNullOrEmpty()) {
                emptySet()
            } else {
                keywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting custom keywords")
            emptySet()
        }
    }

    /**
     * Update UI preferences
     */
    suspend fun updateUIPreferences(
        showFilteredCount: Boolean,
        showConfidenceScore: Boolean,
        overlayOpacity: Float
    ) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updateUIPreferences(
                showCount = showFilteredCount,
                showConfidence = showConfidenceScore,
                opacity = overlayOpacity.coerceIn(0.0f, 1.0f)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error updating UI preferences")
        }
    }

    /**
     * Update performance settings
     */
    suspend fun updatePerformanceSettings(
        maxConcurrentAnalyses: Int,
        processingDelayMs: Long,
        cacheSize: Int
    ) = withContext(Dispatchers.IO) {
        try {
            preferencesDao.updatePerformanceSettings(
                maxAnalyses = maxConcurrentAnalyses.coerceIn(1, 10),
                delayMs = processingDelayMs.coerceIn(0L, 1000L),
                cacheSize = cacheSize.coerceIn(100, 10000)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error updating performance settings")
        }
    }

    /**
     * Reset preferences to defaults
     */
    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        try {
            preferencesDao.resetToDefaults()
            preferencesDao.insertOrUpdatePreferences(getDefaultPreferences())
        } catch (e: Exception) {
            Timber.e(e, "Error resetting preferences to defaults")
        }
    }

    // Encrypted preferences methods for app state

    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        return encryptedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Mark first launch as completed
     */
    fun setFirstLaunchCompleted() {
        encryptedPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    /**
     * Check if onboarding is completed
     */
    fun isOnboardingCompleted(): Boolean {
        return encryptedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Mark onboarding as completed
     */
    fun setOnboardingCompleted() {
        encryptedPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    /**
     * Check if accessibility permission is granted
     */
    fun isAccessibilityPermissionGranted(): Boolean {
        return encryptedPrefs.getBoolean(KEY_ACCESSIBILITY_PERMISSION_GRANTED, false)
    }

    /**
     * Update accessibility permission status
     */
    fun setAccessibilityPermissionGranted(granted: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_ACCESSIBILITY_PERMISSION_GRANTED, granted).apply()
    }

    /**
     * Get last model update timestamp
     */
    fun getLastModelUpdate(): Long {
        return encryptedPrefs.getLong(KEY_LAST_MODEL_UPDATE, 0L)
    }

    /**
     * Update last model update timestamp
     */
    fun setLastModelUpdate(timestamp: Long = System.currentTimeMillis()) {
        encryptedPrefs.edit().putLong(KEY_LAST_MODEL_UPDATE, timestamp).apply()
    }

    /**
     * Export preferences for backup
     */
    suspend fun exportPreferences() = withContext(Dispatchers.IO) {
        try {
            preferencesDao.exportPreferences()
        } catch (e: Exception) {
            Timber.e(e, "Error exporting preferences")
            emptyList()
        }
    }

    /**
     * Get default user preferences
     */
    private fun getDefaultPreferences(): UserPreferences {
        return UserPreferences(
            id = "default",
            filteringEnabled = true,
            filterStrictness = FilterStrictness.MEDIUM,
            confidenceThreshold = 0.7f,
            filterVideos = true,
            filterImages = true,
            filterText = true,
            filterAds = true,
            enabledApps = getDefaultEnabledApps(),
            disabledApps = emptySet(),
            customKeywords = emptySet(),
            whitelistKeywords = emptySet(),
            blacklistKeywords = emptySet(),
            enableLearning = true,
            adaptToFeedback = true,
            analyticsEnabled = false,
            dataRetentionDays = 30,
            showFilteredCount = true,
            showConfidenceScore = false,
            overlayOpacity = 0.8f,
            maxConcurrentAnalyses = 3,
            processingDelayMs = 100L,
            cacheSize = 1000,
            lastModified = System.currentTimeMillis(),
            version = 1
        )
    }

    /**
     * Get default enabled apps
     */
    private fun getDefaultEnabledApps(): Set<String> {
        return setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.youtube.android",
            "com.facebook.katana"
        )
    }
}