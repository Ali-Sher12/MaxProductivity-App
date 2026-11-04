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
import com.maxproductivity.app.data.EXTREMELY_DISTRACTING_PACKAGES
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
    private val deviceOwnerStrategy = DeviceOwnerSuspendStrategy()

    @Volatile
    private var preferences: AppPreferences = AppPreferences()

    @Volatile
    private var lastShortsDetected: Boolean = false

    @Volatile
    private var lastSuspendedPackages: Set<String> = emptySet()

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

            // YouTube Shorts can NEVER be unblocked. They always remain blocked even if blockingEnabled is false.
            if (lastShortsDetected) {
                blockShorts()
                return
            }

            if (!preferences.blockingEnabled) return
            handleYouTubeBlocking()
            return
        }

        YouTubeTrackingState.clearYouTubeForeground()
        lastShortsDetected = false

        // Do not intercept or clear session when our own UI (TimeNoticeActivity / BlockOverlayActivity) is shown
        if (packageName == this.packageName) {
            return
        }

        if (YouTubeTrackingState.isNoticeActive || YouTubeTrackingState.isBlockOverlayActive) {
            return
        }

        // Switching to a different app clears the previously acknowledged session
        if (packageName != acknowledgedPackageSession) {
            acknowledgedPackageSession = null
        }

        if (!preferences.blockingEnabled) return

        // Apps with an active timer (Extremely Distracting apps or user-configured timed apps)
        if (preferences.hasActiveTimer(packageName)) {
            val limitMinutes = preferences.getEffectiveLimitMinutes(packageName) ?: 15
            if (preferences.isTimerExpired(packageName)) {
                val message = if (preferences.isExtremelyDistracting(packageName)) {
                    preferences.extremelyDistractingLimitMessage
                } else {
                    preferences.timerLimitMessage
                }
                blockApp(packageName, message)
                return
            }

            if (acknowledgedPackageSession != packageName) {
                acknowledgedPackageSession = packageName
                val displayedUsedSeconds = preferences.getDisplayedUsageSeconds(packageName)
                val remainingSeconds = maxOf(1L, limitMinutes * 60L - displayedUsedSeconds)
                startActivity(TimeNoticeActivity.createIntent(this, packageName, remainingSeconds, limitMinutes))
                return
            }
            return
        }

        when {
            ALWAYS_BLOCKED_PACKAGES.contains(packageName) -> blockApp(packageName, preferences.reminderText)
            preferences.blockedPackages.contains(packageName) -> blockApp(packageName, preferences.reminderText)
        }
    }

    private fun handleWindowContentChanged(packageName: String) {
        // Only act on content-changed events that originate from YouTube AND while YouTube is
        // already known (from the last window-state-changed event) to be the foreground app.
        if (packageName != AppPreferencesRepository.YOUTUBE_PACKAGE) return
        if (YouTubeTrackingState.foregroundPackage != AppPreferencesRepository.YOUTUBE_PACKAGE) return

        updateYouTubeTrackingState()

        // YouTube Shorts can NEVER be unblocked.
        if (lastShortsDetected) {
            blockShorts()
            return
        }

        if (preferences.blockingEnabled) {
            handleYouTubeBlocking()
        }
    }

    private fun updateYouTubeTrackingState() {
        val root = rootInActiveWindow
        val shortsDetected = ShortsDetector.isShortsScreen(root)
        @Suppress("DEPRECATION")
        root?.recycle()

        lastShortsDetected = shortsDetected
        YouTubeTrackingState.setYouTubeTracking(foreground = true, shortsActive = shortsDetected)
    }

    private fun blockShorts() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        startActivity(
            BlockOverlayActivity.createIntent(
                this,
                getString(R.string.shorts_blocked_message),
                AppPreferencesRepository.YOUTUBE_PACKAGE,
                preferences.backgroundImageUri,
            ),
        )
    }

    private fun handleYouTubeBlocking() {
        // Explicitly blocked YouTube is blocked immediately, regardless of timers.
        if (preferences.blockedPackages.contains(AppPreferencesRepository.YOUTUBE_PACKAGE)) {
            blockYouTube(preferences.reminderText)
            return
        }

        // A timed YouTube app is blocked once its daily limit has been consumed.
        if (preferences.isTimerExpired(AppPreferencesRepository.YOUTUBE_PACKAGE) || YouTubeTrackingState.isYouTubeLimitReached) {
            blockYouTube(preferences.timerLimitMessage)
            return
        }

        if (preferences.hasActiveTimer(AppPreferencesRepository.YOUTUBE_PACKAGE) &&
            acknowledgedPackageSession != AppPreferencesRepository.YOUTUBE_PACKAGE
        ) {
            acknowledgedPackageSession = AppPreferencesRepository.YOUTUBE_PACKAGE
            val limitMinutes = preferences.getEffectiveLimitMinutes(AppPreferencesRepository.YOUTUBE_PACKAGE) ?: 15
            val displayedUsedSeconds = preferences.getDisplayedUsageSeconds(AppPreferencesRepository.YOUTUBE_PACKAGE)
            val remainingSeconds = maxOf(1L, limitMinutes * 60L - displayedUsedSeconds)
            startActivity(TimeNoticeActivity.createIntent(this, AppPreferencesRepository.YOUTUBE_PACKAGE, remainingSeconds, limitMinutes))
            return
        }
    }

    private fun blockApp(packageName: String, message: String) {
        if (preferences.blockingStrategy == BlockingStrategy.DEVICE_OWNER) {
            suspendForDeviceOwner(packageName)
            startActivity(
                BlockOverlayActivity.createIntent(
                    this,
                    message,
                    packageName,
                    preferences.backgroundImageUri,
                ),
            )
            return
        }
        suspensionManager.blockPackage(this, packageName, message, preferences.backgroundImageUri)
    }

    private fun blockYouTube(message: String) {
        if (preferences.blockingStrategy == BlockingStrategy.DEVICE_OWNER) {
            suspendForDeviceOwner(AppPreferencesRepository.YOUTUBE_PACKAGE)
            startActivity(
                BlockOverlayActivity.createIntent(
                    this,
                    message,
                    AppPreferencesRepository.YOUTUBE_PACKAGE,
                    preferences.backgroundImageUri,
                ),
            )
            return
        }
        suspensionManager.blockPackage(
            this,
            AppPreferencesRepository.YOUTUBE_PACKAGE,
            message,
            preferences.backgroundImageUri,
        )
    }

    @Synchronized
    private fun suspendForDeviceOwner(packageName: String) {
        if (lastSuspendedPackages.contains(packageName)) return
        deviceOwnerStrategy.updateSuspendedPackages(this, setOf(packageName), suspend = true)
        lastSuspendedPackages = lastSuspendedPackages + packageName
    }

    @Synchronized
    private fun updateBlockingStrategy(prefs: AppPreferences) {
        val strategy = when (prefs.blockingStrategy) {
            BlockingStrategy.ACCESSIBILITY -> AccessibilityBlockStrategy.fromService(this)
            BlockingStrategy.DEVICE_OWNER -> deviceOwnerStrategy
        }
        suspensionManager.setStrategy(strategy)

        // Extremely Distracting Apps are always timed, never explicitly blocked. They only join the
        // suspended set when their daily timer has expired.
        val explicitlyBlocked = (prefs.blockedPackages + ALWAYS_BLOCKED_PACKAGES)
            .filterNot { EXTREMELY_DISTRACTING_PACKAGES.contains(it) }
            .toSet()
        val expired = prefs.appUsageSeconds.keys
            .filter { prefs.isTimerExpired(it) }
            .toSet()
        val desiredSuspended = if (prefs.blockingEnabled && prefs.blockingStrategy == BlockingStrategy.DEVICE_OWNER) {
            explicitlyBlocked + expired
        } else {
            emptySet()
        }

        if (desiredSuspended != lastSuspendedPackages) {
            val toSuspend = desiredSuspended - lastSuspendedPackages
            val toUnsuspend = lastSuspendedPackages - desiredSuspended
            if (toSuspend.isNotEmpty()) {
                deviceOwnerStrategy.updateSuspendedPackages(this, toSuspend, suspend = true)
            }
            if (toUnsuspend.isNotEmpty()) {
                deviceOwnerStrategy.updateSuspendedPackages(this, toUnsuspend, suspend = false)
            }
            lastSuspendedPackages = desiredSuspended
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        if (lastSuspendedPackages.isNotEmpty()) {
            deviceOwnerStrategy.updateSuspendedPackages(this, lastSuspendedPackages, suspend = false)
            lastSuspendedPackages = emptySet()
        }
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
