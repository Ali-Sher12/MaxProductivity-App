package com.maxproductivity.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class YouTubeTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: AppPreferencesRepository
    private var tickJob: Job? = null

    @Volatile
    private var preferences: AppPreferences = AppPreferences()

    @Volatile
    private var accumulatedSeconds: Long = -1L

    private val localAppUsage = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = MaxProductivityApp.get(this).preferencesRepository
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            repository.preferencesFlow.collect { prefs ->
                preferences = prefs
                val today = LocalDate.now().toString()

                val dbUsage = prefs.youtubeUsageSeconds
                if (accumulatedSeconds == -1L || prefs.youtubeUsageDate != today || dbUsage == 0L) {
                    accumulatedSeconds = dbUsage
                } else {
                    accumulatedSeconds = maxOf(accumulatedSeconds, dbUsage)
                }

                YouTubeTrackingState.isYouTubeLimitReached = hasReachedLimit(prefs, accumulatedSeconds)

                if (prefs.appUsageDate != today) {
                    localAppUsage.clear()
                } else {
                    prefs.appUsageSeconds.forEach { (pkg, secs) ->
                        val localSecs = localAppUsage[pkg]
                        if (localSecs == null || secs == 0L) {
                            localAppUsage[pkg] = secs
                        } else {
                            localAppUsage[pkg] = maxOf(localSecs, secs)
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            repository.resetDailyUsageIfNeeded()
            startTickLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
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

                // Reset daily stats on date change
                if (preferences.youtubeUsageDate != today || preferences.appUsageDate != today) {
                    accumulatedSeconds = 0L
                    localAppUsage.clear()
                    YouTubeTrackingState.isYouTubeLimitReached = false
                    repository.resetDailyUsageIfNeeded(today)
                }

                val fgPackage = YouTubeTrackingState.foregroundPackage

                if (
                    YouTubeTrackingState.isYouTubeForeground &&
                    fgPackage == AppPreferencesRepository.YOUTUBE_PACKAGE &&
                    !YouTubeTrackingState.isShortsActive &&
                    !YouTubeTrackingState.isYouTubeLimitReached
                ) {
                    accumulatedSeconds += 1
                    repository.updateYoutubeUsage(accumulatedSeconds, today)

                    if (hasReachedLimit(preferences, accumulatedSeconds)) {
                        YouTubeTrackingState.isYouTubeLimitReached = true
                        startActivity(
                            BlockOverlayActivity.createIntent(
                                this@YouTubeTimerService,
                                getString(R.string.youtube_limit_reached),
                                AppPreferencesRepository.YOUTUBE_PACKAGE,
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                } else if (fgPackage != null && fgPackage != AppPreferencesRepository.YOUTUBE_PACKAGE) {
                    val limitMinutes = preferences.timerLimits[fgPackage]
                    if (limitMinutes != null && limitMinutes > 0) {
                        val currentUsage = localAppUsage[fgPackage] ?: 0L
                        if (currentUsage < limitMinutes * 60L) {
                            val newUsage = currentUsage + 1
                            localAppUsage[fgPackage] = newUsage
                            repository.updateAppUsage(fgPackage, newUsage, today)

                            if (newUsage >= limitMinutes * 60L) {
                                startActivity(
                                    BlockOverlayActivity.createIntent(
                                        this@YouTubeTimerService,
                                        getString(R.string.block_overlay_default_message),
                                        fgPackage,
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasReachedLimit(prefs: AppPreferences, seconds: Long): Boolean {
        if (prefs.youtubeDailyLimitMinutes <= 0) return false
        return seconds >= prefs.youtubeDailyLimitMinutes * 60L
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
            description = "Tracks YouTube usage while focus mode is active"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "focus_mode_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 1_000L
    }
}
