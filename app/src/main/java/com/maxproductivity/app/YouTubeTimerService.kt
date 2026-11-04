package com.maxproductivity.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import com.maxproductivity.app.data.EXTREMELY_DISTRACTING_PACKAGES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class YouTubeTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: AppPreferencesRepository
    private var tickJob: Job? = null

    @Volatile
    private var preferences: AppPreferences = AppPreferences()

    private val localAppUsage = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = MaxProductivityApp.get(this).preferencesRepository
        createNotificationChannel()
        startServiceInForeground()

        serviceScope.launch {
            repository.preferencesFlow.collect { prefs ->
                preferences = prefs
                val today = LocalDate.now().toString()

                if (prefs.appUsageDate != today) {
                    localAppUsage.clear()
                } else {
                    prefs.appUsageSeconds.forEach { (pkg, secs) ->
                        val localSecs = localAppUsage[pkg]
                        if (localSecs == null) {
                            localAppUsage[pkg] = secs
                        } else {
                            localAppUsage[pkg] = maxOf(localSecs, secs)
                        }
                    }
                }

                YouTubeTrackingState.isYouTubeLimitReached =
                    prefs.isTimerExpired(AppPreferencesRepository.YOUTUBE_PACKAGE)
            }
        }

        serviceScope.launch {
            repository.resetDailyUsageIfNeeded()
            startTickLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServiceInForeground()
        return START_STICKY
    }

    private fun startServiceInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                val today = LocalDate.now().toString()

                if (preferences.appUsageDate != today) {
                    localAppUsage.clear()
                    YouTubeTrackingState.isYouTubeLimitReached = false
                    repository.resetDailyUsageIfNeeded(today)
                    continue
                }

                if (!FocusAccessibilityService.isRunning()) continue
                if (!preferences.blockingEnabled) continue

                val state = YouTubeTrackingState.snapshot()
                if (state.isNoticeActive || state.isBlockOverlayActive) {
                    // LongTerm's pop-up is open; do not deduct time from the app underneath.
                    continue
                }

                val fgPackage = state.foregroundPackage ?: continue
                if (fgPackage == packageName) continue

                // Explicitly blocked apps are handled by the accessibility service.
                if (preferences.blockedPackages.contains(fgPackage)) continue

                val limitSeconds = preferences.getEffectiveLimitSeconds(fgPackage) ?: continue
                val currentUsage = localAppUsage[fgPackage] ?: preferences.getUsageSeconds(fgPackage)

                if (currentUsage >= limitSeconds) {
                    if (fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE) {
                        YouTubeTrackingState.isYouTubeLimitReached = true
                    }
                    continue
                }

                // Shorts sessions are always blocked and are not counted toward the normal
                // YouTube limit.
                if (
                    fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE &&
                    (state.isShortsActive || state.isYouTubeLimitReached)
                ) {
                    continue
                }

                val newUsage = currentUsage + 1
                localAppUsage[fgPackage] = newUsage
                repository.updateAppUsage(fgPackage, newUsage, today)

                if (newUsage >= limitSeconds) {
                    if (fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE) {
                        YouTubeTrackingState.isYouTubeLimitReached = true
                    }
                    val blockMessage = if (EXTREMELY_DISTRACTING_PACKAGES.contains(fgPackage)) {
                        preferences.extremelyDistractingLimitMessage
                    } else {
                        preferences.timerLimitMessage
                    }
                    startActivity(
                        BlockOverlayActivity.createIntent(
                            this@YouTubeTimerService,
                            blockMessage,
                            fgPackage,
                            preferences.backgroundImageUri,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    private fun sendUserHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.timer_notification_title))
            .setContentText(getString(R.string.timer_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus Mode",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Tracks app usage while focus mode is active"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "focus_mode_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 1_000L
    }
}
