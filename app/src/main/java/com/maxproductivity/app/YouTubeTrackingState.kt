package com.maxproductivity.app

object YouTubeTrackingState {

    data class Snapshot(
        val isYouTubeForeground: Boolean,
        val isShortsActive: Boolean,
        val isYouTubeLimitReached: Boolean,
        val isNoticeActive: Boolean,
        val isBlockOverlayActive: Boolean,
        val foregroundPackage: String?,
    )

    @Volatile
    var isYouTubeForeground: Boolean = false
        private set

    @Volatile
    var isShortsActive: Boolean = false
        private set

    @Volatile
    var isYouTubeLimitReached: Boolean = false

    @Volatile
    var isNoticeActive: Boolean = false

    @Volatile
    var isBlockOverlayActive: Boolean = false

    @Volatile
    var foregroundPackage: String? = null
        private set

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        isYouTubeForeground = isYouTubeForeground,
        isShortsActive = isShortsActive,
        isYouTubeLimitReached = isYouTubeLimitReached,
        isNoticeActive = isNoticeActive,
        isBlockOverlayActive = isBlockOverlayActive,
        foregroundPackage = foregroundPackage,
    )

    @Synchronized
    fun updateForeground(packageName: String) {
        foregroundPackage = packageName
    }

    @Synchronized
    fun setYouTubeTracking(foreground: Boolean, shortsActive: Boolean) {
        isYouTubeForeground = foreground
        isShortsActive = shortsActive
    }

    @Synchronized
    fun clearYouTubeForeground() {
        isYouTubeForeground = false
        isShortsActive = false
    }

    @Synchronized
    fun reset() {
        isYouTubeForeground = false
        isShortsActive = false
        isYouTubeLimitReached = false
        isNoticeActive = false
        isBlockOverlayActive = false
        foregroundPackage = null
    }
}
