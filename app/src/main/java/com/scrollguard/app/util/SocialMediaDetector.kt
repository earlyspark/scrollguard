package com.scrollguard.app.util

/**
 * Utility class for detecting and identifying different social media applications.
 * Provides methods to determine the type of social media app based on package names.
 */
object SocialMediaDetector {

    // Package name constants
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    const val TWITTER_PACKAGE = "com.twitter.android"
    const val REDDIT_PACKAGE = "com.reddit.frontpage"
    const val YOUTUBE_PACKAGE = "com.youtube.android"
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val SNAPCHAT_PACKAGE = "com.snapchat.android"
    const val LINKEDIN_PACKAGE = "com.linkedin.android"

    // Alternative package names for different versions/regions
    private val INSTAGRAM_PACKAGES = setOf(
        INSTAGRAM_PACKAGE,
        "com.instagram.lite"
    )

    private val TIKTOK_PACKAGES = setOf(
        TIKTOK_PACKAGE,
        "com.ss.android.ugc.trill", // TikTok Lite
        "com.zhiliaoapp.musically.go" // TikTok Go
    )

    private val TWITTER_PACKAGES = setOf(
        TWITTER_PACKAGE,
        "com.twitter.android.lite"
    )

    private val REDDIT_PACKAGES = setOf(
        REDDIT_PACKAGE,
        "com.reddit.frontpage.compact"
    )

    private val FACEBOOK_PACKAGES = setOf(
        FACEBOOK_PACKAGE,
        "com.facebook.lite",
        "com.facebook.mlite"
    )

    private val YOUTUBE_PACKAGES = setOf(
        YOUTUBE_PACKAGE,
        "com.google.android.youtube.tv",
        "com.google.android.youtube.music"
    )

    /**
     * Check if the package name belongs to Instagram
     */
    fun isInstagram(packageName: String?): Boolean {
        return packageName in INSTAGRAM_PACKAGES
    }

    /**
     * Check if the package name belongs to TikTok
     */
    fun isTikTok(packageName: String?): Boolean {
        return packageName in TIKTOK_PACKAGES
    }

    /**
     * Check if the package name belongs to Twitter/X
     */
    fun isTwitter(packageName: String?): Boolean {
        return packageName in TWITTER_PACKAGES
    }

    /**
     * Check if the package name belongs to Reddit
     */
    fun isReddit(packageName: String?): Boolean {
        return packageName in REDDIT_PACKAGES
    }

    /**
     * Check if the package name belongs to Facebook
     */
    fun isFacebook(packageName: String?): Boolean {
        return packageName in FACEBOOK_PACKAGES
    }

    /**
     * Check if the package name belongs to YouTube
     */
    fun isYouTube(packageName: String?): Boolean {
        return packageName in YOUTUBE_PACKAGES
    }

    /**
     * Check if the package name belongs to Snapchat
     */
    fun isSnapchat(packageName: String?): Boolean {
        return packageName == SNAPCHAT_PACKAGE
    }

    /**
     * Check if the package name belongs to LinkedIn
     */
    fun isLinkedIn(packageName: String?): Boolean {
        return packageName == LINKEDIN_PACKAGE
    }

    /**
     * Check if the package name belongs to any supported social media app
     */
    fun isSocialMediaApp(packageName: String?): Boolean {
        return isInstagram(packageName) ||
               isTikTok(packageName) ||
               isTwitter(packageName) ||
               isReddit(packageName) ||
               isFacebook(packageName) ||
               isYouTube(packageName) ||
               isSnapchat(packageName) ||
               isLinkedIn(packageName)
    }

    /**
     * Get the social media platform type from package name
     */
    fun getPlatformType(packageName: String?): SocialMediaPlatform {
        return when {
            isInstagram(packageName) -> SocialMediaPlatform.INSTAGRAM
            isTikTok(packageName) -> SocialMediaPlatform.TIKTOK
            isTwitter(packageName) -> SocialMediaPlatform.TWITTER
            isReddit(packageName) -> SocialMediaPlatform.REDDIT
            isFacebook(packageName) -> SocialMediaPlatform.FACEBOOK
            isYouTube(packageName) -> SocialMediaPlatform.YOUTUBE
            isSnapchat(packageName) -> SocialMediaPlatform.SNAPCHAT
            isLinkedIn(packageName) -> SocialMediaPlatform.LINKEDIN
            else -> SocialMediaPlatform.UNKNOWN
        }
    }

    /**
     * Get user-friendly name for the platform
     */
    fun getPlatformName(packageName: String?): String {
        return when (getPlatformType(packageName)) {
            SocialMediaPlatform.INSTAGRAM -> "Instagram"
            SocialMediaPlatform.TIKTOK -> "TikTok"
            SocialMediaPlatform.TWITTER -> "Twitter/X"
            SocialMediaPlatform.REDDIT -> "Reddit"
            SocialMediaPlatform.FACEBOOK -> "Facebook"
            SocialMediaPlatform.YOUTUBE -> "YouTube"
            SocialMediaPlatform.SNAPCHAT -> "Snapchat"
            SocialMediaPlatform.LINKEDIN -> "LinkedIn"
            SocialMediaPlatform.UNKNOWN -> "Unknown App"
        }
    }

    /**
     * Check if the platform is primarily video-based
     */
    fun isVideoPlatform(packageName: String?): Boolean {
        return isTikTok(packageName) || 
               isYouTube(packageName) || 
               isSnapchat(packageName)
    }

    /**
     * Check if the platform is primarily text-based
     */
    fun isTextPlatform(packageName: String?): Boolean {
        return isTwitter(packageName) || 
               isReddit(packageName) || 
               isLinkedIn(packageName)
    }

    /**
     * Check if the platform is primarily image-based
     */
    fun isImagePlatform(packageName: String?): Boolean {
        return isInstagram(packageName)
    }

    /**
     * Get all supported package names
     */
    fun getAllSupportedPackages(): Set<String> {
        return INSTAGRAM_PACKAGES + 
               TIKTOK_PACKAGES + 
               TWITTER_PACKAGES + 
               REDDIT_PACKAGES + 
               FACEBOOK_PACKAGES + 
               YOUTUBE_PACKAGES + 
               setOf(SNAPCHAT_PACKAGE, LINKEDIN_PACKAGE)
    }

    /**
     * Get content extraction strategy based on platform
     */
    fun getContentExtractionStrategy(packageName: String?): ContentExtractionStrategy {
        return when (getPlatformType(packageName)) {
            SocialMediaPlatform.INSTAGRAM -> ContentExtractionStrategy.INSTAGRAM
            SocialMediaPlatform.TIKTOK -> ContentExtractionStrategy.TIKTOK
            SocialMediaPlatform.TWITTER -> ContentExtractionStrategy.TWITTER
            SocialMediaPlatform.REDDIT -> ContentExtractionStrategy.REDDIT
            SocialMediaPlatform.FACEBOOK -> ContentExtractionStrategy.FACEBOOK
            SocialMediaPlatform.YOUTUBE -> ContentExtractionStrategy.YOUTUBE
            SocialMediaPlatform.SNAPCHAT -> ContentExtractionStrategy.SNAPCHAT
            SocialMediaPlatform.LINKEDIN -> ContentExtractionStrategy.LINKEDIN
            SocialMediaPlatform.UNKNOWN -> ContentExtractionStrategy.GENERIC
        }
    }
}

/**
 * Enum representing different social media platforms
 */
enum class SocialMediaPlatform {
    INSTAGRAM,
    TIKTOK,
    TWITTER,
    REDDIT,
    FACEBOOK,
    YOUTUBE,
    SNAPCHAT,
    LINKEDIN,
    UNKNOWN
}

/**
 * Enum representing different content extraction strategies
 */
enum class ContentExtractionStrategy {
    INSTAGRAM,    // Focus on captions, comments, story text
    TIKTOK,       // Focus on video descriptions, captions
    TWITTER,      // Focus on tweet text, replies
    REDDIT,       // Focus on post titles, content, comments
    FACEBOOK,     // Focus on post text, comments
    YOUTUBE,      // Focus on video titles, descriptions, comments
    SNAPCHAT,     // Focus on story text, chat messages
    LINKEDIN,     // Focus on post content, professional updates
    GENERIC       // Generic text extraction
}