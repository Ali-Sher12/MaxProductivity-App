package com.maxproductivity.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsDetectorTest {

    @Test
    fun isShortsScreen_returnsFalseForNullRoot() {
        assertFalse(ShortsDetector.isShortsScreen(null))
    }

    @Test
    fun matchesViewId_returnsTrueForKnownSignatures() {
        assertTrue(ShortsDetector.matchesViewId("com.google.android.youtube:id/reel_watch_fragment"))
        assertTrue(ShortsDetector.matchesViewId("com.google.android.youtube:id/reel_recycler"))
        assertTrue(ShortsDetector.matchesViewId("com.google.android.youtube:id/shorts_player"))
        assertTrue(ShortsDetector.matchesViewId("com.google.android.youtube:id/reel_video"))
        assertTrue(ShortsDetector.matchesViewId("com.google.android.youtube:id/shorts_container"))
    }

    @Test
    fun matchesViewId_excludesTabsAndNavigationButtons() {
        assertFalse(ShortsDetector.matchesViewId("com.google.android.youtube:id/shorts_tab"))
        assertFalse(ShortsDetector.matchesViewId("com.google.android.youtube:id/reel_button"))
        assertFalse(ShortsDetector.matchesViewId("com.google.android.youtube:id/bottom_navigation_bar"))
    }

    @Test
    fun matchesViewId_returnsFalseForUnrelatedIds() {
        assertFalse(ShortsDetector.matchesViewId(null))
        assertFalse(ShortsDetector.matchesViewId(""))
        assertFalse(ShortsDetector.matchesViewId("com.google.android.youtube:id/watch_player"))
        assertFalse(ShortsDetector.matchesViewId("com.google.android.youtube:id/home_feed"))
    }
}
