package com.maxproductivity.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

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
        assertEquals(45, prefs.getEffectiveLimitMinutes("com.example.otherapp"))
        assertNull(prefs.getEffectiveLimitMinutes("com.example.unlimited"))
    }

    @Test
    fun getEffectiveLimitMinutes_clampsOtherAppsTo1Through60() {
        val prefs = AppPreferences(
            timerLimits = mapOf(
                "com.example.low" to 0,
                "com.example.high" to 90,
            )
        )
        assertNull(prefs.getEffectiveLimitMinutes("com.example.low"))
        assertEquals(60, prefs.getEffectiveLimitMinutes("com.example.high"))
    }

    @Test
    fun getEffectiveLimitSeconds_usesExactNominalLimit() {
        val prefs = AppPreferences(
            timerLimits = mapOf("com.example.otherapp" to 10),
        )
        // 10 configured minutes are measured as exactly 10 minutes of foreground time.
        assertEquals(600L, prefs.getEffectiveLimitSeconds("com.example.otherapp"))
    }

    @Test
    fun isTimerExpired_triggersAtExactNominalLimit() {
        val pkg = "com.example.otherapp"
        val before = AppPreferences(
            timerLimits = mapOf(pkg to 10),
            appUsageSeconds = mapOf(pkg to 599L),
        )
        val atLimit = AppPreferences(
            timerLimits = mapOf(pkg to 10),
            appUsageSeconds = mapOf(pkg to 600L),
        )

        assertFalse(before.isTimerExpired(pkg))
        assertTrue(atLimit.isTimerExpired(pkg))
    }

    @Test
    fun getDisplayedUsageSeconds_returnsRealMeasuredUsage() {
        val pkg = "com.example.otherapp"
        val prefs = AppPreferences(appUsageSeconds = mapOf(pkg to 540L))
        assertEquals(540L, prefs.getDisplayedUsageSeconds(pkg))
    }

    @Test
    fun dueTimerReminderMilestone_onlyShowsAllowedMilestones() {
        // Timer start (first use of the day).
        assertEquals(
            TIMER_REMINDER_START,
            dueTimerReminderMilestone(limitMinutes = 15, previousUsedSeconds = 0L, currentUsedSeconds = 0L),
        )
        assertEquals(
            TIMER_REMINDER_START,
            dueTimerReminderMilestone(limitMinutes = 15, previousUsedSeconds = 0L, currentUsedSeconds = 1L),
        )

        // No pop-up for ordinary ticks.
        assertNull(dueTimerReminderMilestone(limitMinutes = 15, previousUsedSeconds = 120L, currentUsedSeconds = 121L))

        // 5 minutes remaining, only for limits of 10 minutes or more.
        assertEquals(
            TIMER_REMINDER_FIVE_MINUTES,
            dueTimerReminderMilestone(limitMinutes = 15, previousUsedSeconds = 599L, currentUsedSeconds = 600L),
        )
        assertNull(dueTimerReminderMilestone(limitMinutes = 9, previousUsedSeconds = 239L, currentUsedSeconds = 240L))

        // 1 minute remaining, only for limits of 5 minutes or more.
        assertEquals(
            TIMER_REMINDER_ONE_MINUTE,
            dueTimerReminderMilestone(limitMinutes = 15, previousUsedSeconds = 839L, currentUsedSeconds = 840L),
        )
        assertEquals(
            TIMER_REMINDER_ONE_MINUTE,
            dueTimerReminderMilestone(limitMinutes = 5, previousUsedSeconds = 239L, currentUsedSeconds = 240L),
        )
        assertNull(dueTimerReminderMilestone(limitMinutes = 4, previousUsedSeconds = 179L, currentUsedSeconds = 180L))
    }

    @Test
    fun hasShownReminder_isScopedToPackageAndDate() {
        val prefs = AppPreferences(
            reminderMilestones = setOf(
                reminderMilestoneKey("com.example.otherapp", "2026-11-05", TIMER_REMINDER_START),
            ),
        )
        assertTrue(prefs.hasShownReminder("com.example.otherapp", TIMER_REMINDER_START, "2026-11-05"))
        assertFalse(prefs.hasShownReminder("com.example.otherapp", TIMER_REMINDER_START, "2026-11-06"))
        assertFalse(prefs.hasShownReminder("com.example.another", TIMER_REMINDER_START, "2026-11-05"))
    }

    @Test
    fun hasActiveTimer_isAlwaysTrueForExtremelyDistractingUnlessFullyBlocked() {
        val prefs = AppPreferences()
        assertTrue(prefs.hasActiveTimer("com.instagram.android"))
        assertFalse(prefs.hasActiveTimer("com.example.otherapp"))

        val timed = prefs.copy(timerLimits = mapOf("com.example.otherapp" to 12))
        assertTrue(timed.hasActiveTimer("com.example.otherapp"))

        val blockedEd = prefs.copy(blockedPackages = setOf("com.instagram.android"))
        assertFalse(blockedEd.hasActiveTimer("com.instagram.android"))
        assertTrue(blockedEd.isExtremelyDistracting("com.instagram.android"))
    }

    @Test
    fun isTimerLockedForToday_onlyAppliesToExtremelyDistractingApps() {
        val ed = "com.instagram.android"
        val other = "com.example.otherapp"
        val prefs = AppPreferences(
            timerLastModifiedDates = mapOf(
                ed to "2026-11-04",
                other to "2026-11-04",
            )
        )

        assertTrue(prefs.isTimerLockedForToday(ed, today = "2026-11-04"))
        assertFalse(prefs.isTimerLockedForToday(ed, today = "2026-11-05"))
        assertFalse(prefs.isTimerLockedForToday(other, today = "2026-11-04"))
    }

    @Test
    fun userAddedPackagesAreExtremelyDistractingButBuiltInOnesStayBuiltIn() {
        val prefs = AppPreferences(
            userExtremelyDistractingPackages = setOf("com.example.custom"),
        )
        assertTrue(prefs.isExtremelyDistracting("com.example.custom"))
        assertFalse(prefs.isBuiltInExtremelyDistracting("com.example.custom"))
        assertTrue(prefs.isBuiltInExtremelyDistracting("com.instagram.android"))
        assertEquals(15, prefs.getEffectiveLimitMinutes("com.example.custom"))
    }

    @Test
    fun isEdNightBlocked_onlyDuringConfiguredWindowWhenEnabled() {
        val pkg = "com.instagram.android"
        val enabled = AppPreferences(nightBlockEdApps = true)
        val disabled = AppPreferences(nightBlockEdApps = false)

        assertTrue(enabled.isEdNightBlocked(pkg, LocalTime.of(22, 0)))
        assertTrue(enabled.isEdNightBlocked(pkg, LocalTime.of(2, 30)))
        assertTrue(enabled.isEdNightBlocked(pkg, LocalTime.of(9, 59)))
        assertFalse(enabled.isEdNightBlocked(pkg, LocalTime.of(10, 0)))
        assertFalse(enabled.isEdNightBlocked(pkg, LocalTime.of(21, 59)))
        assertFalse(disabled.isEdNightBlocked(pkg, LocalTime.of(23, 0)))
        assertFalse(enabled.isEdNightBlocked("com.example.otherapp", LocalTime.of(23, 0)))
    }

    @Test
    fun isWithinEdNightBlockWindow_coversTenPmToTenAm() {
        assertTrue(isWithinEdNightBlockWindow(LocalTime.of(22, 0)))
        assertTrue(isWithinEdNightBlockWindow(LocalTime.of(0, 0)))
        assertFalse(isWithinEdNightBlockWindow(LocalTime.of(10, 0)))
        assertFalse(isWithinEdNightBlockWindow(LocalTime.of(15, 0)))
    }
}
