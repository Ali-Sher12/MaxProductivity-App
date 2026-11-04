package com.maxproductivity.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun getEffectiveLimitMinutes_returnsDefault15ForLimitedSocialWhenUnset() {
        val prefs = AppPreferences()
        for (pkg in LIMITED_SOCIAL_PACKAGES) {
            assertEquals(15, prefs.getEffectiveLimitMinutes(pkg))
        }
    }

    @Test
    fun getEffectiveLimitMinutes_clampsConfiguredSocialLimitsTo1Through15() {
        val prefs = AppPreferences(
            timerLimits = mapOf(
                "com.instagram.android" to 5,
                "com.reddit.frontpage" to 0,
                "com.snapchat.android" to 30,
            )
        )
        // 5 is within 1..15 -> 5
        assertEquals(5, prefs.getEffectiveLimitMinutes("com.instagram.android"))
        // 0 clamped to min 1
        assertEquals(1, prefs.getEffectiveLimitMinutes("com.reddit.frontpage"))
        // 30 clamped to max 15
        assertEquals(15, prefs.getEffectiveLimitMinutes("com.snapchat.android"))
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
}
