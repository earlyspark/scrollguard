package com.scrollguard.app

import android.app.Application
import androidx.room.Room
import com.scrollguard.app.data.database.ScrollGuardDatabase
import com.scrollguard.app.data.repository.ContentRepository
import com.scrollguard.app.data.repository.PreferencesRepository
import com.scrollguard.app.service.analytics.AnalyticsManager
import com.scrollguard.app.service.llm.LlamaInferenceManager
import com.scrollguard.app.service.llm.ModelDownloadManager
import com.scrollguard.app.util.ErrorHandler
import com.scrollguard.app.ui.util.NotificationHelper
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main application class for ScrollGuard.
 * Initializes core services and provides dependency injection.
 */
class ScrollGuardApplication : Application() {

    // Application scope for background operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Database
    val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            ScrollGuardDatabase::class.java,
            "scrollguard_database"
        )
        .fallbackToDestructiveMigration() // TODO: Proper migrations for production
        .build()
    }

    // Repositories
    val contentRepository by lazy {
        ContentRepository(
            contentDao = database.contentDao(),
            sessionDao = database.sessionDao()
        )
    }

    val preferencesRepository by lazy {
        PreferencesRepository(
            context = applicationContext,
            preferencesDao = database.preferencesDao()
        )
    }

    // Core Services
    val analyticsManager by lazy {
        AnalyticsManager(applicationContext)
    }

    val llamaInferenceManager by lazy {
        LlamaInferenceManager(applicationContext)
    }

    val modelDownloadManager by lazy {
        ModelDownloadManager(applicationContext)
    }

    // Error Handler
    val errorHandler by lazy {
        ErrorHandler(applicationContext, analyticsManager)
    }

    override fun onCreate() {
        super.onCreate()
        
        // Set up global exception handler (avoid recursion by delegating to previous handler)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            errorHandler.reportCriticalError(exception, "UncaughtException", thread.name)
            // Delegate to the original handler so the system can crash/report properly
            previousHandler?.uncaughtException(thread, exception)
        }
        
        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("ScrollGuard Application starting...")
        
        // Initialize core services
        initializeServices()
        
        Timber.d("ScrollGuard Application initialized")
    }

    private fun initializeServices() {
        // Create notification channels
        NotificationHelper.createNotificationChannels(applicationContext)
        
        // Pre-load analytics manager to handle consent
        analyticsManager.initialize()
        
        // Initialize LLM manager but don't load model yet
        // Model will be loaded when accessibility service starts
        applicationScope.launch {
            llamaInferenceManager.initialize()
        }
        
        // Log application start
        analyticsManager.logEvent("app_start") {
            param("version_name", BuildConfig.VERSION_NAME)
            param("version_code", BuildConfig.VERSION_CODE)
            param("debug_build", BuildConfig.DEBUG)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Timber.d("ScrollGuard Application terminating...")
        
        // Cleanup services
        llamaInferenceManager.cleanup()
        analyticsManager.cleanup()
    }
}
