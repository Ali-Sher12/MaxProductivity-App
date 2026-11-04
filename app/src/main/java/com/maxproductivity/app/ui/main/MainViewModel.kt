package com.maxproductivity.app.ui.main

import android.app.admin.DevicePolicyManager
import com.maxproductivity.app.MaxProductivityApp
import com.maxproductivity.app.YouTubeTimerService
import com.maxproductivity.app.blocking.BlockingStrategy
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import com.maxproductivity.app.data.LIMITED_SOCIAL_PACKAGES
import com.maxproductivity.app.data.LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES
import com.maxproductivity.app.data.LIMITED_SOCIAL_MIN_LIMIT_MINUTES
import com.maxproductivity.app.data.LIMITED_SOCIAL_MAX_LIMIT_MINUTES
import com.maxproductivity.app.util.PermissionStatus
import com.maxproductivity.app.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.Context

val fallbackLabels = mapOf(
    "com.instagram.android" to "Instagram",
    "com.zhiliaoapp.musically" to "TikTok",
    "com.ss.android.ugc.trill" to "TikTok",
    "com.reddit.frontpage" to "Reddit",
    "com.snapchat.android" to "Snapchat",
    "com.twitter.android" to "Twitter",
)

data class BlockableApp(
    val packageName: String,
    val label: String,
    val isBlocked: Boolean,
    val hasTimer: Boolean = false,
    val timerMinutes: Int = 30,
    val isLimitedSocial: Boolean = false,
)

data class MainUiState(
    val showOnboarding: Boolean = true,
    val permissionStatuses: List<PermissionStatus> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val blockableApps: List<BlockableApp> = emptyList(),
    val youtubeUsageSeconds: Long = 0L,
    val appUsageSeconds: Map<String, Long> = emptyMap(),
    val isDeviceOwner: Boolean = false,
)

class MainViewModel(
    private val app: MaxProductivityApp,
) : ViewModel() {

    private val repository: AppPreferencesRepository = app.preferencesRepository
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var pendingPermissionContext: Context? = null
    private var cachedBaseApps: List<BlockableApp>? = null

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                val allApps = getBaseApps().map { app ->
                    val isLimited = LIMITED_SOCIAL_PACKAGES.contains(app.packageName)
                    val isBlocked = prefs.blockedPackages.contains(app.packageName)
                    val limitMinutes = if (isLimited) {
                        (prefs.timerLimits[app.packageName] ?: LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES)
                            .coerceIn(LIMITED_SOCIAL_MIN_LIMIT_MINUTES, LIMITED_SOCIAL_MAX_LIMIT_MINUTES)
                    } else {
                        prefs.timerLimits[app.packageName]
                    }
                    val hasTimer = isLimited || (limitMinutes != null && limitMinutes > 0)
                    val timerMinutes = limitMinutes ?: 15
                    app.copy(
                        isBlocked = isBlocked,
                        hasTimer = hasTimer,
                        timerMinutes = timerMinutes,
                        isLimitedSocial = isLimited,
                    )
                }
                _uiState.update { state ->
                    state.copy(
                        preferences = prefs,
                        blockableApps = allApps,
                        youtubeUsageSeconds = prefs.youtubeUsageSeconds,
                        appUsageSeconds = prefs.appUsageSeconds,
                        showOnboarding = state.showOnboarding && !PermissionUtils.allGranted(app),
                    )
                }
            }
        }
        refreshPermissions()
        updateDeviceOwnerStatus()
    }

    private fun getBaseApps(): List<BlockableApp> {
        cachedBaseApps?.let { return it }
        val loaded = getInstalledAndAlwaysBlockedApps()
        cachedBaseApps = loaded
        return loaded
    }

    private fun getInstalledAndAlwaysBlockedApps(): List<BlockableApp> {
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

        // Deduplicate TikTok: if com.ss.android.ugc.trill is installed, use it;
        // otherwise only keep com.zhiliaoapp.musically (never show both).
        val targetSocialPackages = mutableSetOf(
            "com.instagram.android",
            "com.reddit.frontpage",
            "com.snapchat.android",
            "com.twitter.android",
        )
        if (installed.containsKey("com.ss.android.ugc.trill")) {
            targetSocialPackages.add("com.ss.android.ugc.trill")
        } else {
            targetSocialPackages.add("com.zhiliaoapp.musically")
        }

        val allPackages = (installed.keys + targetSocialPackages)
            .filter { it != myPackageName && (it != "com.ss.android.ugc.trill" || installed.containsKey("com.ss.android.ugc.trill")) }
            .distinct()
            .sortedBy {
                (installed[it] ?: fallbackLabels[it] ?: it).lowercase()
            }

        return allPackages.map { pkg ->
            val isLimited = LIMITED_SOCIAL_PACKAGES.contains(pkg)
            BlockableApp(
                packageName = pkg,
                label = installed[pkg] ?: fallbackLabels[pkg] ?: pkg,
                isBlocked = false,
                hasTimer = isLimited,
                timerMinutes = if (isLimited) LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES else 15,
                isLimitedSocial = isLimited,
            )
        }
    }

    fun bindContext(context: Context) {
        pendingPermissionContext = context
        refreshPermissions()
        startTimerService(context)
    }

    fun refreshPermissions() {
        val context = pendingPermissionContext ?: app
        _uiState.update {
            it.copy(
                permissionStatuses = PermissionUtils.getAllStatuses(context),
                showOnboarding = it.showOnboarding && !PermissionUtils.allGranted(context),
            )
        }
    }

    fun openPermissionSettings(context: Context, type: com.maxproductivity.app.util.PermissionType) {
        context.startActivity(PermissionUtils.createSettingsIntent(context, type))
    }

    fun completeOnboarding() {
        _uiState.update { it.copy(showOnboarding = false) }
    }

    fun showOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockingEnabled(enabled) }
    }

    fun toggleBlockedPackage(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.preferences.blockedPackages
            val updated = if (blocked) current + packageName else current - packageName
            repository.setBlockedPackages(updated)
        }
    }

    fun setAppTimerLimit(packageName: String, minutes: Int?) {
        val today = java.time.LocalDate.now().toString()
        if (_uiState.value.preferences.timerLastModifiedDates[packageName] == today) {
            return
        }
        viewModelScope.launch {
            repository.setTimerLimitAndRecordChange(packageName, minutes)
        }
    }

    fun setYoutubeLimit(minutes: Int) {
        viewModelScope.launch {
            repository.setYoutubeDailyLimitAndRecordChange(minutes)
        }
    }

    fun setReminderText(text: String) {
        viewModelScope.launch { repository.setReminderText(text) }
    }

    fun setBackgroundImageUri(uri: String) {
        viewModelScope.launch {
            val parsedUri = Uri.parse(uri)
            try {
                app.contentResolver.takePersistableUriPermission(
                    parsedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // OpenDocument URIs should support this; ignore if unavailable.
            }
            repository.setBackgroundImageUri(uri)
        }
    }

    fun setBlockingStrategy(strategy: BlockingStrategy) {
        viewModelScope.launch { repository.setBlockingStrategy(strategy) }
    }

    fun setBlockYouTubeShorts(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockYouTubeShorts(enabled) }
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
}
