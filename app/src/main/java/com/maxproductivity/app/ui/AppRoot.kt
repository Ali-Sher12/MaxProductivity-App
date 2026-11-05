package com.maxproductivity.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maxproductivity.app.ui.auth.CreatePasswordScreen
import com.maxproductivity.app.ui.auth.UnlockScreen
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

    when {
        !uiState.isReady -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        !uiState.hasPassword -> {
            CreatePasswordScreen(
                error = uiState.passwordError,
                onCreatePassword = viewModel::createPassword,
            )
        }

        !uiState.isUnlocked -> {
            UnlockScreen(
                error = uiState.passwordError,
                onUnlock = viewModel::unlock,
            )
        }

        uiState.showOnboarding -> {
            OnboardingScreen(
                permissionStatuses = uiState.permissionStatuses,
                onOpenPermission = onOpenPermission,
                onContinue = viewModel::completeOnboarding,
                onBack = if (uiState.onboardingCompleted) viewModel::hideOnboarding else null,
            )
        }

        else -> {
            MainScreen(
                uiState = uiState,
                onTogglePackage = viewModel::toggleBlockedPackage,
                onAppTimerChange = viewModel::setAppTimerLimit,
                onRemoveTimer = viewModel::removeAppTimer,
                onReminderTextChange = viewModel::setReminderText,
                onTimerLimitMessageChange = viewModel::setTimerLimitMessage,
                onExtremelyDistractingLimitMessageChange = viewModel::setExtremelyDistractingLimitMessage,
                onMottoChange = viewModel::setMotto,
                onBackgroundImageChange = viewModel::setBackgroundImageUri,
                onRefreshPermissions = viewModel::refreshPermissions,
                onOpenOnboarding = viewModel::showOnboarding,
                onLock = viewModel::lock,
                onChangePassword = viewModel::changePassword,
                onClearPasswordError = viewModel::clearPasswordError,
                onNightBlockChange = viewModel::setNightBlockEdApps,
                onAddExtremelyDistracting = viewModel::addExtremelyDistractingApp,
                onRemoveExtremelyDistracting = viewModel::removeExtremelyDistractingApp,
            )
        }
    }
}
