package com.maxproductivity.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun getEffectiveLimitMinutes_returnsDefault15ForExtremelyDistractingWhenUnset() {
        val prefs = AppPreferences()
        for (pkg in EXTREMELY_DISTRACTING_PACKAGES) {
            assertEquals(15, prefs.getEffectiveLimitMinutes(pkg))
        }
    }

    @Test
    fun getEffectiveLimitMinutes_clampsConfiguredExtremelyDistractingLimitsTo1Through20() {
        val prefs = AppPreferences(
            timerLimits = mapOf(
                "com.instagram.android" to 5,
                "com.reddit.frontpage" to 0,
                "com.snapchat.android" to 30,
            )
        )
        assertEquals(5, prefs.getEffectiveLimitMinutes("com.instagram.android"))
        assertEquals(1, prefs.getEffectiveLimitMinutes("com.reddit.frontpage"))
        assertEquals(20, prefs.getEffectiveLimitMinutes("com.snapchat.android"))
    }

    @Test
    fun getEffectiveLimitMinutes_returnsCustomLimitsOrNullForOtherApps() {
        val prefs = AppPreferences(
            timerLimits = mapOf(
                "com.example.otherapp" to 45,
            )
        )
        assertEquals(30, prefs.getEffectiveLimitMinutes("com.example.otherapp"))
        assertNull(prefs.getEffectiveLimitMinutes("com.example.unlimited"))
    }

    @Test
    fun getEffectiveLimitSeconds_usesNinetyPercentOfNominalLimit() {
        val prefs = AppPreferences(
            timerLimits = mapOf("com.example.otherapp" to 10),
        )
        // 10 nominal minutes -> 9 real minutes of enforcement.
        assertEquals(540L, prefs.getEffectiveLimitSeconds("com.example.otherapp"))
    }

    @Test
    fun isTimerExpired_triggersAtNinetyPercentOfNominalLimit() {
        val pkg = "com.example.otherapp"
        val before = AppPreferences(
            timerLimits = mapOf(pkg to 10),
            appUsageSeconds = mapOf(pkg to 539L),
        )
        val atLimit = AppPreferences(
            timerLimits = mapOf(pkg to 10),
            appUsageSeconds = mapOf(pkg to 540L),
        )

        assertFalse(before.isTimerExpired(pkg))
        assertTrue(atLimit.isTimerExpired(pkg))
    }

    @Test
    fun getDisplayedUsageSeconds_hidesTheNinetyPercentFactor() {
        val pkg = "com.example.otherapp"
        val prefs = AppPreferences(appUsageSeconds = mapOf(pkg to 540L))
        // 540 real seconds are shown as 600 seconds (10 nominal minutes).
        assertEquals(600L, prefs.getDisplayedUsageSeconds(pkg))
    }

    @Test
    fun hasActiveTimer_isAlwaysTrueForExtremelyDistractingAndOptionalForOtherApps() {
        val prefs = AppPreferences()
        assertTrue(prefs.hasActiveTimer("com.instagram.android"))
        assertFalse(prefs.hasActiveTimer("com.example.otherapp"))

        val timed = prefs.copy(timerLimits = mapOf("com.example.otherapp" to 12))
        assertTrue(timed.hasActiveTimer("com.example.otherapp"))
    }

    @Test
    fun isTimerLockedForToday_onlyMatchesTheStoredDate() {
        val pkg = "com.example.otherapp"
        val prefs = AppPreferences(timerLastModifiedDates = mapOf(pkg to "2026-11-04"))

        assertTrue(prefs.isTimerLockedForToday(pkg, today = "2026-11-04"))
        assertFalse(prefs.isTimerLockedForToday(pkg, today = "2026-11-05"))
    }
}
