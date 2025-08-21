package com.scrollguard.app.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.scrollguard.app.ScrollGuardApplication
import timber.log.Timber

/**
 * Launcher activity that handles initial app routing.
 * Determines whether to show onboarding or main activity based on app state.
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // No UI needed - just routing logic
        routeToAppropriateActivity()
    }

    private fun routeToAppropriateActivity() {
        try {
            val app = application as ScrollGuardApplication
            
            when {
                app.preferencesRepository.isFirstLaunch() -> {
                    // First time user - show onboarding
                    startActivity(Intent(this, OnboardingActivity::class.java))
                }
                !app.preferencesRepository.isOnboardingCompleted() -> {
                    // Onboarding not completed - continue onboarding
                    startActivity(Intent(this, OnboardingActivity::class.java))
                }
                else -> {
                    // Normal app flow - show main activity
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
            
            finish()
            
        } catch (e: Exception) {
            Timber.e(e, "Error in launcher routing")
            // Fallback to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}