package com.maxproductivity.app.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.maxproductivity.app.data.AppPreferencesRepository

object UsageStatsHelper {

    fun getForegroundPackage(context: Context): String? {
        if (!PermissionUtils.hasUsageAccess(context)) return null

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5_000L

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime,
        ) ?: return null

        val recent = stats
            .filter { it.lastTimeUsed >= endTime - 2_000L }
            .maxByOrNull { it.lastTimeUsed }
            ?: return null

        return recent.packageName
    }

    fun isPackageInForeground(context: Context, packageName: String): Boolean {
        return getForegroundPackage(context) == packageName
    }

    fun isYouTubeInForeground(context: Context): Boolean {
        if (YouTubeTrackingStateCompat.isYouTubeForeground) return true
        return isPackageInForeground(context, AppPreferencesRepository.YOUTUBE_PACKAGE)
    }
}

/** Avoid circular dependency with YouTubeTrackingState from util package. */
object YouTubeTrackingStateCompat {
    @Volatile
    var isYouTubeForeground: Boolean = false
}
