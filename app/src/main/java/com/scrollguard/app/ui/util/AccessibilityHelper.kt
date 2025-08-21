package com.scrollguard.app.ui.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import timber.log.Timber

/**
 * Helper class for accessibility service operations.
 * Provides utilities for checking service status and managing permissions.
 */
object AccessibilityHelper {

    /**
     * Check if our accessibility service is enabled
     */
    fun isServiceEnabled(context: Context): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            val packageName = context.packageName
            val serviceName = "$packageName/.service.ContentFilterService"
            
            enabledServices.any { service ->
                service.id.equals(serviceName, ignoreCase = true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking accessibility service status")
            false
        }
    }

    /**
     * Check if accessibility service is enabled using Settings.Secure
     */
    fun isServiceEnabledAlternative(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val packageName = context.packageName
            val serviceName = "$packageName/.service.ContentFilterService"
            
            enabledServices.contains(serviceName)
        } catch (e: Exception) {
            Timber.e(e, "Error checking accessibility service status (alternative)")
            false
        }
    }

    /**
     * Get list of enabled accessibility services
     */
    fun getEnabledServices(context: Context): List<String> {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            enabledServices.map { it.id }
        } catch (e: Exception) {
            Timber.e(e, "Error getting enabled accessibility services")
            emptyList()
        }
    }

    /**
     * Get accessibility service info
     */
    fun getServiceInfo(context: Context): AccessibilityServiceInfo? {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            val packageName = context.packageName
            val serviceName = "$packageName/.service.ContentFilterService"
            
            enabledServices.find { service ->
                service.id.equals(serviceName, ignoreCase = true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting accessibility service info")
            null
        }
    }

    /**
     * Check if accessibility is enabled on the device
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            accessibilityManager.isEnabled
        } catch (e: Exception) {
            Timber.e(e, "Error checking accessibility enabled status")
            false
        }
    }

    /**
     * Get accessibility capabilities
     */
    fun getServiceCapabilities(context: Context): String? {
        return try {
            val serviceInfo = getServiceInfo(context)
            serviceInfo?.let {
                val capabilities = mutableListOf<String>()
                
                if (it.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0) {
                    capabilities.add("Interactive Windows")
                }
                if (it.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0) {
                    capabilities.add("Touch Exploration")
                }
                if (it.flags and AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY != 0) {
                    capabilities.add("Enhanced Web Accessibility")
                }
                if (it.flags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS != 0) {
                    capabilities.add("View IDs")
                }
                
                capabilities.joinToString(", ")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting service capabilities")
            null
        }
    }
}