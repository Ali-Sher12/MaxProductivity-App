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
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import com.maxproductivity.app.data.ED_NIGHT_BLOCK_MESSAGE
import com.maxproductivity.app.data.dueTimerReminderMilestone
import com.maxproductivity.app.data.reminderMilestoneKey
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
    private val shownReminderMilestones = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    @Volatile private var trackedPackage: String? = null
    @Volatile private var trackedStartedAtElapsed: Long = 0L
    @Volatile private var trackedBaseUsage: Long = 0L

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
                    shownReminderMilestones.clear()
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
                    shownReminderMilestones.clear()
                    resetTracking()
                    YouTubeTrackingState.isYouTubeLimitReached = false
                    repository.resetDailyUsageIfNeeded(today)
                    continue
                }

                if (!FocusAccessibilityService.isRunning()) {
                    resetTracking()
                    continue
                }

                // Never count time while the screen is off or the device is asleep.
                val powerManager = getSystemService(PowerManager::class.java)
                if (powerManager?.isInteractive != true) {
                    resetTracking()
                    continue
                }

                val state = YouTubeTrackingState.snapshot()
                if (state.isNoticeActive || state.isBlockOverlayActive) {
                    // LongTerm's pop-up is open; do not deduct time from the app underneath.
                    resetTracking()
                    continue
                }

                val fgPackage = state.foregroundPackage
                if (fgPackage == null || fgPackage == packageName) {
                    resetTracking()
                    continue
                }

                if (preferences.isEdNightBlocked(fgPackage)) {
                    resetTracking()
                    startActivity(
                        BlockOverlayActivity.createIntent(
                            this@YouTubeTimerService,
                            ED_NIGHT_BLOCK_MESSAGE,
                            fgPackage,
                            preferences.backgroundImageUri,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    continue
                }

                // Explicitly blocked apps are handled by the accessibility service.
                if (preferences.blockedPackages.contains(fgPackage)) {
                    resetTracking()
                    continue
                }

                val limitSeconds = preferences.getEffectiveLimitSeconds(fgPackage)
                if (limitSeconds == null) {
                    resetTracking()
                    continue
                }

                val currentUsage = localAppUsage[fgPackage] ?: preferences.getUsageSeconds(fgPackage)
                if (currentUsage >= limitSeconds) {
                    if (fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE) {
                        YouTubeTrackingState.isYouTubeLimitReached = true
                    }
                    resetTracking()
                    continue
                }

                // Shorts sessions are always blocked and are not counted toward the normal
                // YouTube limit.
                if (
                    fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE &&
                    (state.isShortsActive || state.isYouTubeLimitReached)
                ) {
                    resetTracking()
                    continue
                }

                val previousUsage = currentUsage
                val nowElapsed = SystemClock.elapsedRealtime()
                if (trackedPackage != fgPackage) {
                    trackedPackage = fgPackage
                    trackedStartedAtElapsed = nowElapsed
                    trackedBaseUsage = currentUsage
                }

                val elapsedSeconds = ((nowElapsed - trackedStartedAtElapsed) / 1000L).coerceAtLeast(0L)
                val newUsage = trackedBaseUsage + elapsedSeconds
                if (newUsage != currentUsage) {
                    localAppUsage[fgPackage] = newUsage
                    repository.updateAppUsage(fgPackage, newUsage, today)
                }

                if (newUsage >= limitSeconds) {
                    if (fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE) {
                        YouTubeTrackingState.isYouTubeLimitReached = true
                    }
                    val blockMessage = if (preferences.isExtremelyDistracting(fgPackage)) {
                        preferences.extremelyDistractingLimitMessage
                    } else {
                        preferences.timerLimitMessage
                    }
                    resetTracking()
                    startActivity(
                        BlockOverlayActivity.createIntent(
                            this@YouTubeTimerService,
                            blockMessage,
                            fgPackage,
                            preferences.backgroundImageUri,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    continue
                }

                // Remind only when the timer starts, when 5 minutes remain (limit >= 10m),
                // and when 1 minute remains (limit >= 5m).
                val limitMinutes = (limitSeconds / 60L).toInt()
                val milestone = dueTimerReminderMilestone(limitMinutes, previousUsage, newUsage)
                if (milestone != null) {
                    val milestoneKey = reminderMilestoneKey(fgPackage, today, milestone)
                    val alreadyShown = preferences.hasShownReminder(fgPackage, milestone, today)
                    if (!alreadyShown && shownReminderMilestones.add(milestoneKey)) {
                        repository.markReminderMilestoneShown(fgPackage, milestone, today)
                        val remainingSeconds = maxOf(1L, limitSeconds - newUsage)
                        resetTracking()
                        startActivity(
                            TimeNoticeActivity.createIntent(
                                this@YouTubeTimerService,
                                fgPackage,
                                remainingSeconds,
                                limitMinutes,
                            ),
                        )
                        continue
                    }
                }
            }
        }
    }

    private fun resetTracking() {
        trackedPackage = null
        trackedStartedAtElapsed = 0L
        trackedBaseUsage = 0L
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
