package com.maxproductivity.app

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.maxproductivity.app.blocking.AccessibilityBlockStrategy
import com.maxproductivity.app.blocking.AppSuspensionManager
import com.maxproductivity.app.blocking.BlockingStrategy
import com.maxproductivity.app.blocking.DeviceOwnerSuspendStrategy
import com.maxproductivity.app.data.ALWAYS_BLOCKED_PACKAGES
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FocusAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: AppPreferencesRepository
    private lateinit var suspensionManager: AppSuspensionManager

    @Volatile
    private var preferences: AppPreferences = AppPreferences()

    override fun onCreate() {
        super.onCreate()
        repository = MaxProductivityApp.get(this).preferencesRepository
        suspensionManager = AppSuspensionManager(AccessibilityBlockStrategy.fromService(this))

        serviceScope.launch {
            repository.preferencesFlow.collect { prefs ->
                preferences = prefs
                updateBlockingStrategy(prefs)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, YouTubeTimerService::class.java))
        } else {
            startService(Intent(this, YouTubeTimerService::class.java))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !preferences.blockingEnabled) return

        val packageName = event.packageName?.toString() ?: return
        YouTubeTrackingState.foregroundPackage = packageName

        when {
            packageName == AppPreferencesRepository.YOUTUBE_PACKAGE -> handleYouTube(event)
            ALWAYS_BLOCKED_PACKAGES.contains(packageName) -> blockApp(packageName, preferences.reminderText)
            preferences.blockedPackages.contains(packageName) -> blockApp(packageName, preferences.reminderText)
            checkAppTimerLimit(packageName) -> { /* Handled in checkAppTimerLimit */ }
            else -> {
                if (YouTubeTrackingState.isYouTubeForeground) {
                    YouTubeTrackingState.isYouTubeForeground = false
                    YouTubeTrackingState.isShortsActive = false
                }
            }
        }
    }

    private fun checkAppTimerLimit(packageName: String): Boolean {
        val limitMinutes = preferences.timerLimits[packageName] ?: return false
        val usageSeconds = preferences.appUsageSeconds[packageName] ?: 0L
        if (usageSeconds >= limitMinutes * 60L) {
            blockApp(packageName, getString(R.string.block_overlay_default_message))
            return true
        }
        return false
    }

    private fun handleYouTube(event: AccessibilityEvent) {
        YouTubeTrackingState.isYouTubeForeground = true

        if (YouTubeTrackingState.isYouTubeLimitReached) {
            blockYouTube(getString(R.string.youtube_limit_reached))
            return
        }

        val root = rootInActiveWindow
        val shortsDetected = ShortsDetector.isShortsScreen(root)
        root?.recycle()

        YouTubeTrackingState.isShortsActive = shortsDetected

        if (shortsDetected) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            blockYouTube(getString(R.string.shorts_blocked_message))
        }
    }

    private fun blockApp(packageName: String, message: String) {
        if (preferences.blockingStrategy == BlockingStrategy.DEVICE_OWNER) return
        suspensionManager.blockPackage(this, packageName, message)
    }

    private fun blockYouTube(message: String) {
        suspensionManager.blockPackage(this, AppPreferencesRepository.YOUTUBE_PACKAGE, message)
    }

    private fun updateBlockingStrategy(prefs: AppPreferences) {
        val strategy = when (prefs.blockingStrategy) {
            BlockingStrategy.ACCESSIBILITY -> AccessibilityBlockStrategy.fromService(this)
            BlockingStrategy.DEVICE_OWNER -> DeviceOwnerSuspendStrategy()
        }
        suspensionManager.setStrategy(strategy)

        if (prefs.blockingStrategy == BlockingStrategy.DEVICE_OWNER) {
            // Combine manual blocked packages with ALWAYS_BLOCKED_PACKAGES
            val allBlocked = prefs.blockedPackages + ALWAYS_BLOCKED_PACKAGES
            suspensionManager.updateSuspendedPackages(this, allBlocked, suspend = true)
        } else {
            val allBlocked = prefs.blockedPackages + ALWAYS_BLOCKED_PACKAGES
            DeviceOwnerSuspendStrategy().updateSuspendedPackages(this, allBlocked, suspend = false)
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: FocusAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }
}
