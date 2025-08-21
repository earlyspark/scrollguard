package com.scrollguard.app.data.model

/**
 * Enum representing different types of user feedback on content filtering decisions.
 */
enum class FeedbackType {
    CORRECT_FILTER,      // Content was correctly filtered as unproductive
    INCORRECT_FILTER,    // Content was incorrectly filtered (should not have been filtered)
    SHOULD_FILTER,       // Content should have been filtered but wasn't
    NEUTRAL              // Neutral feedback
}