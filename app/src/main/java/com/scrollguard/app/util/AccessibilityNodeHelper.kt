package com.scrollguard.app.util

import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Utility class for working with AccessibilityNodeInfo objects.
 * Provides helper methods for finding and extracting content from accessibility nodes.
 */
object AccessibilityNodeHelper {

    /**
     * Find nodes by view ID resource name
     */
    fun findNodesByViewId(
        rootNode: AccessibilityNodeInfo, 
        viewId: String
    ): List<AccessibilityNodeInfo>? {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            findNodesByViewIdRecursive(rootNode, viewId, result)
        } catch (e: Exception) {
            Timber.e(e, "Error finding nodes by view ID: $viewId")
            return null
        }
        
        return if (result.isEmpty()) null else result
    }

    /**
     * Find nodes by class name
     */
    fun findNodesByClassName(
        rootNode: AccessibilityNodeInfo, 
        className: String
    ): List<AccessibilityNodeInfo>? {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            findNodesByClassNameRecursive(rootNode, className, result)
        } catch (e: Exception) {
            Timber.e(e, "Error finding nodes by class name: $className")
            return null
        }
        
        return if (result.isEmpty()) null else result
    }

    /**
     * Find nodes containing specific text
     */
    fun findNodesByText(
        rootNode: AccessibilityNodeInfo, 
        text: String, 
        exactMatch: Boolean = false
    ): List<AccessibilityNodeInfo>? {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            findNodesByTextRecursive(rootNode, text, exactMatch, result)
        } catch (e: Exception) {
            Timber.e(e, "Error finding nodes by text: $text")
            return null
        }
        
        return if (result.isEmpty()) null else result
    }

    /**
     * Find nodes by content description
     */
    fun findNodesByContentDescription(
        rootNode: AccessibilityNodeInfo, 
        contentDescription: String,
        exactMatch: Boolean = false
    ): List<AccessibilityNodeInfo>? {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            findNodesByContentDescriptionRecursive(rootNode, contentDescription, exactMatch, result)
        } catch (e: Exception) {
            Timber.e(e, "Error finding nodes by content description: $contentDescription")
            return null
        }
        
        return if (result.isEmpty()) null else result
    }

    /**
     * Extract all text content from a node and its children
     */
    fun extractAllText(node: AccessibilityNodeInfo): String {
        val textBuilder = StringBuilder()
        
        try {
            extractAllTextRecursive(node, textBuilder)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting all text")
        }
        
        return textBuilder.toString().trim()
    }

    /**
     * Get bounds of a node in screen coordinates
     */
    fun getScreenBounds(node: AccessibilityNodeInfo): android.graphics.Rect {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return bounds
    }

    /**
     * Check if a node is visible on screen
     */
    fun isNodeVisible(node: AccessibilityNodeInfo): Boolean {
        return node.isVisibleToUser && 
               node.isEnabled && 
               !node.isContentInvalid
    }

    /**
     * Check if a node represents a clickable element
     */
    fun isClickable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || 
               node.isFocusable || 
               node.actionList.any { 
                   it.id == AccessibilityNodeInfo.ACTION_CLICK 
               }
    }

    /**
     * Get the depth of a node in the accessibility tree
     */
    fun getNodeDepth(node: AccessibilityNodeInfo): Int {
        var depth = 0
        var current = node.parent
        
        while (current != null) {
            depth++
            val parent = current.parent
            current.recycle()
            current = parent
        }
        
        return depth
    }

    /**
     * Find the closest parent node with a specific property
     */
    fun findParentWithProperty(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        var current = node.parent
        
        while (current != null) {
            if (predicate(current)) {
                return current
            }
            
            val parent = current.parent
            current.recycle()
            current = parent
        }
        
        return null
    }

    // Private recursive helper methods

    private fun findNodesByViewIdRecursive(
        node: AccessibilityNodeInfo,
        viewId: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.viewIdResourceName == viewId) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                findNodesByViewIdRecursive(it, viewId, result)
            }
        }
    }

    private fun findNodesByClassNameRecursive(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                findNodesByClassNameRecursive(it, className, result)
            }
        }
    }

    private fun findNodesByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        exactMatch: Boolean,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString()
        if (nodeText != null) {
            val matches = if (exactMatch) {
                nodeText == text
            } else {
                nodeText.contains(text, ignoreCase = true)
            }
            
            if (matches) {
                result.add(node)
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                findNodesByTextRecursive(it, text, exactMatch, result)
            }
        }
    }

    private fun findNodesByContentDescriptionRecursive(
        node: AccessibilityNodeInfo,
        contentDescription: String,
        exactMatch: Boolean,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeDescription = node.contentDescription?.toString()
        if (nodeDescription != null) {
            val matches = if (exactMatch) {
                nodeDescription == contentDescription
            } else {
                nodeDescription.contains(contentDescription, ignoreCase = true)
            }
            
            if (matches) {
                result.add(node)
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                findNodesByContentDescriptionRecursive(it, contentDescription, exactMatch, result)
            }
        }
    }

    private fun extractAllTextRecursive(
        node: AccessibilityNodeInfo,
        textBuilder: StringBuilder
    ) {
        // Add node's own text
        node.text?.let { text ->
            if (text.isNotEmpty()) {
                if (textBuilder.isNotEmpty()) {
                    textBuilder.append(" ")
                }
                textBuilder.append(text)
            }
        }
        
        // Add content description if no text
        if (node.text.isNullOrEmpty()) {
            node.contentDescription?.let { description ->
                if (description.isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append(" ")
                    }
                    textBuilder.append(description)
                }
            }
        }
        
        // Recursively process children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                extractAllTextRecursive(it, textBuilder)
            }
        }
    }
}