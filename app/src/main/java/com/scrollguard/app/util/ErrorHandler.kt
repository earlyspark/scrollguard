package com.scrollguard.app.util

import android.content.Context
import com.scrollguard.app.service.analytics.AnalyticsManager
import com.scrollguard.app.ui.util.NotificationHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * Global error handler for managing exceptions and error reporting.
 * Provides centralized error handling, logging, and user notification.
 */
class ErrorHandler(
    private val context: Context,
    private val analyticsManager: AnalyticsManager
) {

    private val errorScope = CoroutineScope(SupervisorJob())

    /**
     * Coroutine exception handler for background operations
     */
    val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, exception ->
        handleException(exception, "Coroutine", coroutineContext)
    }

    /**
     * Handle exceptions with context and logging
     */
    fun handleException(
        exception: Throwable,
        source: String,
        context: CoroutineContext? = null,
        showNotification: Boolean = false
    ) {
        errorScope.launch {
            try {
                // Log the error
                Timber.e(exception, "Error in $source")

                // Log to analytics (if consent given)
                logErrorToAnalytics(exception, source)

                // Show notification if requested
                if (showNotification) {
                    showErrorNotification(exception, source)
                }

            } catch (e: Exception) {
                // Fallback logging if error handling fails
                Timber.e(e, "Error in error handler")
            }
        }
    }

    /**
     * Handle network errors specifically
     */
    fun handleNetworkError(exception: Throwable, operation: String) {
        handleException(exception, "Network:$operation", showNotification = true)
        
        NotificationHelper.showErrorNotification(
            context,
            "Network Error",
            "Unable to complete $operation. Please check your internet connection."
        )
    }

    /**
     * Handle model loading errors
     */
    fun handleModelError(exception: Throwable) {
        handleException(exception, "ModelLoading", showNotification = true)
        
        NotificationHelper.showErrorNotification(
            context,
            "AI Model Error",
            "Failed to load AI model. Content filtering may not work properly."
        )
    }

    /**
     * Handle accessibility service errors
     */
    fun handleAccessibilityError(exception: Throwable) {
        handleException(exception, "AccessibilityService")
        
        // Don't show notification for accessibility errors as they're frequent
        Timber.w("Accessibility service error: ${exception.message}")
    }

    /**
     * Handle database errors
     */
    fun handleDatabaseError(exception: Throwable, operation: String) {
        handleException(exception, "Database:$operation")
        
        // Log as warning since these are usually recoverable
        Timber.w(exception, "Database error in $operation")
    }

    /**
     * Log error to analytics
     */
    private fun logErrorToAnalytics(exception: Throwable, source: String) {
        try {
            analyticsManager.logEvent("error_occurred") {
                param("error_source", source)
                param("error_type", exception.javaClass.simpleName)
                param("error_message", exception.message?.take(100) ?: "Unknown")
                param("stack_trace", exception.stackTraceToString().take(500))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to log error to analytics")
        }
    }

    /**
     * Show error notification to user
     */
    private fun showErrorNotification(exception: Throwable, source: String) {
        val title = when {
            source.contains("Network") -> "Network Error"
            source.contains("Model") -> "AI Model Error"
            source.contains("Database") -> "Data Error"
            source.contains("Permission") -> "Permission Error"
            else -> "App Error"
        }

        val message = getErrorMessage(exception, source)
        
        NotificationHelper.showErrorNotification(context, title, message)
    }

    /**
     * Get user-friendly error message
     */
    private fun getErrorMessage(exception: Throwable, source: String): String {
        return when {
            exception is java.net.UnknownHostException -> 
                "No internet connection available"
            exception is java.net.SocketTimeoutException -> 
                "Connection timed out"
            exception is SecurityException -> 
                "Permission denied"
            exception is java.io.FileNotFoundException -> 
                "Required file not found"
            exception.message?.contains("permission", ignoreCase = true) == true -> 
                "Permission required to continue"
            exception.message?.contains("network", ignoreCase = true) == true -> 
                "Network connection problem"
            source.contains("Model") -> 
                "AI model loading failed"
            source.contains("Database") -> 
                "Data storage error"
            else -> 
                "An unexpected error occurred"
        }
    }

    /**
     * Report critical errors that require immediate attention
     */
    fun reportCriticalError(exception: Throwable, source: String, context: String? = null) {
        Timber.e(exception, "CRITICAL ERROR in $source: $context")
        
        analyticsManager.logEvent("critical_error") {
            param("error_source", source)
            param("error_context", context ?: "unknown")
            param("error_type", exception.javaClass.simpleName)
            param("error_message", exception.message ?: "Unknown")
        }
        
        showErrorNotification(exception, source)
    }

    /**
     * Check if error is recoverable
     */
    fun isRecoverableError(exception: Throwable): Boolean {
        return when (exception) {
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.io.IOException -> true
            else -> false
        }
    }
}