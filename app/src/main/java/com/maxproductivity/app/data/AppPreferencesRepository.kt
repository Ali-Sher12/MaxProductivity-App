package com.maxproductivity.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maxproductivity.app.blocking.BlockingStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Extremely Distracting Apps: Twitter, Instagram, Reddit, Snapchat, TikTok, Discord.
 * These apps are always timed and can never be explicitly blocked.
 */
val EXTREMELY_DISTRACTING_PACKAGES = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill",
    "com.zhiliaoapp.musically.go",
    "com.reddit.frontpage",
    "com.snapchat.android",
    "com.twitter.android",
    "com.twitter.android.lite",
    "com.discord",
    "com.discord.canary",
    "com.facebook.katana",
    "com.facebook.lite",
    "com.facebook.orca",
    "com.instagram.barcelona",
    "com.pinterest",
    "tv.twitch.android.app",
    "xyz.blueskyweb.app",
    "com.tumblr",
    "com.bereal.ft",
)

const val EXTREMELY_DISTRACTING_DEFAULT_LIMIT_MINUTES = 15
const val EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES = 1
const val EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES = 20

const val OTHER_APP_MIN_LIMIT_MINUTES = 1
const val OTHER_APP_MAX_LIMIT_MINUTES = 30

// Backwards-compatible names used by older code/tests.
const val LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES = EXTREMELY_DISTRACTING_DEFAULT_LIMIT_MINUTES
const val LIMITED_SOCIAL_MIN_LIMIT_MINUTES = EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES
const val LIMITED_SOCIAL_MAX_LIMIT_MINUTES = EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES

/**
 * Timers are intentionally measured against 90% of the nominal limit. The user sees the
 * nominal value everywhere; only the enforcement uses this factor.
 */
const val TIMER_MEASUREMENT_NUMERATOR = 9L
const val TIMER_MEASUREMENT_DENOMINATOR = 10L

val ALWAYS_BLOCKED_PACKAGES: Set<String> = emptySet()

data class AppPreferences(
    val blockedPackages: Set<String> = emptySet(),
    val timerLimits: Map<String, Int> = emptyMap(), // packageName -> limit in minutes
    val timerPriorBlockedPackages: Set<String> = emptySet(), // packages that were blocked before a timer was set
    val timerLastModifiedDates: Map<String, String> = emptyMap(), // packageName -> yyyy-MM-dd
    val blockingEnabled: Boolean = true,
    val appUsageSeconds: Map<String, Long> = emptyMap(), // packageName -> real usage in seconds
    val appUsageDate: String = LocalDate.now().toString(),
    val reminderText: String = "Stay focused. This app is blocked.",
    val timerLimitMessage: String = "Your time limit for this app is up for today.",
    val extremelyDistractingLimitMessage: String = "Daily limit reached for this app.",
    val motto: String = "Build better habits, one day at a time.",
    val backgroundImageUri: String = "",
    val blockingStrategy: BlockingStrategy = BlockingStrategy.ACCESSIBILITY,
    val passwordHash: String = "",
    val onboardingCompleted: Boolean = false,
) {
    fun isExtremelyDistracting(packageName: String): Boolean = EXTREMELY_DISTRACTING_PACKAGES.contains(packageName)

    /** The limit the user sees/configured. EXTREMELY_DISTRACTING apps fall back to 15 minutes. */
    fun getEffectiveLimitMinutes(packageName: String): Int? {
        if (isExtremelyDistracting(packageName)) {
            return (timerLimits[packageName] ?: EXTREMELY_DISTRACTING_DEFAULT_LIMIT_MINUTES)
                .coerceIn(EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES, EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES)
        }
        return timerLimits[packageName]
            ?.takeIf { it > 0 }
            ?.coerceIn(OTHER_APP_MIN_LIMIT_MINUTES, OTHER_APP_MAX_LIMIT_MINUTES)
    }

    /** Hidden enforcement limit: 90% of the nominal limit. */
    fun getEffectiveLimitSeconds(packageName: String): Long? {
        val minutes = getEffectiveLimitMinutes(packageName) ?: return null
        return (minutes * 60L * TIMER_MEASUREMENT_NUMERATOR) / TIMER_MEASUREMENT_DENOMINATOR
    }

    fun getUsageSeconds(packageName: String): Long = appUsageSeconds[packageName] ?: 0L

    /**
     * Usage shown to the user is scaled back up so it lines up with the nominal limit.
     * This keeps the 90% enforcement factor invisible.
     */
    fun getDisplayedUsageSeconds(packageName: String): Long {
        val real = getUsageSeconds(packageName)
        return (real * TIMER_MEASUREMENT_DENOMINATOR) / TIMER_MEASUREMENT_NUMERATOR
    }

    fun hasActiveTimer(packageName: String): Boolean =
        isExtremelyDistracting(packageName) || (timerLimits[packageName] ?: 0) > 0

    fun isTimerExpired(packageName: String): Boolean {
        val limitSeconds = getEffectiveLimitSeconds(packageName) ?: return false
        return getUsageSeconds(packageName) >= limitSeconds
    }

    fun isTimerLockedForToday(
        packageName: String,
        today: String = LocalDate.now().toString(),
    ): Boolean = timerLastModifiedDates[packageName] == today
}

class AppPreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val rawTimerLimits = (prefs[KEY_TIMER_LIMITS] ?: emptySet()).toTimerMap()
        val mergedTimerLimits = rawTimerLimits
            .filterKeys { !EXTREMELY_DISTRACTING_PACKAGES.contains(it) }
            .mapValues { (_, value) ->
                value.coerceIn(OTHER_APP_MIN_LIMIT_MINUTES, OTHER_APP_MAX_LIMIT_MINUTES)
            }
            .toMutableMap()
        for (pkg in EXTREMELY_DISTRACTING_PACKAGES) {
            val existing = rawTimerLimits[pkg]
            mergedTimerLimits[pkg] = (existing ?: EXTREMELY_DISTRACTING_DEFAULT_LIMIT_MINUTES)
                .coerceIn(EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES, EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES)
        }

        AppPreferences(
            blockedPackages = (prefs[KEY_BLOCKED_PACKAGES] ?: emptySet())
                .filterNot {
                    EXTREMELY_DISTRACTING_PACKAGES.contains(it) || mergedTimerLimits.containsKey(it)
                }
                .toSet(),
            timerLimits = mergedTimerLimits,
            timerPriorBlockedPackages = (prefs[KEY_TIMER_PRIOR_BLOCKED] ?: emptySet()) +
                (prefs[KEY_BLOCKED_PACKAGES] ?: emptySet())
                    .filter { mergedTimerLimits.containsKey(it) && !EXTREMELY_DISTRACTING_PACKAGES.contains(it) },
            timerLastModifiedDates = (prefs[KEY_TIMER_LAST_MODIFIED] ?: emptySet()).toStringMap(),
            blockingEnabled = prefs[KEY_BLOCKING_ENABLED] ?: true,
            appUsageSeconds = (prefs[KEY_APP_USAGE_SECONDS] ?: emptySet()).toUsageMap(),
            appUsageDate = prefs[KEY_APP_USAGE_DATE] ?: LocalDate.now().toString(),
            reminderText = prefs[KEY_REMINDER_TEXT] ?: "Stay focused. This app is blocked.",
            timerLimitMessage = prefs[KEY_TIMER_LIMIT_MESSAGE]
                ?: "Your time limit for this app is up for today.",
            extremelyDistractingLimitMessage = prefs[KEY_EXTREMELY_DISTRACTING_LIMIT_MESSAGE]
                ?: "Daily limit reached for this app.",
            motto = prefs[KEY_MOTTO] ?: "Build better habits, one day at a time.",
            backgroundImageUri = prefs[KEY_BACKGROUND_IMAGE_URI] ?: "",
            blockingStrategy = BlockingStrategy.fromName(prefs[KEY_BLOCKING_STRATEGY]),
            passwordHash = prefs[KEY_PASSWORD_HASH] ?: "",
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
        )
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCKING_ENABLED] = enabled }
    }

    suspend fun setBlockedPackages(packages: Set<String>) {
        context.dataStore.edit {
            it[KEY_BLOCKED_PACKAGES] = packages
                .filterNot { pkg -> EXTREMELY_DISTRACTING_PACKAGES.contains(pkg) }
                .toSet()
        }
    }

    suspend fun setTimer(packageName: String, minutes: Int) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val isExtremelyDistracting = EXTREMELY_DISTRACTING_PACKAGES.contains(packageName)
            val clamped = if (isExtremelyDistracting) {
                minutes.coerceIn(EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES, EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES)
            } else {
                minutes.coerceIn(OTHER_APP_MIN_LIMIT_MINUTES, OTHER_APP_MAX_LIMIT_MINUTES)
            }

            val currentLimits = (prefs[KEY_TIMER_LIMITS] ?: emptySet()).toTimerMap().toMutableMap()
            currentLimits[packageName] = clamped
            prefs[KEY_TIMER_LIMITS] = currentLimits.toTimerSet()

            val currentBlocked = (prefs[KEY_BLOCKED_PACKAGES] ?: emptySet()).toMutableSet()
            val priorBlocked = (prefs[KEY_TIMER_PRIOR_BLOCKED] ?: emptySet()).toMutableSet()
            if (currentBlocked.contains(packageName)) {
                priorBlocked.add(packageName)
            } else {
                priorBlocked.remove(packageName)
            }
            prefs[KEY_TIMER_PRIOR_BLOCKED] = priorBlocked

            // A timed app is not an explicitly blocked app. EXTREMELY_DISTRACTING apps are never blocked.
            currentBlocked.remove(packageName)
            prefs[KEY_BLOCKED_PACKAGES] = currentBlocked

            val currentModified = (prefs[KEY_TIMER_LAST_MODIFIED] ?: emptySet()).toStringMap().toMutableMap()
            currentModified[packageName] = today
            prefs[KEY_TIMER_LAST_MODIFIED] = currentModified.toStringSet()
        }
    }

    suspend fun removeTimer(packageName: String) {
        if (EXTREMELY_DISTRACTING_PACKAGES.contains(packageName)) return
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()

            val currentLimits = (prefs[KEY_TIMER_LIMITS] ?: emptySet()).toTimerMap().toMutableMap()
            currentLimits.remove(packageName)
            prefs[KEY_TIMER_LIMITS] = currentLimits.toTimerSet()

            val priorBlocked = (prefs[KEY_TIMER_PRIOR_BLOCKED] ?: emptySet()).toMutableSet()
            val wasBlockedBeforeTimer = priorBlocked.remove(packageName)
            prefs[KEY_TIMER_PRIOR_BLOCKED] = priorBlocked

            val currentBlocked = (prefs[KEY_BLOCKED_PACKAGES] ?: emptySet()).toMutableSet()
            if (wasBlockedBeforeTimer || currentBlocked.contains(packageName)) {
                currentBlocked.add(packageName)
            } else {
                currentBlocked.remove(packageName)
            }
            prefs[KEY_BLOCKED_PACKAGES] = currentBlocked

            val currentModified = (prefs[KEY_TIMER_LAST_MODIFIED] ?: emptySet()).toStringMap().toMutableMap()
            currentModified[packageName] = today
            prefs[KEY_TIMER_LAST_MODIFIED] = currentModified.toStringSet()
        }
    }

    suspend fun setReminderText(text: String) {
        context.dataStore.edit { it[KEY_REMINDER_TEXT] = text }
    }

    suspend fun setTimerLimitMessage(text: String) {
        context.dataStore.edit { it[KEY_TIMER_LIMIT_MESSAGE] = text }
    }

    suspend fun setExtremelyDistractingLimitMessage(text: String) {
        context.dataStore.edit { it[KEY_EXTREMELY_DISTRACTING_LIMIT_MESSAGE] = text }
    }

    suspend fun setBackgroundImageUri(uri: String) {
        context.dataStore.edit { it[KEY_BACKGROUND_IMAGE_URI] = uri }
    }

    suspend fun setBlockingStrategy(strategy: BlockingStrategy) {
        context.dataStore.edit { it[KEY_BLOCKING_STRATEGY] = strategy.name }
    }

    suspend fun setMotto(text: String) {
        context.dataStore.edit { it[KEY_MOTTO] = text }
    }

    suspend fun setPasswordHash(hash: String) {
        context.dataStore.edit { it[KEY_PASSWORD_HASH] = hash }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
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
        private val KEY_TIMER_PRIOR_BLOCKED = stringSetPreferencesKey("timer_prior_blocked")
        private val KEY_TIMER_LAST_MODIFIED = stringSetPreferencesKey("timer_last_modified")
        private val KEY_BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")
        private val KEY_APP_USAGE_SECONDS = stringSetPreferencesKey("app_usage_seconds")
        private val KEY_APP_USAGE_DATE = stringPreferencesKey("app_usage_date")
        private val KEY_REMINDER_TEXT = stringPreferencesKey("reminder_text")
        private val KEY_TIMER_LIMIT_MESSAGE = stringPreferencesKey("timer_limit_message")
        private val KEY_EXTREMELY_DISTRACTING_LIMIT_MESSAGE = stringPreferencesKey("tirst_limit_message")
        private val KEY_MOTTO = stringPreferencesKey("motto")
        private val KEY_BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        private val KEY_BLOCKING_STRATEGY = stringPreferencesKey("blocking_strategy")
        private val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

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
