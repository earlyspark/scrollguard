package com.scrollguard.app.ui.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private var accessibilityDialogShown = false
    
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Snackbar.make(
                    binding.root,
                    R.string.permission_notifications_denied,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

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
        // When returning from settings, check if accessibility is now enabled
        if (AccessibilityHelper.isServiceEnabled(this)) {
            accessibilityDialogShown = false
        }
        ensureNotificationPermission()
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
        val isEnabled = AccessibilityHelper.isServiceEnabled(this)
        Timber.d("ScrollGuard: Accessibility service enabled: $isEnabled, dialog shown: $accessibilityDialogShown")
        
        // Check accessibility permission (but don't show dialog repeatedly)
        if (!isEnabled && !accessibilityDialogShown) {
            Timber.d("ScrollGuard: Showing accessibility dialog")
            showAccessibilityPermissionDialog()
        } else if (isEnabled) {
            // Reset flag if accessibility is now enabled
            accessibilityDialogShown = false
            Timber.d("ScrollGuard: Accessibility enabled, resetting dialog flag")
        }

        // Update model status
        updateModelStatus()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
        accessibilityDialogShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_accessibility_title)
            .setMessage(R.string.permission_accessibility_message)
            .setPositiveButton(R.string.permission_accessibility_button) { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                accessibilityDialogShown = false
            }
            .setCancelable(true)
            .setOnCancelListener {
                accessibilityDialogShown = false
            }
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
