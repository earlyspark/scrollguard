package com.scrollguard.app.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.scrollguard.app.R
import com.scrollguard.app.ScrollGuardApplication
import com.scrollguard.app.databinding.ActivityOnboardingBinding
import com.scrollguard.app.ui.fragment.OnboardingPageFragment
import timber.log.Timber

/**
 * Onboarding activity for first-time setup and permissions.
 * Guides users through privacy explanation, permissions, and initial configuration.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var app: ScrollGuardApplication
    private lateinit var onboardingAdapter: OnboardingPagerAdapter

    private val onboardingPages = listOf(
        OnboardingPage(
            title = R.string.onboarding_step_1_title,
            description = R.string.onboarding_step_1_description,
            imageRes = R.drawable.ic_shield
        ),
        OnboardingPage(
            title = R.string.onboarding_step_2_title,
            description = R.string.onboarding_step_2_description,
            imageRes = R.drawable.ic_settings
        ),
        OnboardingPage(
            title = R.string.onboarding_step_3_title,
            description = R.string.onboarding_step_3_description,
            imageRes = R.drawable.ic_statistics
        ),
        OnboardingPage(
            title = R.string.onboarding_step_4_title,
            description = R.string.onboarding_step_4_description,
            imageRes = R.drawable.ic_download
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        app = application as ScrollGuardApplication
        
        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingPagerAdapter(this)
        binding.onboardingViewpager.adapter = onboardingAdapter
        
        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.onboardingIndicator, binding.onboardingViewpager) { _, _ ->
            // Empty implementation - we just want the dots
        }.attach()
        
        // Listen for page changes
        binding.onboardingViewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtons(position)
            }
        })
        
        updateButtons(0)
    }

    private fun setupButtons() {
        binding.skipButton.setOnClickListener {
            finishOnboarding()
        }
        
        binding.continueButton.setOnClickListener {
            val currentPosition = binding.onboardingViewpager.currentItem
            
            if (currentPosition < onboardingPages.size - 1) {
                // Go to next page
                binding.onboardingViewpager.currentItem = currentPosition + 1
            } else {
                // Finish onboarding
                finishOnboarding()
            }
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == onboardingPages.size - 1
        
        binding.skipButton.text = if (isLastPage) {
            getString(R.string.skip_button)
        } else {
            getString(R.string.skip_button)
        }
        
        binding.continueButton.text = if (isLastPage) {
            getString(R.string.finish_button)
        } else {
            getString(R.string.continue_button)
        }
    }

    private fun finishOnboarding() {
        try {
            // Mark onboarding as completed
            app.preferencesRepository.setOnboardingCompleted()
            
            // Start main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            
        } catch (e: Exception) {
            Timber.e(e, "Error finishing onboarding")
            // Still proceed to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private inner class OnboardingPagerAdapter(activity: OnboardingActivity) : FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = onboardingPages.size

        override fun createFragment(position: Int): Fragment {
            val page = onboardingPages[position]
            return OnboardingPageFragment.newInstance(
                title = getString(page.title),
                description = getString(page.description),
                imageRes = page.imageRes
            )
        }
    }

    data class OnboardingPage(
        val title: Int,
        val description: Int,
        val imageRes: Int
    )
}