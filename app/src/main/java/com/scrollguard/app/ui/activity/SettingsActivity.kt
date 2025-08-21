package com.scrollguard.app.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.databinding.ActivitySettingsBinding
import com.scrollguard.app.data.model.FilterStrictness
import com.scrollguard.app.ui.adapter.AppSelectionAdapter
import com.scrollguard.app.ui.model.AppInfo
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Settings activity for app configuration and preferences.
 * Allows users to adjust filter sensitivity, select apps, and manage preferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: ScrollGuardApplication
    private lateinit var appSelectionAdapter: AppSelectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        app = application as ScrollGuardApplication
        
        setupToolbar()
        setupUI()
        loadSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupUI() {
        setupSensitivitySlider()
        setupAppsRecyclerView()
        setupAnalyticsSwitch()
    }

    private fun setupSensitivitySlider() {
        binding.sensitivitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                lifecycleScope.launch {
                    try {
                        val strictness = when (value.toInt()) {
                            0 -> FilterStrictness.LOW
                            1 -> FilterStrictness.MEDIUM
                            2 -> FilterStrictness.HIGH
                            else -> FilterStrictness.MEDIUM
                        }
                        
                        app.preferencesRepository.setFilterStrictness(strictness)
                        Timber.d("Updated filter strictness to: $strictness")
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating filter strictness")
                    }
                }
            }
        }
    }

    private fun setupAppsRecyclerView() {
        appSelectionAdapter = AppSelectionAdapter { appInfo, isEnabled ->
            lifecycleScope.launch {
                try {
                    val enabledApps = app.preferencesRepository.getEnabledApps().toMutableSet()
                    
                    if (isEnabled) {
                        enabledApps.add(appInfo.packageName)
                    } else {
                        enabledApps.remove(appInfo.packageName)
                    }
                    
                    app.preferencesRepository.setEnabledApps(enabledApps)
                    Timber.d("Updated enabled apps: ${appInfo.packageName} -> $isEnabled")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error updating enabled apps")
                }
            }
        }
        
        binding.appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = appSelectionAdapter
        }
    }

    private fun setupAnalyticsSwitch() {
        binding.analyticsSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                try {
                    app.preferencesRepository.setAnalyticsEnabled(isChecked)
                    Timber.d("Updated analytics consent: $isChecked")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error updating analytics consent")
                }
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                // Load current preferences
                val preferences = app.preferencesRepository.getUserPreferencesOnce()
                
                // Update sensitivity slider
                val sliderValue = when (preferences.filterStrictness) {
                    FilterStrictness.LOW -> 0f
                    FilterStrictness.MEDIUM -> 1f
                    FilterStrictness.HIGH -> 2f
                    FilterStrictness.CUSTOM -> 3f
                }
                binding.sensitivitySlider.value = sliderValue
                
                // Update analytics switch
                binding.analyticsSwitch.isChecked = preferences.analyticsEnabled
                
                // Load app list
                loadAppList(preferences.enabledApps)
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading settings")
            }
        }
    }

    private suspend fun loadAppList(enabledApps: Set<String>) {
        try {
            // Create list of supported social media apps
            val supportedApps = listOf(
                AppInfo(
                    packageName = "com.instagram.android",
                    appName = getString(R.string.instagram),
                    iconRes = R.drawable.ic_launcher_foreground // Placeholder - would use actual app icons
                ),
                AppInfo(
                    packageName = "com.zhiliaoapp.musically", // TikTok
                    appName = getString(R.string.tiktok),
                    iconRes = R.drawable.ic_launcher_foreground
                ),
                AppInfo(
                    packageName = "com.twitter.android",
                    appName = getString(R.string.twitter),
                    iconRes = R.drawable.ic_launcher_foreground
                ),
                AppInfo(
                    packageName = "com.reddit.frontpage",
                    appName = getString(R.string.reddit),
                    iconRes = R.drawable.ic_launcher_foreground
                ),
                AppInfo(
                    packageName = "com.youtube.android",
                    appName = getString(R.string.youtube),
                    iconRes = R.drawable.ic_launcher_foreground
                ),
                AppInfo(
                    packageName = "com.facebook.katana",
                    appName = getString(R.string.facebook),
                    iconRes = R.drawable.ic_launcher_foreground
                )
            )
            
            // Map enabled status
            val appInfoWithStatus = supportedApps.map { appInfo ->
                appInfo.copy(isEnabled = enabledApps.contains(appInfo.packageName))
            }
            
            // Update adapter
            runOnUiThread {
                appSelectionAdapter.submitList(appInfoWithStatus)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error loading app list")
        }
    }
}