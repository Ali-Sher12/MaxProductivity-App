package com.maxproductivity.app.ui.main

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxproductivity.app.MaxProductivityApp
import com.maxproductivity.app.YouTubeTimerService
import com.maxproductivity.app.blocking.BlockingStrategy
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import com.maxproductivity.app.data.EXTREMELY_DISTRACTING_PACKAGES
import com.maxproductivity.app.data.OTHER_APP_MAX_LIMIT_MINUTES
import com.maxproductivity.app.data.OTHER_APP_MIN_LIMIT_MINUTES
import com.maxproductivity.app.data.EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES
import com.maxproductivity.app.data.EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES
import com.maxproductivity.app.util.PasswordUtils
import com.maxproductivity.app.util.PermissionStatus
import com.maxproductivity.app.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

val fallbackLabels = mapOf(
    "com.instagram.android" to "Instagram",
    "com.zhiliaoapp.musically" to "TikTok",
    "com.ss.android.ugc.trill" to "TikTok",
    "com.zhiliaoapp.musically.go" to "TikTok Lite",
    "com.reddit.frontpage" to "Reddit",
    "com.snapchat.android" to "Snapchat",
    "com.twitter.android" to "X (Twitter)",
    "com.twitter.android.lite" to "X Lite",
    "com.discord" to "Discord",
    "com.discord.canary" to "Discord Canary",
    "com.facebook.katana" to "Facebook",
    "com.facebook.lite" to "Facebook Lite",
    "com.facebook.orca" to "Messenger",
    "com.instagram.barcelona" to "Threads",
    "com.pinterest" to "Pinterest",
    "tv.twitch.android.app" to "Twitch",
    "xyz.blueskyweb.app" to "Bluesky",
    "com.tumblr" to "Tumblr",
    "com.bereal.ft" to "BeReal",
)

private const val OVERLAY_IMAGE_PREFIX = "block_overlay_image_"

data class BlockableApp(
    val packageName: String,
    val label: String,
    val isExtremelyDistracting: Boolean,
    val isBuiltInExtremelyDistracting: Boolean,
    val isBlocked: Boolean,
    val hasTimer: Boolean,
    val timerMinutes: Int,
    val isTimerLockedForToday: Boolean,
    val isTimerExpired: Boolean,
    val usageSeconds: Long,
    val displayedUsageSeconds: Long,
)

data class MainUiState(
    val isReady: Boolean = false,
    val hasPassword: Boolean = false,
    val isUnlocked: Boolean = false,
    val passwordError: String? = null,
    val showOnboarding: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val permissionStatuses: List<PermissionStatus> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val blockableApps: List<BlockableApp> = emptyList(),
    val isDeviceOwner: Boolean = false,
)

class MainViewModel(
    private val app: MaxProductivityApp,
) : ViewModel() {

    private val repository: AppPreferencesRepository = app.preferencesRepository
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var pendingPermissionContext: Context? = null
    private var cachedBaseApps: List<InstalledApp>? = null

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                val baseApps = getBaseApps()
                val knownPackages = baseApps.map { it.packageName }.toSet()
                val extraEdApps = prefs.userExtremelyDistractingPackages
                    .filter { it !in knownPackages && it != app.packageName }
                    .map { pkg ->
                        InstalledApp(packageName = pkg, label = fallbackLabels[pkg] ?: pkg)
                    }
                val allApps = (baseApps + extraEdApps).map { entry ->
                    val isExtremelyDistracting = prefs.isExtremelyDistracting(entry.packageName)
                    val hasTimer = prefs.hasActiveTimer(entry.packageName)
                    val limitMinutes = prefs.getEffectiveLimitMinutes(entry.packageName)
                    val isExpired = hasTimer && prefs.isTimerExpired(entry.packageName)
                    BlockableApp(
                        packageName = entry.packageName,
                        label = entry.label,
                        isExtremelyDistracting = isExtremelyDistracting,
                        isBuiltInExtremelyDistracting = prefs.isBuiltInExtremelyDistracting(entry.packageName),
                        isBlocked = prefs.blockedPackages.contains(entry.packageName),
                        hasTimer = hasTimer,
                        timerMinutes = limitMinutes ?: 0,
                        isTimerLockedForToday = prefs.isTimerLockedForToday(entry.packageName),
                        isTimerExpired = isExpired,
                        usageSeconds = prefs.getUsageSeconds(entry.packageName),
                        displayedUsageSeconds = prefs.getDisplayedUsageSeconds(entry.packageName),
                    )
                }
                _uiState.update { state ->
                    state.copy(
                        isReady = true,
                        hasPassword = prefs.passwordHash.isNotBlank(),
                        showOnboarding = if (!state.isReady) {
                            !prefs.onboardingCompleted
                        } else {
                            state.showOnboarding
                        },
                        onboardingCompleted = prefs.onboardingCompleted,
                        preferences = prefs,
                        blockableApps = allApps,
                    )
                }
            }
        }
        refreshPermissions()
        updateDeviceOwnerStatus()
    }

    private fun getBaseApps(): List<InstalledApp> {
        cachedBaseApps?.let { return it }
        val loaded = getInstalledAndExtremelyDistractingApps()
        cachedBaseApps = loaded
        return loaded
    }

    private fun getInstalledAndExtremelyDistractingApps(): List<InstalledApp> {
        val pm = app.packageManager
        val installed = mutableMapOf<String, String>()

        // 1. Query all launcher activities
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            installed[pkg] = label
        }

        // 2. Also query installed applications that have a launch intent
        try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (appInfo in apps) {
                if (installed.containsKey(appInfo.packageName)) continue
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    installed[appInfo.packageName] = appInfo.loadLabel(pm).toString()
                }
            }
        } catch (_: Exception) { }

        val myPackageName = app.packageName

        val tiktokPackage = when {
            installed.containsKey("com.ss.android.ugc.trill") -> "com.ss.android.ugc.trill"
            installed.containsKey("com.zhiliaoapp.musically.go") -> "com.zhiliaoapp.musically.go"
            else -> "com.zhiliaoapp.musically"
        }
        val twitterPackage = if (installed.containsKey("com.twitter.android.lite")) {
            "com.twitter.android.lite"
        } else {
            "com.twitter.android"
        }
        val facebookPackage = if (installed.containsKey("com.facebook.lite")) {
            "com.facebook.lite"
        } else {
            "com.facebook.katana"
        }
        val discordPackage = if (installed.containsKey("com.discord.canary")) {
            "com.discord.canary"
        } else {
            "com.discord"
        }

        val targetSocialPackages = mutableSetOf(
            "com.instagram.android",
            "com.reddit.frontpage",
            "com.snapchat.android",
            twitterPackage,
            discordPackage,
            tiktokPackage,
            facebookPackage,
            "com.facebook.orca",
            "com.instagram.barcelona",
            "com.pinterest",
            "tv.twitch.android.app",
        )

        // Extremely Distracting Apps are always shown so their default/configured limits
        // are visible, even if the app is not installed on this device.
        val allPackages = (installed.keys + EXTREMELY_DISTRACTING_PACKAGES + targetSocialPackages)
            .filter {
                if (it == myPackageName) return@filter false
                if (it == "com.ss.android.ugc.trill" || it == "com.zhiliaoapp.musically" || it == "com.zhiliaoapp.musically.go") {
                    return@filter it == tiktokPackage
                }
                if (it == "com.twitter.android" || it == "com.twitter.android.lite") {
                    return@filter it == twitterPackage
                }
                if (it == "com.facebook.katana" || it == "com.facebook.lite") {
                    return@filter it == facebookPackage
                }
                if (it == "com.discord" || it == "com.discord.canary") {
                    return@filter it == discordPackage
                }
                true
            }
            .distinct()

        val isIgnoredInternalPackage: (String, String) -> Boolean = { pkg, label ->
            if (EXTREMELY_DISTRACTING_PACKAGES.contains(pkg) || targetSocialPackages.contains(pkg)) {
                false
            } else {
                pkg.contains(".accessibility") ||
                    pkg.startsWith("com.accessibility.") ||
                    pkg.startsWith("com.android.internal") ||
                    pkg.startsWith("com.google.android.overlay") ||
                    pkg.startsWith("com.samsung.android.overlay") ||
                    label == pkg ||
                    (label.startsWith("com.") && !label.contains(" ")) ||
                    (label.startsWith("android.") && !label.contains(" "))
            }
        }

        return allPackages
            .map { pkg ->
                InstalledApp(
                    packageName = pkg,
                    label = installed[pkg] ?: fallbackLabels[pkg] ?: pkg,
                )
            }
            .filterNot { isIgnoredInternalPackage(it.packageName, it.label) }
            .sortedBy { it.label.lowercase() }
    }

    fun bindContext(context: Context) {
        pendingPermissionContext = context
        refreshPermissions()
        startTimerService(context)
    }

    fun refreshPermissions() {
        val context = pendingPermissionContext ?: app
        _uiState.update {
            it.copy(permissionStatuses = PermissionUtils.getAllStatuses(context))
        }
    }

    fun openPermissionSettings(context: Context, type: com.maxproductivity.app.util.PermissionType) {
        context.startActivity(PermissionUtils.createSettingsIntent(context, type))
    }

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(true) }
        _uiState.update { it.copy(showOnboarding = false, onboardingCompleted = true) }
    }

    fun showOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun hideOnboarding() {
        _uiState.update { it.copy(showOnboarding = false) }
    }

    fun createPassword(password: String) {
        if (password.length < 4) {
            _uiState.update { it.copy(passwordError = "Password must be at least 4 characters.") }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            repository.setPasswordHash(PasswordUtils.hash(password))
            _uiState.update {
                it.copy(
                    hasPassword = true,
                    isUnlocked = true,
                    passwordError = null,
                )
            }
        }
    }

    fun unlock(password: String) {
        val stored = _uiState.value.preferences.passwordHash
        viewModelScope.launch(Dispatchers.Default) {
            if (PasswordUtils.verify(password, stored)) {
                // Silently re-hash if the stored hash uses old iteration count
                if (PasswordUtils.needsRehash(stored)) {
                    repository.setPasswordHash(PasswordUtils.hash(password))
                }
                _uiState.update { it.copy(isUnlocked = true, passwordError = null) }
            } else {
                _uiState.update { it.copy(passwordError = "Incorrect password.") }
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        val stored = _uiState.value.preferences.passwordHash
        if (newPassword.length < 4) {
            _uiState.update { it.copy(passwordError = "New password must be at least 4 characters.") }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            if (!PasswordUtils.verify(currentPassword, stored)) {
                _uiState.update { it.copy(passwordError = "Current password is incorrect.") }
                return@launch
            }
            repository.setPasswordHash(PasswordUtils.hash(newPassword))
            _uiState.update { it.copy(passwordError = null) }
        }
    }

    fun clearPasswordError() {
        _uiState.update { it.copy(passwordError = null) }
    }

    fun lock() {
        _uiState.update { it.copy(isUnlocked = false, passwordError = null) }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockingEnabled(enabled) }
    }

    fun toggleBlockedPackage(packageName: String, blocked: Boolean) {
        val appState = _uiState.value.blockableApps.find { it.packageName == packageName } ?: return
        if (appState.isTimerExpired) return
        if (!appState.isExtremelyDistracting && appState.hasTimer) return

        viewModelScope.launch {
            val current = _uiState.value.preferences.blockedPackages
            val updated = if (blocked) current + packageName else current - packageName
            repository.setBlockedPackages(updated)
        }
    }

    fun setAppTimerLimit(packageName: String, minutes: Int) {
        val appState = _uiState.value.blockableApps.find { it.packageName == packageName } ?: return
        if (appState.isBlocked) return
        if (appState.isTimerExpired) return
        if (appState.isExtremelyDistracting && appState.isTimerLockedForToday) return

        val clamped = if (appState.isExtremelyDistracting) {
            minutes.coerceIn(EXTREMELY_DISTRACTING_MIN_LIMIT_MINUTES, EXTREMELY_DISTRACTING_MAX_LIMIT_MINUTES)
        } else {
            minutes.coerceIn(OTHER_APP_MIN_LIMIT_MINUTES, OTHER_APP_MAX_LIMIT_MINUTES)
        }
        viewModelScope.launch { repository.setTimer(packageName, clamped) }
    }

    fun removeAppTimer(packageName: String) {
        val appState = _uiState.value.blockableApps.find { it.packageName == packageName } ?: return
        if (appState.isExtremelyDistracting) return
        if (appState.isTimerExpired) return
        viewModelScope.launch { repository.removeTimer(packageName) }
    }

    fun setNightBlockEdApps(enabled: Boolean) {
        viewModelScope.launch { repository.setNightBlockEdApps(enabled) }
    }

    fun addExtremelyDistractingApp(packageName: String) {
        if (packageName.isBlank() || packageName == app.packageName) return
        viewModelScope.launch { repository.addUserExtremelyDistractingPackage(packageName) }
    }

    fun removeExtremelyDistractingApp(packageName: String) {
        if (EXTREMELY_DISTRACTING_PACKAGES.contains(packageName)) return
        viewModelScope.launch { repository.removeUserExtremelyDistractingPackage(packageName) }
    }

    fun setReminderText(text: String) {
        viewModelScope.launch { repository.setReminderText(text) }
    }

    fun setTimerLimitMessage(text: String) {
        viewModelScope.launch { repository.setTimerLimitMessage(text) }
    }

    fun setExtremelyDistractingLimitMessage(text: String) {
        viewModelScope.launch { repository.setExtremelyDistractingLimitMessage(text) }
    }

    fun setBackgroundImageUri(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uri.isBlank()) {
                app.filesDir.listFiles()
                    ?.filter { it.name.startsWith(OVERLAY_IMAGE_PREFIX) }
                    ?.forEach { it.delete() }
                repository.setBackgroundImageUri("")
                return@launch
            }

            val parsedUri = Uri.parse(uri)
            val target = File(app.filesDir, "$OVERLAY_IMAGE_PREFIX${System.currentTimeMillis()}")
            try {
                app.contentResolver.openInputStream(parsedUri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists() && target.length() > 0L) {
                    app.filesDir.listFiles()
                        ?.filter { it.name.startsWith(OVERLAY_IMAGE_PREFIX) && it != target }
                        ?.forEach { it.delete() }
                    repository.setBackgroundImageUri(Uri.fromFile(target).toString())
                } else {
                    target.delete()
                    repository.setBackgroundImageUri(uri)
                }
            } catch (_: Exception) {
                target.delete()
                // Fall back to the original URI; Coil may still be able to read it.
                repository.setBackgroundImageUri(uri)
            }
        }
    }

    fun setBlockingStrategy(strategy: BlockingStrategy) {
        viewModelScope.launch { repository.setBlockingStrategy(strategy) }
    }

    fun setMotto(text: String) {
        viewModelScope.launch { repository.setMotto(text) }
    }

    private fun startTimerService(context: Context) {
        val intent = Intent(context, YouTubeTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateDeviceOwnerStatus() {
        val dpm = app.getSystemService(DevicePolicyManager::class.java)
        _uiState.update {
            it.copy(isDeviceOwner = dpm?.isDeviceOwnerApp(app.packageName) == true)
        }
    }

    private data class InstalledApp(
        val packageName: String,
        val label: String,
    )
}
