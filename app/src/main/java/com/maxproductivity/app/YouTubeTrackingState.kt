package com.maxproductivity.app

import android.view.accessibility.AccessibilityNodeInfo

object YouTubeTrackingState {
    @Volatile
    var isYouTubeForeground: Boolean = false

    @Volatile
    var isShortsActive: Boolean = false

    @Volatile
    var isYouTubeLimitReached: Boolean = false

    @Volatile
    var foregroundPackage: String? = null
}
