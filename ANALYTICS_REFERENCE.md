# ScrollGuard Analytics Implementation Reference

## Overview

This document provides comprehensive guidance for implementing analytics in ScrollGuard while maintaining strict privacy standards. All analytics must be privacy-safe, aggregated, and never include actual user content.

## Development & Testing Analytics

### Testing During Development

#### Android Studio + Emulator Setup
```kotlin
// DebugAnalytics.kt - Development only
class DebugAnalytics {
    fun logContentClassification(
        contentType: String,
        processingTimeMs: Long,
        confidence: Float,
        modelVersion: String
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("ScrollGuard", """
                Content Classification:
                - Type: $contentType
                - Processing Time: ${processingTimeMs}ms
                - Confidence: $confidence
                - Model: $modelVersion
            """.trimIndent())
        }
    }
}
```

#### Performance Monitoring During Development
```kotlin
// PerformanceTracker.kt
class PerformanceTracker {
    private val timings = mutableMapOf<String, Long>()
    
    fun startTimer(operation: String) {
        timings[operation] = System.currentTimeMillis()
    }
    
    fun endTimer(operation: String): Long {
        val startTime = timings[operation] ?: return 0
        val duration = System.currentTimeMillis() - startTime
        
        if (BuildConfig.DEBUG) {
            Log.d("Performance", "$operation took ${duration}ms")
        }
        
        // In production, send to Firebase
        firebaseAnalytics?.logEvent("performance_metric") {
            param("operation", operation)
            param("duration_ms", duration)
        }
        
        return duration
    }
}
```

## Firebase Analytics Implementation

### Setup and Configuration

```kotlin
// AnalyticsManager.kt
class AnalyticsManager(private val context: Context) {
    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(context)
    }
    
    private val userPrefs by lazy {
        context.getSharedPreferences("analytics_prefs", Context.MODE_PRIVATE)
    }
    
    var analyticsEnabled: Boolean
        get() = userPrefs.getBoolean("analytics_enabled", false)
        set(value) = userPrefs.edit().putBoolean("analytics_enabled", value).apply()
    
    init {
        // Always ask for consent before enabling
        firebaseAnalytics.setAnalyticsCollectionEnabled(analyticsEnabled)
    }
}
```

### User Consent Management

```kotlin
// ConsentManager.kt
class ConsentManager(private val context: Context) {
    
    fun showAnalyticsConsentDialog(onResult: (Boolean) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Help Improve ScrollGuard")
            .setMessage("""
                Would you like to share anonymous usage data to help improve ScrollGuard?
                
                What we collect:
                • App performance metrics
                • Feature usage statistics
                • Error reports and crashes
                
                What we DON'T collect:
                • Your social media content
                • Personal information
                • Specific posts or comments
                
                You can change this anytime in Settings.
            """.trimIndent())
            .setPositiveButton("Yes, Help Improve") { _, _ -> onResult(true) }
            .setNegativeButton("No Thanks") { _, _ -> onResult(false) }
            .setCancelable(false)
            .show()
    }
}
```

## Privacy-Safe Analytics Events

### 1. Personal Usage Analytics (Your Own Data)

```kotlin
// PersonalAnalytics.kt
class PersonalAnalytics(private val analytics: AnalyticsManager) {
    
    fun logContentFiltered(
        appPackage: String,
        contentType: ContentType,
        filterReason: FilterReason,
        confidenceScore: Float
    ) {
        if (!analytics.analyticsEnabled) return
        
        analytics.logEvent("content_filtered") {
            // ✅ Safe - No actual content
            param("app_category", getAppCategory(appPackage))
            param("content_type", contentType.name)
            param("filter_reason", filterReason.name)
            param("confidence_bucket", getConfidenceBucket(confidenceScore))
            param("session_id", getCurrentSessionId()) // Session-scoped only
        }
    }
    
    fun logTimeSaved(
        blockedContentCount: Int,
        estimatedTimeMinutes: Int,
        sessionDurationMinutes: Int
    ) {
        analytics.logEvent("time_saved") {
            param("blocked_content_count", blockedContentCount)
            param("estimated_time_saved_minutes", estimatedTimeMinutes)
            param("session_duration_minutes", sessionDurationMinutes)
            param("productivity_gain_ratio", estimatedTimeMinutes.toFloat() / sessionDurationMinutes)
        }
    }
    
    fun logUserFeedback(
        feedbackType: FeedbackType,
        originalClassification: String,
        userCorrection: String?
    ) {
        analytics.logEvent("user_feedback") {
            param("feedback_type", feedbackType.name)
            param("classification_accuracy", if (userCorrection == null) "correct" else "incorrect")
            param("improvement_category", userCorrection ?: "none")
        }
    }
    
    private fun getAppCategory(packageName: String): String {
        return when {
            packageName.contains("instagram") -> "social_photo"
            packageName.contains("tiktok") -> "social_video"
            packageName.contains("twitter") || packageName.contains("x.com") -> "social_microblog"
            packageName.contains("reddit") -> "social_forum"
            packageName.contains("youtube") -> "video_platform"
            else -> "other"
        }
    }
    
    private fun getConfidenceBucket(score: Float): String {
        return when {
            score >= 0.9 -> "high"
            score >= 0.7 -> "medium"
            score >= 0.5 -> "low"
            else -> "very_low"
        }
    }
}

enum class ContentType {
    TEXT_POST, VIDEO_POST, IMAGE_POST, COMMENT, STORY, ADVERTISEMENT
}

enum class FilterReason {
    UNPRODUCTIVE, CLICKBAIT, NEGATIVE_CONTENT, ADDICTIVE_CONTENT, USER_DEFINED
}

enum class FeedbackType {
    FALSE_POSITIVE, FALSE_NEGATIVE, CORRECT_FILTER, MANUAL_OVERRIDE
}
```

### 2. Aggregated User Analytics

```kotlin
// AggregatedAnalytics.kt
class AggregatedAnalytics(private val analytics: AnalyticsManager) {
    
    fun logDailySummary(summary: DailySummary) {
        analytics.logEvent("daily_summary") {
            // ✅ Safe - Aggregated data only
            param("total_content_filtered", summary.totalFiltered)
            param("total_content_viewed", summary.totalViewed)
            param("filter_accuracy_percentage", summary.accuracyPercentage)
            param("most_filtered_app_category", summary.topFilteredCategory)
            param("active_filtering_hours", summary.activeHours)
            param("user_satisfaction_rating", summary.satisfactionRating)
        }
    }
    
    fun logWeeklySummary(summary: WeeklySummary) {
        analytics.logEvent("weekly_summary") {
            param("avg_daily_filtered_content", summary.avgDailyFiltered)
            param("total_time_saved_minutes", summary.totalTimeSaved)
            param("most_productive_day", summary.mostProductiveDay)
            param("filter_effectiveness_trend", summary.effectivenessTrend)
            param("user_engagement_score", summary.engagementScore)
        }
    }
    
    fun logAppUsagePattern(patterns: List<AppUsagePattern>) {
        // ✅ Safe - Pattern analysis without content
        patterns.forEach { pattern ->
            analytics.logEvent("app_usage_pattern") {
                param("app_category", pattern.category)
                param("peak_usage_hour", pattern.peakHour)
                param("avg_session_duration_minutes", pattern.avgSessionDuration)
                param("filter_engagement_rate", pattern.filterEngagementRate)
                param("time_of_day_category", pattern.timeCategory)
            }
        }
    }
}

data class DailySummary(
    val totalFiltered: Int,
    val totalViewed: Int,
    val accuracyPercentage: Float,
    val topFilteredCategory: String,
    val activeHours: Int,
    val satisfactionRating: Float
)

data class WeeklySummary(
    val avgDailyFiltered: Float,
    val totalTimeSaved: Int,
    val mostProductiveDay: String,
    val effectivenessTrend: String, // "improving", "stable", "declining"
    val engagementScore: Float
)

data class AppUsagePattern(
    val category: String,
    val peakHour: Int,
    val avgSessionDuration: Int,
    val filterEngagementRate: Float,
    val timeCategory: String // "morning", "afternoon", "evening", "night"
)
```

### 3. Performance & Technical Analytics

```kotlin
// TechnicalAnalytics.kt
class TechnicalAnalytics(private val analytics: AnalyticsManager) {
    
    fun logModelPerformance(
        modelVersion: String,
        classificationTimeMs: Long,
        memoryUsageMB: Int,
        confidenceScore: Float,
        deviceSpecs: DeviceSpecs
    ) {
        analytics.logEvent("model_performance") {
            param("model_version", modelVersion)
            param("classification_time_ms", classificationTimeMs)
            param("memory_usage_mb", memoryUsageMB)
            param("confidence_score_bucket", getConfidenceBucket(confidenceScore))
            param("device_ram_gb", deviceSpecs.ramGB)
            param("android_version", deviceSpecs.androidVersion)
            param("device_tier", deviceSpecs.performanceTier)
        }
    }
    
    fun logBatteryImpact(
        sessionDurationMinutes: Int,
        batteryDrainPercentage: Float,
        backgroundProcessingTime: Long
    ) {
        analytics.logEvent("battery_impact") {
            param("session_duration_minutes", sessionDurationMinutes)
            param("battery_drain_percentage", batteryDrainPercentage)
            param("background_processing_ms", backgroundProcessingTime)
            param("efficiency_score", calculateEfficiencyScore(sessionDurationMinutes, batteryDrainPercentage))
        }
    }
    
    fun logAccessibilityServiceHealth(
        serviceUptime: Long,
        eventProcessingRate: Float,
        errorCount: Int,
        recoveryCount: Int
    ) {
        analytics.logEvent("accessibility_service_health") {
            param("uptime_hours", serviceUptime / 3600000)
            param("events_per_minute", eventProcessingRate)
            param("error_count", errorCount)
            param("recovery_count", recoveryCount)
            param("stability_score", calculateStabilityScore(errorCount, recoveryCount))
        }
    }
    
    private fun calculateEfficiencyScore(durationMinutes: Int, drainPercentage: Float): Float {
        return if (drainPercentage > 0) durationMinutes / drainPercentage else 100f
    }
    
    private fun calculateStabilityScore(errors: Int, recoveries: Int): Float {
        val totalEvents = errors + recoveries
        return if (totalEvents > 0) recoveries.toFloat() / totalEvents else 1f
    }
}

data class DeviceSpecs(
    val ramGB: Int,
    val androidVersion: Int,
    val performanceTier: String // "low", "mid", "high"
)
```

## Interesting Data to Collect (Future Iterations)

### User Behavior Insights

```kotlin
// BehaviorAnalytics.kt - Future Implementation
class BehaviorAnalytics(private val analytics: AnalyticsManager) {
    
    fun logScrollingPatterns(
        scrollSpeed: Float,
        pauseDuration: Long,
        contentEngagementTime: Long,
        appCategory: String
    ) {
        // ✅ Behavioral patterns without content details
        analytics.logEvent("scrolling_pattern") {
            param("scroll_speed_category", categorizeScrollSpeed(scrollSpeed))
            param("pause_duration_bucket", categorizePauseDuration(pauseDuration))
            param("engagement_time_bucket", categorizeEngagementTime(contentEngagementTime))
            param("app_category", appCategory)
            param("time_of_day", getCurrentTimeCategory())
        }
    }
    
    fun logProductivityCorrelation(
        preFilteringScreenTime: Int,
        postFilteringScreenTime: Int,
        userReportedProductivity: Float,
        filterStrictness: String
    ) {
        analytics.logEvent("productivity_correlation") {
            param("screen_time_reduction_percentage", 
                  calculateReduction(preFilteringScreenTime, postFilteringScreenTime))
            param("productivity_improvement_score", userReportedProductivity)
            param("filter_strictness_level", filterStrictness)
            param("correlation_strength", calculateCorrelation(
                calculateReduction(preFilteringScreenTime, postFilteringScreenTime),
                userReportedProductivity
            ))
        }
    }
    
    fun logContentCategoryEffectiveness(
        categoryStats: Map<String, CategoryEffectiveness>
    ) {
        categoryStats.forEach { (category, effectiveness) ->
            analytics.logEvent("category_effectiveness") {
                param("content_category", category)
                param("filter_accuracy_rate", effectiveness.accuracyRate)
                param("user_satisfaction_score", effectiveness.userSatisfaction)
                param("time_saved_ratio", effectiveness.timeSavedRatio)
                param("false_positive_rate", effectiveness.falsePositiveRate)
            }
        }
    }
}

data class CategoryEffectiveness(
    val accuracyRate: Float,
    val userSatisfaction: Float,
    val timeSavedRatio: Float,
    val falsePositiveRate: Float
)
```

### Learning System Analytics

```kotlin
// LearningAnalytics.kt - Future Implementation
class LearningAnalytics(private val analytics: AnalyticsManager) {
    
    fun logModelAdaptation(
        originalAccuracy: Float,
        adaptedAccuracy: Float,
        trainingIterations: Int,
        personalDataPoints: Int
    ) {
        analytics.logEvent("model_adaptation") {
            param("accuracy_improvement", adaptedAccuracy - originalAccuracy)
            param("training_iterations", trainingIterations)
            param("personal_data_points", personalDataPoints)
            param("adaptation_effectiveness", categorizeImprovement(adaptedAccuracy - originalAccuracy))
        }
    }
    
    fun logUserPreferenceLearning(
        learningCategory: String,
        confidenceChange: Float,
        patternStrength: Float
    ) {
        analytics.logEvent("preference_learning") {
            param("learning_category", learningCategory)
            param("confidence_change", confidenceChange)
            param("pattern_strength_bucket", categorizePatternStrength(patternStrength))
            param("learning_effectiveness", if (confidenceChange > 0) "positive" else "negative")
        }
    }
}
```

## Error Tracking & Crash Reporting

```kotlin
// ErrorAnalytics.kt
class ErrorAnalytics(private val analytics: AnalyticsManager) {
    
    fun logAccessibilityServiceError(
        errorType: String,
        errorCode: Int,
        recoverySuccess: Boolean,
        affectedApp: String?
    ) {
        analytics.logEvent("accessibility_error") {
            param("error_type", errorType)
            param("error_code", errorCode)
            param("recovery_success", recoverySuccess)
            param("affected_app_category", getAppCategory(affectedApp))
            param("android_version", Build.VERSION.SDK_INT)
        }
    }
    
    fun logModelInferenceError(
        modelVersion: String,
        errorType: String,
        inputSize: Int,
        memoryAvailable: Long
    ) {
        analytics.logEvent("model_inference_error") {
            param("model_version", modelVersion)
            param("error_type", errorType)
            param("input_size_bucket", categorizeInputSize(inputSize))
            param("memory_available_mb", memoryAvailable / 1024 / 1024)
        }
    }
}
```

## Privacy Guidelines & Compliance

### Data Collection Rules

#### ✅ ALWAYS ALLOWED
```kotlin
// Safe data examples
val safeData = mapOf(
    "app_category" to "social_video",           // Generic category
    "content_type" to "video_post",             // Content type only
    "confidence_score_bucket" to "high",        // Bucketed scores
    "processing_time_ms" to 145,                // Performance metrics
    "filter_accuracy_percentage" to 87.5,       // Aggregated accuracy
    "session_duration_minutes" to 23,           // Time metrics
    "user_satisfaction_rating" to 4.2           // User-provided ratings
)
```

#### ❌ NEVER ALLOWED
```kotlin
// Examples of forbidden data
val forbiddenData = mapOf(
    "post_text" to "actual post content",       // Actual content
    "user_comment" to "user's comment",         // Personal text
    "username" to "@actualuser",                // Identifying info
    "specific_hashtags" to "#personalhashtag",  // Specific content markers
    "image_description" to "detailed image",    // Content descriptions
    "location_mentioned" to "user's location",  // Personal location data
    "personal_topics" to "user's interests"     // Personal preferences details
)
```

### Implementation Safeguards

```kotlin
// DataSanitizer.kt
class DataSanitizer {
    
    fun sanitizeForAnalytics(rawData: Map<String, Any>): Map<String, Any> {
        return rawData.mapValues { (key, value) ->
            when {
                isContentField(key) -> "[REDACTED]"
                isPersonalField(key) -> "[REDACTED]"
                isNumericField(key) -> bucketizeNumeric(value)
                isTextField(key) -> generalizeText(value.toString())
                else -> value
            }
        }.filterNot { (key, value) -> 
            value == "[REDACTED]" || isForbiddenField(key)
        }
    }
    
    private fun isContentField(key: String): Boolean {
        val contentFields = listOf("text", "content", "message", "caption", "comment")
        return contentFields.any { key.contains(it, ignoreCase = true) }
    }
    
    private fun bucketizeNumeric(value: Any): String {
        val num = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: return "unknown"
            else -> return "unknown"
        }
        
        return when {
            num < 10 -> "low"
            num < 50 -> "medium"
            num < 100 -> "high"
            else -> "very_high"
        }
    }
}
```

## Google Play Store Compliance

### AccessibilityService Declaration

```xml
<!-- accessibility_service.xml -->
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:packageNames="com.instagram.android,com.zhiliaoapp.musically,com.twitter.android,com.reddit.frontpage"
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
    android:accessibilityFlags="flagDefault"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true" />
```

### Privacy Policy Template

```markdown
# ScrollGuard Privacy Policy

## Data Collection
ScrollGuard processes social media content locally on your device to provide content filtering. We collect only aggregated, anonymous usage statistics to improve the app.

## What We Collect
- App performance metrics (processing speed, battery usage)
- Feature usage statistics (which filters are most effective)
- Error reports and crash data
- Aggregated effectiveness measurements

## What We DON'T Collect
- Your social media posts, comments, or messages
- Personal information or identifying data
- Specific content you view or interact with
- Your social media account information

## Data Storage
All content analysis happens on your device. Analytics data is aggregated and anonymized before being sent to our servers.

## Your Rights
You can opt out of analytics at any time in the app settings. This will not affect the core filtering functionality.
```

## Implementation Checklist

### Phase 1: Basic Analytics Setup
- [ ] Implement `AnalyticsManager` with consent flow
- [ ] Add basic performance tracking
- [ ] Set up crash reporting
- [ ] Create privacy policy

### Phase 2: Core Metrics
- [ ] Implement content filtering analytics (privacy-safe)
- [ ] Add user feedback tracking
- [ ] Set up daily/weekly summary reporting
- [ ] Create effectiveness measurement system

### Phase 3: Advanced Insights
- [ ] Implement behavioral pattern analysis
- [ ] Add productivity correlation tracking
- [ ] Set up A/B testing framework
- [ ] Create personalization effectiveness metrics

### Phase 4: Optimization Analytics
- [ ] Add detailed performance monitoring
- [ ] Implement adaptive analytics based on user engagement
- [ ] Set up predictive analytics for filter effectiveness
- [ ] Create automated reporting dashboard

## Testing Strategy

### Development Testing
```kotlin
// AnalyticsTest.kt
class AnalyticsTest {
    @Test
    fun `analytics data contains no personal content`() {
        val testEvent = createTestAnalyticsEvent()
        val sanitizedEvent = DataSanitizer().sanitizeForAnalytics(testEvent)
        
        assertThat(sanitizedEvent).doesNotContainKey("user_content")
        assertThat(sanitizedEvent).doesNotContainKey("personal_data")
        assertThat(sanitizedEvent.values).noneMatch { it.toString().contains("@user") }
    }
    
    @Test
    fun `all numeric values are properly bucketed`() {
        val testData = mapOf("score" to 0.87, "count" to 145)
        val result = DataSanitizer().sanitizeForAnalytics(testData)
        
        assertThat(result["score"]).isEqualTo("high")
        assertThat(result["count"]).isEqualTo("very_high")
    }
}
```

### User Privacy Verification
- Regular audits of all analytics events
- Automated testing for personal data leakage
- Manual review of all analytics code before release
- User data export functionality for transparency

---

*This document should be updated as analytics requirements evolve. Always prioritize user privacy over data collection.*