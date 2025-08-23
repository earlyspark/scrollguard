package com.scrollguard.app.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.databinding.DialogModelDownloadBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog fragment for model download with progress tracking.
 * Shows download progress and handles model initialization.
 */
class ModelDownloadDialogFragment : DialogFragment() {

    private var _binding: DialogModelDownloadBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var app: ScrollGuardApplication
    private var onDownloadComplete: (() -> Unit)? = null
    private var isDownloading = false

    companion object {
        fun newInstance(onComplete: (() -> Unit)? = null): ModelDownloadDialogFragment {
            return ModelDownloadDialogFragment().apply {
                onDownloadComplete = onComplete
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogModelDownloadBinding.inflate(layoutInflater)
        app = requireActivity().application as ScrollGuardApplication
        
        setupUI()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(!isDownloading)
            .create()
    }

    private fun setupUI() {
        binding.downloadButton.setOnClickListener {
            if (!isDownloading) {
                startDownload()
            }
        }
        
        binding.cancelButton.setOnClickListener {
            if (!isDownloading) {
                dismiss()
            } else {
                // TODO: Cancel download if in progress
                dismiss()
            }
        }
    }

    private fun startDownload() {
        isDownloading = true
        dialog?.setCancelable(false)
        
        // Show progress section
        binding.progressSection.visibility = View.VISIBLE
        binding.downloadButton.isEnabled = false
        binding.downloadButton.text = getString(R.string.downloading)
        
        // Update progress to indeterminate first
        binding.progressBar.isIndeterminate = true
        binding.progressText.text = getString(R.string.downloading)
        
        lifecycleScope.launch {
            try {
                // Get the recommended model to download
                val modelDownloadManager = app.modelDownloadManager
                val recommendedModel = modelDownloadManager.getRecommendedModel()
                
                // Check if model is already downloaded
                if (modelDownloadManager.isModelDownloaded(recommendedModel)) {
                    updateProgress("Model already exists, loading...", 50)
                    val loaded = app.llamaInferenceManager.loadModel()
                    if (loaded) {
                        updateProgress(getString(R.string.download_complete), 100)
                        onDownloadSuccess()
                    } else {
                        showError(getString(R.string.error_model_load))
                    }
                    return@launch
                }
                
                // Start actual download
                updateProgress("Starting download...", 5)
                val downloadSuccess = modelDownloadManager.downloadModel(recommendedModel)
                
                if (downloadSuccess) {
                    // Initialize inference manager after download
                    updateProgress("Initializing AI engine...", 90)
                    val initialized = app.llamaInferenceManager.initialize()
                    
                    if (initialized) {
                        val loaded = app.llamaInferenceManager.loadModel()
                        if (loaded) {
                            updateProgress(getString(R.string.download_complete), 100)
                            onDownloadSuccess()
                        } else {
                            showError(getString(R.string.error_model_load))
                        }
                    } else {
                        showError("Failed to initialize AI engine")
                    }
                } else {
                    showError(getString(R.string.download_failed))
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error downloading model")
                showError(getString(R.string.download_failed))
            }
        }
    }

    private fun updateProgress(text: String, progress: Int) {
        binding.progressText.text = text
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = progress
        binding.progressPercentage.text = "$progress%"
    }

    private fun showError(message: String) {
        binding.progressText.text = message
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        binding.progressPercentage.text = "0%"
        
        binding.downloadButton.isEnabled = true
        binding.downloadButton.text = getString(R.string.retry_download)
        dialog?.setCancelable(true)
        isDownloading = false
    }

    private fun onDownloadSuccess() {
        // Hide progress and show success
        binding.progressText.text = getString(R.string.download_complete)
        binding.downloadButton.text = getString(R.string.close)
        binding.downloadButton.isEnabled = true
        
        binding.downloadButton.setOnClickListener {
            onDownloadComplete?.invoke()
            dismiss()
        }
        
        dialog?.setCancelable(true)
        isDownloading = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}