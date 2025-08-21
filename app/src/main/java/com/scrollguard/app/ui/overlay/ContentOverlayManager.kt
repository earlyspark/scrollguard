package com.scrollguard.app.ui.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.scrollguard.app.databinding.ContentFilterOverlayBinding
import com.scrollguard.app.ui.dialog.ContentFeedbackDialogFragment
import timber.log.Timber

/**
 * Manages content filter overlays for hiding unproductive content.
 * Creates and manages overlay views that appear over filtered content.
 */
class ContentOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeOverlays = mutableMapOf<String, OverlayInfo>()

    data class OverlayInfo(
        val view: View,
        val binding: ContentFilterOverlayBinding,
        val params: WindowManager.LayoutParams,
        val analysisId: Long
    )

    /**
     * Show overlay for filtered content
     */
    fun showContentOverlay(
        contentId: String,
        reason: String,
        analysisId: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        try {
            // Remove existing overlay if present
            hideContentOverlay(contentId)

            val binding = ContentFilterOverlayBinding.inflate(LayoutInflater.from(context))
            
            // Set overlay content
            binding.filterReason.text = reason
            
            // Set up feedback buttons
            binding.feedbackYesButton.setOnClickListener {
                provideFeedback(analysisId, isPositive = true)
                hideContentOverlay(contentId)
            }
            
            binding.feedbackNoButton.setOnClickListener {
                provideFeedback(analysisId, isPositive = false)
                hideContentOverlay(contentId)
            }
            
            // Set up show content button
            binding.showContentButton.setOnClickListener {
                hideContentOverlay(contentId)
                // TODO: Implement content showing logic
            }

            // Create window parameters
            val params = WindowManager.LayoutParams().apply {
                this.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                this.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                this.format = android.graphics.PixelFormat.TRANSLUCENT
                this.width = width
                this.height = height
                this.x = x
                this.y = y
                this.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
            }

            // Add overlay to window
            windowManager.addView(binding.root, params)
            
            // Store overlay info
            activeOverlays[contentId] = OverlayInfo(binding.root, binding, params, analysisId)
            
            Timber.d("Content overlay shown for: $contentId")

        } catch (e: Exception) {
            Timber.e(e, "Error showing content overlay")
        }
    }

    /**
     * Hide content overlay
     */
    fun hideContentOverlay(contentId: String) {
        try {
            activeOverlays[contentId]?.let { overlayInfo ->
                windowManager.removeView(overlayInfo.view)
                activeOverlays.remove(contentId)
                Timber.d("Content overlay hidden for: $contentId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error hiding content overlay")
        }
    }

    /**
     * Hide all active overlays
     */
    fun hideAllOverlays() {
        try {
            activeOverlays.values.forEach { overlayInfo ->
                windowManager.removeView(overlayInfo.view)
            }
            activeOverlays.clear()
            Timber.d("All content overlays hidden")
        } catch (e: Exception) {
            Timber.e(e, "Error hiding all overlays")
        }
    }

    /**
     * Check if system alert window permission is granted
     */
    fun hasOverlayPermission(): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }

    /**
     * Provide quick feedback on filtering decision
     */
    private fun provideFeedback(analysisId: Long, isPositive: Boolean) {
        try {
            if (context is FragmentActivity) {
                val dialog = ContentFeedbackDialogFragment.newInstance(analysisId) {
                    Timber.d("Feedback submitted for analysis: $analysisId")
                }
                dialog.show(context.supportFragmentManager, "content_feedback")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing feedback")
        }
    }

    /**
     * Get number of active overlays
     */
    fun getActiveOverlayCount(): Int = activeOverlays.size

    /**
     * Check if overlay exists for content
     */
    fun hasOverlay(contentId: String): Boolean = activeOverlays.containsKey(contentId)
}