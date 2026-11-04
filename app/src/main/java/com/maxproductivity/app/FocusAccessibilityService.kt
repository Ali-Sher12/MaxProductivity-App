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
import com.maxproductivity.app.data.LIMITED_SOCIAL_PACKAGES
import com.maxproductivity.app.data.LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES
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

    @Volatile
    private var lastShortsDetected: Boolean = false

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
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // IMPORTANT: only TYPE_WINDOW_STATE_CHANGED reliably means "the foreground app switched".
        // TYPE_WINDOW_CONTENT_CHANGED can be dispatched by windows that are NOT in the foreground
        // (status bar clock ticks, notification icon updates, IME, other background windows
        // redrawing) even while YouTube is still the actual foreground app. Previously we treated
        // every event the same way, so any unrelated content-changed event from another package
        // would call clearYouTubeForeground() mid-session and silently drop timer ticks until the
        // next YouTube window-state event arrived. That's why usage was undercounted, and only
        // intermittently, depending on how much unrelated system/background activity happened to
        // fire during a session.
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(packageName)
        }
    }

    private fun handleWindowStateChanged(packageName: String) {
        YouTubeTrackingState.updateForeground(packageName)

        if (packageName == AppPreferencesRepository.YOUTUBE_PACKAGE) {
            updateYouTubeTrackingState()
            if (preferences.blockingEnabled) {
                handleYouTubeBlocking()
            }
            return
        }

        YouTubeTrackingState.clearYouTubeForeground()
        lastShortsDetected = false

        // Do not intercept or clear session when our own UI (TimeNoticeActivity / BlockOverlayActivity) is shown
        if (packageName == this.packageName) {
            return
        }

        // Switching to a different app clears the previously acknowledged session
        if (packageName != acknowledgedPackageSession) {
            acknowledgedPackageSession = null
        }

        if (!preferences.blockingEnabled) return

        // Limited social media apps (Instagram, TikTok, Twitter/X, Reddit, Snapchat)
        if (LIMITED_SOCIAL_PACKAGES.contains(packageName)) {
            val usageSeconds = preferences.appUsageSeconds[packageName] ?: 0L
            val limitMinutes = preferences.getEffectiveLimitMinutes(packageName) ?: LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES
            val limitSeconds = limitMinutes * 60L
            if (usageSeconds >= limitSeconds) {
                blockApp(packageName, "Daily limit reached for this app (${limitMinutes}m).")
                return
            }

            if (acknowledgedPackageSession != packageName) {
                val remainingSeconds = maxOf(1L, limitSeconds - usageSeconds)
                startActivity(TimeNoticeActivity.createIntent(this, packageName, remainingSeconds, limitMinutes))
                return
            }
            return
        }

        when {
            ALWAYS_BLOCKED_PACKAGES.contains(packageName) -> blockApp(packageName, preferences.reminderText)
            preferences.blockedPackages.contains(packageName) -> blockApp(packageName, preferences.reminderText)
            checkAppTimerLimit(packageName) -> { /* Handled in checkAppTimerLimit */ }
        }
    }

    private fun handleWindowContentChanged(packageName: String) {
        // Only act on content-changed events that originate from YouTube AND while YouTube is
        // already known (from the last window-state-changed event) to be the foreground app.
        // This is what lets us re-run Shorts detection as the user scrolls within YouTube
        // (switching between the Shorts feed and a normal video without a window-state change),
        // without letting unrelated background windows disturb the foreground tracking state.
        if (packageName != AppPreferencesRepository.YOUTUBE_PACKAGE) return
        if (YouTubeTrackingState.foregroundPackage != AppPreferencesRepository.YOUTUBE_PACKAGE) return

        updateYouTubeTrackingState()
        if (preferences.blockingEnabled) {
            handleYouTubeBlocking()
        }
    }

    private fun updateYouTubeTrackingState() {
        val root = rootInActiveWindow
        val shortsDetected = ShortsDetector.isShortsScreen(root)
        root?.recycle()

        lastShortsDetected = shortsDetected
        YouTubeTrackingState.setYouTubeTracking(foreground = true, shortsActive = shortsDetected)
    }

    private fun handleYouTubeBlocking() {
        if (YouTubeTrackingState.isYouTubeLimitReached) {
            blockYouTube(getString(R.string.youtube_limit_reached))
            return
        }

        if (lastShortsDetected) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            blockYouTube(getString(R.string.shorts_blocked_message))
        }
    }

    private fun checkAppTimerLimit(packageName: String): Boolean {
        val limitMinutes = preferences.timerLimits[packageName] ?: return false
        val usageSeconds = preferences.appUsageSeconds[packageName] ?: 0L
        if (usageSeconds >= limitMinutes * 60L) {
            blockApp(packageName, preferences.reminderText)
            return true
        }
        return false
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
        YouTubeTrackingState.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: FocusAccessibilityService? = null

        @Volatile
        private var acknowledgedPackageSession: String? = null

        fun isRunning(): Boolean = instance != null

        fun acknowledgeSession(packageName: String) {
            acknowledgedPackageSession = packageName
        }

        fun clearAcknowledgedSession() {
            acknowledgedPackageSession = null
        }
    }
}
