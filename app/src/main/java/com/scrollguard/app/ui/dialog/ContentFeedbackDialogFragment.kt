package com.scrollguard.app.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.databinding.DialogContentFeedbackBinding
import com.scrollguard.app.data.model.UserFeedback
import com.scrollguard.app.data.model.FeedbackType
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog fragment for collecting user feedback on content filtering decisions.
 * Helps improve AI accuracy through user corrections.
 */
class ContentFeedbackDialogFragment : DialogFragment() {

    private var _binding: DialogContentFeedbackBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var app: ScrollGuardApplication
    private var contentAnalysisId: Long = -1L
    private var onFeedbackSubmitted: (() -> Unit)? = null

    companion object {
        private const val ARG_ANALYSIS_ID = "analysis_id"
        
        fun newInstance(
            analysisId: Long, 
            onSubmitted: (() -> Unit)? = null
        ): ContentFeedbackDialogFragment {
            return ContentFeedbackDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ANALYSIS_ID, analysisId)
                }
                onFeedbackSubmitted = onSubmitted
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogContentFeedbackBinding.inflate(layoutInflater)
        app = requireActivity().application as ScrollGuardApplication
        
        contentAnalysisId = arguments?.getLong(ARG_ANALYSIS_ID) ?: -1L
        
        setupUI()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupUI() {
        binding.submitButton.setOnClickListener {
            submitFeedback()
        }
        
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun submitFeedback() {
        val feedbackType = when (binding.feedbackRadioGroup.checkedRadioButtonId) {
            R.id.feedback_correct -> FeedbackType.CORRECT_FILTER
            R.id.feedback_incorrect -> FeedbackType.INCORRECT_FILTER
            R.id.feedback_should_filter -> FeedbackType.SHOULD_FILTER
            else -> FeedbackType.CORRECT_FILTER
        }
        
        val comment = binding.feedbackComment.text?.toString()?.trim()
        
        lifecycleScope.launch {
            try {
                val feedback = UserFeedback(
                    feedbackType = feedbackType,
                    comment = comment,
                    timestamp = System.currentTimeMillis()
                )
                
                // Save feedback to database
                app.contentRepository.updateUserFeedback(contentAnalysisId, feedback)
                
                // Log analytics event
                app.analyticsManager.logEvent("user_feedback_submitted") {
                    param("feedback_type", feedbackType.name)
                    param("has_comment", !comment.isNullOrEmpty())
                }
                
                // Show success message
                activity?.let { activity ->
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.feedback_submitted,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                
                onFeedbackSubmitted?.invoke()
                dismiss()
                
            } catch (e: Exception) {
                Timber.e(e, "Error submitting feedback")
                
                // Show error message
                activity?.let { activity ->
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.error_generic,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}