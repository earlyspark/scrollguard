package com.scrollguard.app.ui.model

/**
 * Data class representing app information for settings UI.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val iconRes: Int,
    val isEnabled: Boolean = false
)