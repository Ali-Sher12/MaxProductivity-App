package com.maxproductivity.app.ui.main

import android.app.admin.DevicePolicyManager
import com.maxproductivity.app.MaxProductivityApp
import com.maxproductivity.app.YouTubeTimerService
import com.maxproductivity.app.blocking.BlockingStrategy
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.data.AppPreferencesRepository
import com.maxproductivity.app.data.ALWAYS_BLOCKED_PACKAGES
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
import android.os.Build
import android.content.Context

val fallbackLabels = mapOf(
    "com.snapchat.android" to "Snapchat",
    "com.instagram.android" to "Instagram",
    "com.zhiliaoapp.musically" to "TikTok",
    "com.reddit.frontpage" to "Reddit",
    "com.discord" to "Discord"
)

data class BlockableApp(
    val packageName: String,
    val label: String,
    val isBlocked: Boolean,
    val hasTimer: Boolean = false,
    val timerMinutes: Int = 30,
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

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                val allApps = getInstalledAndAlwaysBlockedApps().map { app ->
                    val isAlwaysBlocked = ALWAYS_BLOCKED_PACKAGES.contains(app.packageName)
                    val isBlocked = isAlwaysBlocked || prefs.blockedPackages.contains(app.packageName)
                    val limitMinutes = prefs.timerLimits[app.packageName]
                    val hasTimer = limitMinutes != null && limitMinutes > 0
                    val timerMinutes = limitMinutes ?: 30
                    app.copy(
                        isBlocked = isBlocked,
                        hasTimer = hasTimer && !isAlwaysBlocked,
                        timerMinutes = timerMinutes
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

    private fun getInstalledAndAlwaysBlockedApps(): List<BlockableApp> {
        val pm = app.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val installed = resolveInfos.map {
            val pkg = it.activityInfo.packageName
            val label = it.loadLabel(pm).toString()
            pkg to label
        }.toMap()

        val myPackageName = app.packageName

        val allPackages = (installed.keys + ALWAYS_BLOCKED_PACKAGES)
            .filter { it != myPackageName }
            .sortedBy {
                installed[it] ?: fallbackLabels[it] ?: it
            }

        return allPackages.map { pkg ->
            BlockableApp(
                packageName = pkg,
                label = installed[pkg] ?: fallbackLabels[pkg] ?: pkg,
                isBlocked = ALWAYS_BLOCKED_PACKAGES.contains(pkg),
                hasTimer = false,
                timerMinutes = 30
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
        viewModelScope.launch { repository.setBackgroundImageUri(uri) }
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
