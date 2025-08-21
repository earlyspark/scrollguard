package com.scrollguard.app.ui.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.databinding.ActivityMainBinding
import com.scrollguard.app.service.ContentFilterService
import com.scrollguard.app.ui.util.AccessibilityHelper
import com.scrollguard.app.ui.dialog.ModelDownloadDialogFragment
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main activity for ScrollGuard application.
 * Provides dashboard interface with service controls and statistics.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var app: ScrollGuardApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        app = application as ScrollGuardApplication
        
        setupToolbar()
        setupUI()
        observeData()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        updateServiceStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.main_title)
            setDisplayShowTitleEnabled(true)
        }
    }

    private fun setupUI() {
        // Filtering toggle
        binding.filteringSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                try {
                    app.preferencesRepository.setFilteringEnabled(isChecked)
                    updateServiceStatus()
                    
                    val message = if (isChecked) {
                        getString(R.string.filtering_enabled)
                    } else {
                        getString(R.string.filtering_disabled)
                    }
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error toggling filtering")
                    binding.filteringSwitch.isChecked = !isChecked // Revert
                    Snackbar.make(binding.root, R.string.error_generic, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        // Settings button
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Statistics button
        binding.statisticsButton.setOnClickListener {
            // TODO: Implement statistics activity
            Snackbar.make(binding.root, "Statistics coming soon!", Snackbar.LENGTH_SHORT).show()
        }

        // Model download button
        binding.downloadModelButton.setOnClickListener {
            showModelDownloadDialog()
        }
    }

    private fun observeData() {
        // Observe preferences for filtering status
        lifecycleScope.launch {
            app.preferencesRepository.getUserPreferences().collect { preferences ->
                binding.filteringSwitch.isChecked = preferences.filteringEnabled
                updateServiceStatus()
            }
        }

        // Update statistics
        lifecycleScope.launch {
            try {
                val stats = app.contentRepository.getContentStatistics()
                binding.contentFilteredCount.text = stats.contentFiltered.toString()
                binding.timeSaved.text = formatTimeSaved(stats.timeSavedEstimate)
            } catch (e: Exception) {
                Timber.e(e, "Error loading statistics")
            }
        }
    }

    private fun updateUI() {
        // Check accessibility permission
        if (!AccessibilityHelper.isServiceEnabled(this)) {
            showAccessibilityPermissionDialog()
        }

        // Update model status
        updateModelStatus()
    }

    private fun updateServiceStatus() {
        lifecycleScope.launch {
            val isFilteringEnabled = app.preferencesRepository.isFilteringEnabled()
            val isAccessibilityEnabled = AccessibilityHelper.isServiceEnabled(this@MainActivity)
            
            when {
                !isAccessibilityEnabled -> {
                    binding.statusText.text = getString(R.string.permission_required)
                    binding.statusIcon.setImageResource(R.drawable.ic_shield)
                    binding.statusIcon.alpha = 0.5f
                }
                isFilteringEnabled -> {
                    binding.statusText.text = getString(R.string.filtering_enabled)
                    binding.statusIcon.setImageResource(R.drawable.ic_shield)
                    binding.statusIcon.alpha = 1.0f
                }
                else -> {
                    binding.statusText.text = getString(R.string.filtering_disabled)
                    binding.statusIcon.setImageResource(R.drawable.ic_shield)
                    binding.statusIcon.alpha = 0.5f
                }
            }
        }
    }

    private fun updateModelStatus() {
        lifecycleScope.launch {
            try {
                val isModelLoaded = app.llamaInferenceManager.isModelLoaded()
                
                if (isModelLoaded) {
                    binding.modelStatusText.text = getString(R.string.model_loaded)
                    binding.downloadModelButton.text = getString(R.string.update_model)
                } else {
                    binding.modelStatusText.text = getString(R.string.model_not_loaded)
                    binding.downloadModelButton.text = getString(R.string.download_model)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking model status")
                binding.modelStatusText.text = getString(R.string.model_not_loaded)
            }
        }
    }

    private fun showAccessibilityPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_accessibility_title)
            .setMessage(R.string.permission_accessibility_message)
            .setPositiveButton(R.string.permission_accessibility_button) { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun showModelDownloadDialog() {
        val dialog = ModelDownloadDialogFragment.newInstance {
            updateModelStatus()
            Snackbar.make(binding.root, R.string.download_complete, Snackbar.LENGTH_LONG).show()
        }
        dialog.show(supportFragmentManager, "model_download")
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error opening accessibility settings")
            Snackbar.make(binding.root, R.string.error_accessibility_permission, Snackbar.LENGTH_LONG).show()
        }
    }



    private fun formatTimeSaved(timeMs: Long): String {
        val minutes = timeMs / (1000 * 60)
        return getString(R.string.minutes_format, minutes.toInt())
    }
}