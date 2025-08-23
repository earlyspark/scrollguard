package com.scrollguard.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.data.model.ContentAnalysis
import com.scrollguard.app.data.model.ContentType
import com.scrollguard.app.service.analytics.AnalyticsManager
import com.scrollguard.app.service.llm.LlamaInferenceManager
import com.scrollguard.app.util.AccessibilityNodeHelper
import com.scrollguard.app.util.SocialMediaDetector
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Main accessibility service that monitors social media apps and filters content.
 * This service runs in the background and intercepts content from target apps.
 */
class ContentFilterService : AccessibilityService() {

    companion object {
        private const val CONTENT_PROCESSING_DELAY_MS = 100L
        private const val MAX_CONCURRENT_ANALYSES = 3
        private const val OVERLAY_FADE_DURATION_MS = 300L
        
        // Supported social media packages
        private val SUPPORTED_PACKAGES = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.twitter.android", // Twitter/X
            "com.reddit.frontpage",
            "com.youtube.android",
            "com.facebook.katana",
            "com.snapchat.android"
        )
    }

    private lateinit var app: ScrollGuardApplication
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var llamaInferenceManager: LlamaInferenceManager
    private lateinit var windowManager: WindowManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val handler = Handler(Looper.getMainLooper())
    private val contentCache = ConcurrentHashMap<String, ContentAnalysis>()
    private val activeOverlays = ConcurrentHashMap<AccessibilityNodeInfo, OverlayInfo>()
    
    private data class OverlayInfo(
        val overlay: View,
        val layoutParams: WindowManager.LayoutParams,
        val contentBounds: android.graphics.Rect
    )
    
    private var isServiceEnabled = false
    private var processingQueue = mutableListOf<ContentProcessingTask>()
    private var activeAnalyses = 0

    override fun onCreate() {
        super.onCreate()
        Timber.d("ContentFilterService created")
        
        app = application as ScrollGuardApplication
        analyticsManager = app.analyticsManager
        llamaInferenceManager = app.llamaInferenceManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Start foreground service for background processing
        startForegroundService()
        
        analyticsManager.logEvent("accessibility_service_start") {
            param("service_version", "1.0")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("ContentFilterService connected")
        
        isServiceEnabled = true
        
        // Initialize LLM inference
        serviceScope.launch {
            initializeLLM()
        }
        
        // Show connection confirmation
        Toast.makeText(
            this, 
            "ScrollGuard is now protecting your feed", 
            Toast.LENGTH_SHORT
        ).show()
        
        analyticsManager.logEvent("accessibility_service_connected") {
            param("supported_packages", SUPPORTED_PACKAGES.size)
        }
    }

    override fun onInterrupt() {
        Timber.w("ContentFilterService interrupted")
        
        // Clear overlays immediately on interruption
        clearAllOverlays()
        
        analyticsManager.logEvent("accessibility_service_interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("ContentFilterService destroyed")
        
        isServiceEnabled = false
        cleanup()
        
        analyticsManager.logEvent("accessibility_service_destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceEnabled || event == null) return
        
        // Only process events from supported social media apps
        val packageName = event.packageName?.toString()
        Timber.d("ScrollGuard: Accessibility event from $packageName")
        
        if (packageName !in SUPPORTED_PACKAGES) return
        
        Timber.d("ScrollGuard: Processing event from supported app: $packageName")
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleContentChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
        }
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        // Update overlay positions on scroll events
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            updateOverlayPositions()
        }
        
        // Debounce rapid content changes
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            processContentChange(event)
        }, CONTENT_PROCESSING_DELAY_MS)
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        Timber.d("Window state changed: ${event.packageName}")
        
        // Clear overlays when switching apps
        if (event.packageName?.toString() !in SUPPORTED_PACKAGES) {
            clearAllOverlays()
        }
    }

    private fun processContentChange(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: return
        
        Timber.d("Processing content change for: $packageName")
        
        try {
            // Extract content nodes based on app type
            val contentNodes = when {
                SocialMediaDetector.isInstagram(packageName) -> {
                    Timber.d("ScrollGuard: Extracting Instagram content")
                    extractInstagramContent(rootNode)
                }
                SocialMediaDetector.isTikTok(packageName) -> {
                    Timber.d("ScrollGuard: Extracting TikTok content")
                    extractTikTokContent(rootNode)
                }
                SocialMediaDetector.isTwitter(packageName) -> {
                    Timber.d("ScrollGuard: Extracting Twitter content")
                    extractTwitterContent(rootNode)
                }
                SocialMediaDetector.isReddit(packageName) -> {
                    Timber.d("ScrollGuard: Extracting Reddit content")
                    extractRedditContent(rootNode)
                }
                else -> {
                    Timber.d("ScrollGuard: Extracting generic content")
                    extractGenericContent(rootNode)
                }
            }
            
            Timber.d("ScrollGuard: Found ${contentNodes.size} content nodes to analyze")
            
            // Process each content node
            contentNodes.forEach { node ->
                queueContentForAnalysis(node, packageName)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error processing content change")
            analyticsManager.logEvent("content_processing_error") {
                param("package_name", packageName)
                param("error_type", e.javaClass.simpleName)
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun extractInstagramContent(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val contentNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // Look for Instagram post containers
        AccessibilityNodeHelper.findNodesByViewId(rootNode, "android:id/text1")?.let { nodes ->
            contentNodes.addAll(nodes.filter { it.text?.isNotEmpty() == true })
        }
        
        // Look for caption text
        AccessibilityNodeHelper.findNodesByClassName(rootNode, "android.widget.TextView")?.let { nodes ->
            contentNodes.addAll(nodes.filter { 
                it.text?.isNotEmpty() == true && it.text.length > 10 
            })
        }
        
        return contentNodes
    }

    private fun extractTikTokContent(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val contentNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // TikTok video descriptions and captions
        AccessibilityNodeHelper.findNodesByClassName(rootNode, "android.widget.TextView")?.let { nodes ->
            contentNodes.addAll(nodes.filter { 
                it.text?.isNotEmpty() == true && 
                it.text.length > 5 &&
                !it.text.toString().matches(Regex("^[@#].*")) // Skip pure hashtags/mentions
            })
        }
        
        return contentNodes
    }

    private fun extractTwitterContent(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val contentNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // Twitter tweet text
        AccessibilityNodeHelper.findNodesByClassName(rootNode, "android.widget.TextView")?.let { nodes ->
            contentNodes.addAll(nodes.filter { 
                it.text?.isNotEmpty() == true && 
                it.text.length > 10 &&
                !it.isClickable // Avoid buttons and links
            })
        }
        
        return contentNodes
    }

    private fun extractRedditContent(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val contentNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // Reddit post titles and content
        AccessibilityNodeHelper.findNodesByClassName(rootNode, "android.widget.TextView")?.let { nodes ->
            contentNodes.addAll(nodes.filter { 
                it.text?.isNotEmpty() == true && 
                it.text.length > 15 // Longer threshold for Reddit
            })
        }
        
        return contentNodes
    }

    private fun extractGenericContent(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val contentNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // Generic text extraction for other apps
        AccessibilityNodeHelper.findNodesByClassName(rootNode, "android.widget.TextView")?.let { nodes ->
            contentNodes.addAll(nodes.filter { 
                it.text?.isNotEmpty() == true && 
                it.text.length > 10
            })
        }
        
        return contentNodes
    }

    private fun queueContentForAnalysis(node: AccessibilityNodeInfo, packageName: String) {
        val text = node.text?.toString() ?: return
        val contentHash = text.hashCode().toString()
        
        // Check cache first
        contentCache[contentHash]?.let { cachedAnalysis ->
            if (!cachedAnalysis.isProductive) {
                applyContentFilter(node, cachedAnalysis)
            }
            return
        }
        
        // Limit concurrent analyses
        if (activeAnalyses >= MAX_CONCURRENT_ANALYSES) {
            processingQueue.add(ContentProcessingTask(node, text, packageName, contentHash))
            return
        }
        
        // Process immediately
        processContent(node, text, packageName, contentHash)
    }

    private fun processContent(
        node: AccessibilityNodeInfo, 
        text: String, 
        packageName: String, 
        contentHash: String
    ) {
        activeAnalyses++
        
        processingScope.launch {
            try {
                val analysis = analyzeContent(text, packageName)
                
                // Cache result
                contentCache[contentHash] = analysis
                
                // Apply filter if content is unproductive
                if (!analysis.isProductive) {
                    withContext(Dispatchers.Main) {
                        applyContentFilter(node, analysis)
                    }
                }
                
                // Log analytics
                analyticsManager.logEvent("content_analyzed") {
                    param("package_name", packageName)
                    param("is_productive", analysis.isProductive)
                    param("confidence", analysis.confidence)
                    param("processing_time_ms", analysis.processingTimeMs)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error analyzing content")
            } finally {
                activeAnalyses--
                
                // Process queued items
                if (processingQueue.isNotEmpty()) {
                    val nextTask = processingQueue.removeAt(0)
                    processContent(nextTask.node, nextTask.text, nextTask.packageName, nextTask.contentHash)
                }
            }
        }
    }

    private suspend fun analyzeContent(text: String, packageName: String): ContentAnalysis {
        return withContext(Dispatchers.Default) {
            try {
                val result = llamaInferenceManager.classifyContent(text)
                
                ContentAnalysis(
                    content = text,
                    contentType = ContentType.fromPackageName(packageName),
                    isProductive = result.isProductive,
                    confidence = result.confidence,
                    reason = result.reason,
                    processingTimeMs = result.processingTimeMs,
                    timestamp = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Error in LLM analysis")
                
                // Fallback to simple heuristics
                val isProductive = !text.contains(Regex("(?i)(trending|viral|shocking|clickbait)"))
                
                ContentAnalysis(
                    content = text,
                    contentType = ContentType.fromPackageName(packageName),
                    isProductive = isProductive,
                    confidence = 0.5f,
                    reason = "fallback_heuristic",
                    processingTimeMs = 10,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private fun applyContentFilter(node: AccessibilityNodeInfo, analysis: ContentAnalysis) {
        try {
            // Create overlay to blur/hide content
            val overlay = createContentOverlay(analysis)
            
            // Position overlay over the content
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Position relative to the actual content bounds
                gravity = Gravity.TOP or Gravity.START
                x = bounds.right - 140 // Position near right edge of content
                y = bounds.top + 5 // Small offset from content top
            }
            
            // Add overlay to window
            windowManager.addView(overlay, layoutParams)
            activeOverlays[node] = OverlayInfo(overlay, layoutParams, android.graphics.Rect(bounds))
            
            // Auto-remove overlay after some time
            handler.postDelayed({
                removeOverlay(node)
            }, 30000) // 30 seconds
            
            Timber.d("Applied content filter: ${analysis.reason}")
            
        } catch (e: Exception) {
            Timber.e(e, "Error applying content filter")
        }
    }

    private fun createContentOverlay(analysis: ContentAnalysis): View {
        val overlay = LayoutInflater.from(this).inflate(R.layout.content_filter_overlay, null)
        
        // Configure overlay based on analysis
        overlay.alpha = when {
            analysis.confidence > 0.8f -> 0.9f
            analysis.confidence > 0.6f -> 0.7f
            else -> 0.5f
        }
        
        overlay.setBackgroundColor(
            ContextCompat.getColor(this, R.color.blur_overlay)
        )
        
        return overlay
    }

    private fun removeOverlay(node: AccessibilityNodeInfo) {
        activeOverlays.remove(node)?.let { overlayInfo ->
            try {
                windowManager.removeView(overlayInfo.overlay)
            } catch (e: Exception) {
                Timber.w(e, "Error removing overlay")
            }
        }
    }

    private fun updateOverlayPositions() {
        try {
            val overlaysToUpdate = mutableListOf<Pair<AccessibilityNodeInfo, OverlayInfo>>()
            
            // Collect overlays that need position updates
            activeOverlays.forEach { (node, overlayInfo) ->
                val currentBounds = android.graphics.Rect()
                node.getBoundsInScreen(currentBounds)
                
                // Check if the content has moved
                if (currentBounds != overlayInfo.contentBounds) {
                    overlaysToUpdate.add(node to overlayInfo)
                }
            }
            
            // Update positions for moved content
            overlaysToUpdate.forEach { (node, overlayInfo) ->
                val newBounds = android.graphics.Rect()
                node.getBoundsInScreen(newBounds)
                
                // Update layout parameters
                overlayInfo.layoutParams.x = newBounds.right - 140
                overlayInfo.layoutParams.y = newBounds.top + 5
                
                // Update the overlay position
                windowManager.updateViewLayout(overlayInfo.overlay, overlayInfo.layoutParams)
                
                // Update stored bounds
                overlayInfo.contentBounds.set(newBounds)
                
                Timber.v("Updated overlay position for content at ${newBounds.top}")
            }
            
        } catch (e: Exception) {
            Timber.w(e, "Error updating overlay positions")
        }
    }

    private fun clearAllOverlays() {
        activeOverlays.values.forEach { overlayInfo ->
            try {
                windowManager.removeView(overlayInfo.overlay)
            } catch (e: Exception) {
                Timber.w(e, "Error removing overlay during cleanup")
            }
        }
        activeOverlays.clear()
    }

    private suspend fun initializeLLM() {
        try {
            if (!llamaInferenceManager.isInitialized()) {
                llamaInferenceManager.initialize()
            }
            
            if (!llamaInferenceManager.isModelLoaded()) {
                // Model loading will be handled by LlamaInferenceManager
                // when first classification is requested
                Timber.d("LLM inference manager ready")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LLM")
            analyticsManager.logEvent("llm_init_error") {
                param("error_type", e.javaClass.simpleName)
                param("error_message", e.message ?: "unknown")
            }
        }
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, LLMInferenceService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun cleanup() {
        clearAllOverlays()
        contentCache.clear()
        processingQueue.clear()
        
        serviceScope.cancel()
        processingScope.cancel()
        
        handler.removeCallbacksAndMessages(null)
    }

    private data class ContentProcessingTask(
        val node: AccessibilityNodeInfo,
        val text: String,
        val packageName: String,
        val contentHash: String
    )
}