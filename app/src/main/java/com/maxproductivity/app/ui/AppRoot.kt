package com.maxproductivity.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maxproductivity.app.ui.main.MainScreen
import com.maxproductivity.app.ui.main.MainViewModel
import com.maxproductivity.app.ui.onboarding.OnboardingScreen
import com.maxproductivity.app.util.PermissionType

@Composable
fun AppRoot(
    viewModel: MainViewModel = viewModel(),
    onOpenPermission: (PermissionType) -> Unit,
    onBind: () -> Unit,
) {
    DisposableEffect(Unit) {
        onBind()
        onDispose { }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showOnboarding) {
        OnboardingScreen(
            permissionStatuses = uiState.permissionStatuses,
            onOpenPermission = onOpenPermission,
            onContinue = viewModel::completeOnboarding,
        )
    } else {
        MainScreen(
            uiState = uiState,
            onToggleBlocking = viewModel::setBlockingEnabled,
            onTogglePackage = viewModel::toggleBlockedPackage,
            onAppTimerChange = viewModel::setAppTimerLimit,
            onYoutubeLimitChange = viewModel::setYoutubeLimit,
            onReminderTextChange = viewModel::setReminderText,
            onBackgroundImageChange = viewModel::setBackgroundImageUri,
            onBlockingStrategyChange = viewModel::setBlockingStrategy,
            onToggleShortsBlocking = viewModel::setBlockYouTubeShorts,
            onRefreshPermissions = viewModel::refreshPermissions,
            onOpenOnboarding = viewModel::showOnboarding,
        )
    }
}
