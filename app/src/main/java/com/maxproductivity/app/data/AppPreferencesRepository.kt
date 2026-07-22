package com.maxproductivity.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maxproductivity.app.blocking.BlockingStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

val ALWAYS_BLOCKED_PACKAGES = setOf(
    "com.snapchat.android",
    "com.instagram.android",
    "com.zhiliaoapp.musically",
    "com.reddit.frontpage",
    "com.discord",
)

data class AppPreferences(
    val blockedPackages: Set<String> = emptySet(),
    val timerLimits: Map<String, Int> = emptyMap(), // packageName -> limit in minutes
    val timerLastModifiedDates: Map<String, String> = emptyMap(), // packageName -> yyyy-MM-dd
    val blockingEnabled: Boolean = true,
    val youtubeDailyLimitMinutes: Int = 60,
    val youtubeUsageSeconds: Long = 0L,
    val youtubeUsageDate: String = LocalDate.now().toString(),
    val appUsageSeconds: Map<String, Long> = emptyMap(), // packageName -> usage in seconds
    val appUsageDate: String = LocalDate.now().toString(),
    val reminderText: String = "Stay focused. You've got this.",
    val backgroundImageUri: String = "",
    val blockingStrategy: BlockingStrategy = BlockingStrategy.ACCESSIBILITY,
    val blockYouTubeShorts: Boolean = true,
)

class AppPreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            blockedPackages = prefs[KEY_BLOCKED_PACKAGES] ?: emptySet(),
            timerLimits = (prefs[KEY_TIMER_LIMITS] ?: emptySet()).toTimerMap(),
            timerLastModifiedDates = (prefs[KEY_TIMER_LAST_MODIFIED] ?: emptySet()).toStringMap(),
            blockingEnabled = prefs[KEY_BLOCKING_ENABLED] ?: true,
            youtubeDailyLimitMinutes = prefs[KEY_YOUTUBE_LIMIT_MINUTES] ?: 60,
            youtubeUsageSeconds = prefs[KEY_YOUTUBE_USAGE_SECONDS] ?: 0L,
            youtubeUsageDate = prefs[KEY_YOUTUBE_USAGE_DATE] ?: LocalDate.now().toString(),
            appUsageSeconds = (prefs[KEY_APP_USAGE_SECONDS] ?: emptySet()).toUsageMap(),
            appUsageDate = prefs[KEY_APP_USAGE_DATE] ?: LocalDate.now().toString(),
            reminderText = prefs[KEY_REMINDER_TEXT] ?: "Stay focused. You've got this.",
            backgroundImageUri = prefs[KEY_BACKGROUND_IMAGE_URI] ?: "",
            blockingStrategy = BlockingStrategy.fromName(prefs[KEY_BLOCKING_STRATEGY]),
            blockYouTubeShorts = prefs[KEY_BLOCK_YOUTUBE_SHORTS] ?: true,
        )
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCKING_ENABLED] = enabled }
    }

    suspend fun setBlockedPackages(packages: Set<String>) {
        context.dataStore.edit { it[KEY_BLOCKED_PACKAGES] = packages }
    }

    suspend fun setTimerLimitAndRecordChange(packageName: String, minutes: Int?) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val currentLimits = (prefs[KEY_TIMER_LIMITS] ?: emptySet()).toTimerMap().toMutableMap()
            if (minutes == null || minutes <= 0) {
                currentLimits.remove(packageName)
            } else {
                currentLimits[packageName] = minutes
            }
            prefs[KEY_TIMER_LIMITS] = currentLimits.toTimerSet()

            val currentModified = (prefs[KEY_TIMER_LAST_MODIFIED] ?: emptySet()).toStringMap().toMutableMap()
            currentModified[packageName] = today
            prefs[KEY_TIMER_LAST_MODIFIED] = currentModified.toStringSet()
        }
    }

    suspend fun setYoutubeDailyLimitAndRecordChange(minutes: Int) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            prefs[KEY_YOUTUBE_LIMIT_MINUTES] = minutes.coerceAtLeast(0)

            val currentModified = (prefs[KEY_TIMER_LAST_MODIFIED] ?: emptySet()).toStringMap().toMutableMap()
            currentModified[YOUTUBE_PACKAGE] = today
            prefs[KEY_TIMER_LAST_MODIFIED] = currentModified.toStringSet()
        }
    }

    suspend fun setReminderText(text: String) {
        context.dataStore.edit { it[KEY_REMINDER_TEXT] = text }
    }

    suspend fun setBackgroundImageUri(uri: String) {
        context.dataStore.edit { it[KEY_BACKGROUND_IMAGE_URI] = uri }
    }

    suspend fun setBlockingStrategy(strategy: BlockingStrategy) {
        context.dataStore.edit { it[KEY_BLOCKING_STRATEGY] = strategy.name }
    }

    suspend fun setBlockYouTubeShorts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCK_YOUTUBE_SHORTS] = enabled }
    }

    suspend fun updateYoutubeUsage(seconds: Long, date: String) {
        context.dataStore.edit {
            it[KEY_YOUTUBE_USAGE_SECONDS] = seconds
            it[KEY_YOUTUBE_USAGE_DATE] = date
        }
    }

    suspend fun updateAppUsage(packageName: String, seconds: Long, date: String) {
        context.dataStore.edit { prefs ->
            val currentUsage = (prefs[KEY_APP_USAGE_SECONDS] ?: emptySet()).toUsageMap().toMutableMap()
            currentUsage[packageName] = seconds
            prefs[KEY_APP_USAGE_SECONDS] = currentUsage.toUsageSet()
            prefs[KEY_APP_USAGE_DATE] = date
        }
    }

    suspend fun resetDailyUsageIfNeeded(today: String = LocalDate.now().toString()) {
        context.dataStore.edit { prefs ->
            val storedYoutubeDate = prefs[KEY_YOUTUBE_USAGE_DATE] ?: today
            if (storedYoutubeDate != today) {
                prefs[KEY_YOUTUBE_USAGE_SECONDS] = 0L
                prefs[KEY_YOUTUBE_USAGE_DATE] = today
            }

            val storedAppDate = prefs[KEY_APP_USAGE_DATE] ?: today
            if (storedAppDate != today) {
                prefs[KEY_APP_USAGE_SECONDS] = emptySet()
                prefs[KEY_APP_USAGE_DATE] = today
            }
        }
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        private val KEY_BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
        private val KEY_TIMER_LIMITS = stringSetPreferencesKey("timer_limits")
        private val KEY_TIMER_LAST_MODIFIED = stringSetPreferencesKey("timer_last_modified")
        private val KEY_BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")
        private val KEY_YOUTUBE_LIMIT_MINUTES = intPreferencesKey("youtube_limit_minutes")
        private val KEY_YOUTUBE_USAGE_SECONDS = longPreferencesKey("youtube_usage_seconds")
        private val KEY_YOUTUBE_USAGE_DATE = stringPreferencesKey("youtube_usage_date")
        private val KEY_APP_USAGE_SECONDS = stringSetPreferencesKey("app_usage_seconds")
        private val KEY_APP_USAGE_DATE = stringPreferencesKey("app_usage_date")
        private val KEY_REMINDER_TEXT = stringPreferencesKey("reminder_text")
        private val KEY_BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        private val KEY_BLOCKING_STRATEGY = stringPreferencesKey("blocking_strategy")
        private val KEY_BLOCK_YOUTUBE_SHORTS = booleanPreferencesKey("block_youtube_shorts")

        private fun Set<String>.toStringMap(): Map<String, String> {
            return associate {
                val parts = it.split('=')
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    parts[0] to ""
                }
            }
        }

        private fun Map<String, String>.toStringSet(): Set<String> {
            return map { "${it.key}=${it.value}" }.toSet()
        }

        private fun Set<String>.toTimerMap(): Map<String, Int> {
            return associate {
                val parts = it.split('=')
                if (parts.size == 2) {
                    parts[0] to (parts[1].toIntOrNull() ?: 0)
                } else {
                    it to 0
                }
            }
        }

        private fun Map<String, Int>.toTimerSet(): Set<String> {
            return map { "${it.key}=${it.value}" }.toSet()
        }

        private fun Set<String>.toUsageMap(): Map<String, Long> {
            return associate {
                val parts = it.split('=')
                if (parts.size == 2) {
                    parts[0] to (parts[1].toLongOrNull() ?: 0L)
                } else {
                    it to 0L
                }
            }
        }

        private fun Map<String, Long>.toUsageSet(): Set<String> {
            return map { "${it.key}=${it.value}" }.toSet()
        }
    }
}

