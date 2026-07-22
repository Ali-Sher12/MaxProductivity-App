package com.maxproductivity.app.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.maxproductivity.app.FocusAccessibilityService

enum class PermissionType {
    ACCESSIBILITY,
    OVERLAY,
    USAGE_ACCESS,
    BATTERY_OPTIMIZATION,
}

data class PermissionStatus(
    val type: PermissionType,
    val isGranted: Boolean,
    val title: String,
    val description: String,
)

object PermissionUtils {

    fun getAllStatuses(context: Context): List<PermissionStatus> = listOf(
        PermissionStatus(
            type = PermissionType.ACCESSIBILITY,
            isGranted = isAccessibilityServiceEnabled(context),
            title = "Accessibility Service",
            description = "Required to detect YouTube Shorts and block distracting apps.",
        ),
        PermissionStatus(
            type = PermissionType.OVERLAY,
            isGranted = Settings.canDrawOverlays(context),
            title = "Display Over Other Apps",
            description = "Required to show the custom block reminder overlay.",
        ),
        PermissionStatus(
            type = PermissionType.USAGE_ACCESS,
            isGranted = hasUsageAccess(context),
            title = "Usage Access",
            description = "Required for the foreground service to track YouTube usage time.",
        ),
        PermissionStatus(
            type = PermissionType.BATTERY_OPTIMIZATION,
            isGranted = isBatteryOptimizationDisabled(context),
            title = "Battery Optimization Exemption",
            description = "Prevents Android from stopping background focus services.",
        ),
    )

    fun allGranted(context: Context): Boolean =
        getAllStatuses(context).all { it.isGranted }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = "${context.packageName}/${FocusAccessibilityService::class.java.name}"
        return enabledServices.split(':').any { TextUtils.equals(it, expected) }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createSettingsIntent(context: Context, type: PermissionType): Intent = when (type) {
        PermissionType.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PermissionType.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        PermissionType.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        PermissionType.BATTERY_OPTIMIZATION -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
    }
}
