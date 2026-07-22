package com.maxproductivity.app.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maxproductivity.app.blocking.BlockingStrategy
import com.maxproductivity.app.data.ALWAYS_BLOCKED_PACKAGES
import kotlin.math.floor

@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggleBlocking: (Boolean) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit,
    onAppTimerChange: (String, Int?) -> Unit,
    onYoutubeLimitChange: (Int) -> Unit,
    onReminderTextChange: (String) -> Unit,
    onBackgroundImageChange: (String) -> Unit,
    onBlockingStrategyChange: (BlockingStrategy) -> Unit,
    onToggleShortsBlocking: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    var reminderText by rememberSaveable(uiState.preferences.reminderText) {
        mutableStateOf(uiState.preferences.reminderText)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    var showAppManagementDialog by remember { mutableStateOf(false) }
    var appToConfirmTimer by remember { mutableStateOf<BlockableApp?>(null) }
    var tempTimerMinutes by remember { mutableStateOf(30) }
    
    var youtubeLimitToConfirm by remember { mutableStateOf<Int?>(null) }

    val today = java.time.LocalDate.now().toString()
    val isYoutubeLimitLocked = uiState.preferences.timerLastModifiedDates["com.google.android.youtube"] == today

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { onBackgroundImageChange(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Focus Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onOpenOnboarding) {
                Text("Permissions")
            }
        }

        SettingsCard(title = "Blocking") {
            SettingRow(
                title = "Enable blocking",
                subtitle = "Intercept distracting apps and YouTube limits",
                checked = uiState.preferences.blockingEnabled,
                onCheckedChange = onToggleBlocking,
            )
        }

        SettingsCard(title = "App Blocklist & Timers") {
            val activeApps = uiState.blockableApps.filter {
                it.isBlocked || it.hasTimer
            }

            if (activeApps.isEmpty()) {
                Text(
                    text = "No active blocks or timers. Click 'Configure App Limits' below to configure apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Column {
                    activeApps.forEachIndexed { index, app ->
                        BlockableAppRow(
                            app = app,
                            usageSeconds = uiState.appUsageSeconds[app.packageName] ?: 0L,
                            isTimerLocked = uiState.preferences.timerLastModifiedDates[app.packageName] == today,
                            onTogglePackage = onTogglePackage,
                            onAppTimerChange = { pkg, minutes ->
                                if (uiState.preferences.timerLastModifiedDates[pkg] != today) {
                                    appToConfirmTimer = app.copy(hasTimer = minutes != null, timerMinutes = minutes ?: 30)
                                    tempTimerMinutes = minutes ?: 30
                                }
                            }
                        )
                        if (index < activeApps.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showAppManagementDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Configure App Limits", style = MaterialTheme.typography.titleMedium)
            }
        }

        SettingsCard(title = "YouTube") {
            Text(
                text = "Shorts are always blocked by design.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!isYoutubeLimitLocked) {
                var localLimitInt by rememberSaveable(uiState.preferences.youtubeDailyLimitMinutes) {
                    mutableStateOf(uiState.preferences.youtubeDailyLimitMinutes)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Daily limit: ${localLimitInt}m", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = localLimitInt.toFloat(),
                            onValueChange = {
                                localLimitInt = it.toInt().coerceIn(5, 120)
                            },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                    }
                    Button(
                        onClick = {
                            youtubeLimitToConfirm = localLimitInt
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Set")
                    }
                }
            } else {
                Text(
                    text = "Daily limit: ${uiState.preferences.youtubeDailyLimitMinutes}m (Locked for today)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Daily limit can only be changed once per day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val usedMinutes = floor(uiState.youtubeUsageSeconds / 60.0).toInt()
            Text(
                text = "Used today: ${usedMinutes}m (${uiState.youtubeUsageSeconds}s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        SettingsCard(title = "Block Overlay") {
            OutlinedTextField(
                value = reminderText,
                onValueChange = {
                    reminderText = it
                    onReminderTextChange(it)
                },
                label = { Text("Reminder text") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { imagePicker.launch("image/*") }) {
                Text(
                    if (uiState.preferences.backgroundImageUri.isBlank()) {
                        "Pick background image"
                    } else {
                        "Change background image"
                    },
                )
            }
        }

        SettingsCard(title = "Blocking Strategy") {
            Text(
                text = if (uiState.isDeviceOwner) {
                    "Device Owner mode is active. Zero-flash blocking is available."
                } else {
                    "Use Accessibility mode by default. Enable Device Owner via ADB for zero-flash blocking."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.preferences.blockingStrategy == BlockingStrategy.ACCESSIBILITY,
                    onClick = { onBlockingStrategyChange(BlockingStrategy.ACCESSIBILITY) },
                    label = { Text("Accessibility") },
                )
                FilterChip(
                    selected = uiState.preferences.blockingStrategy == BlockingStrategy.DEVICE_OWNER,
                    onClick = { onBlockingStrategyChange(BlockingStrategy.DEVICE_OWNER) },
                    label = { Text("Device Owner") },
                    enabled = uiState.isDeviceOwner,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ADB: adb shell dpm set-device-owner com.maxproductivity.app/com.maxproductivity.app.receiver.MyDeviceAdminReceiver",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        TextButton(onClick = onRefreshPermissions, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh permission status")
        }
    }

    if (showAppManagementDialog) {
        Dialog(
            onDismissRequest = { showAppManagementDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configure Apps",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showAppManagementDialog = false }) {
                            Text("Done")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search installed apps...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredApps = uiState.blockableApps.filter {
                        it.label.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No apps found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isTimerLocked = uiState.preferences.timerLastModifiedDates[app.packageName] == today
                                BlockableAppRow(
                                    app = app,
                                    usageSeconds = uiState.appUsageSeconds[app.packageName] ?: 0L,
                                    isTimerLocked = isTimerLocked,
                                    onTogglePackage = onTogglePackage,
                                    onAppTimerChange = { pkg, minutes ->
                                        if (!isTimerLocked) {
                                            appToConfirmTimer = app.copy(hasTimer = minutes != null, timerMinutes = minutes ?: 30)
                                            tempTimerMinutes = minutes ?: 30
                                        }
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (appToConfirmTimer != null) {
        val app = appToConfirmTimer!!
        AlertDialog(
            onDismissRequest = { appToConfirmTimer = null },
            title = {
                Text(
                    text = if (app.hasTimer) "Set Daily Limit" else "Disable Daily Limit",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (app.hasTimer) {
                            "Are you sure you want to set a daily limit for ${app.label}? You can only change this limit once per day."
                        } else {
                            "Are you sure you want to disable the daily limit for ${app.label}? You cannot set it again today."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (app.hasTimer) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Limit: ${tempTimerMinutes}m", style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(
                            value = tempTimerMinutes.toFloat(),
                            onValueChange = { tempTimerMinutes = it.toInt().coerceIn(5, 120) },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAppTimerChange(app.packageName, if (app.hasTimer) tempTimerMinutes else null)
                        appToConfirmTimer = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm & Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { appToConfirmTimer = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (youtubeLimitToConfirm != null) {
        val limit = youtubeLimitToConfirm!!
        AlertDialog(
            onDismissRequest = { youtubeLimitToConfirm = null },
            title = { Text("Confirm YouTube Daily Limit", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to set a daily limit of $limit minutes for normal YouTube usage? You can only change this limit once per day.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onYoutubeLimitChange(limit)
                        youtubeLimitToConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm & Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { youtubeLimitToConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BlockableAppRow(
    app: BlockableApp,
    usageSeconds: Long,
    isTimerLocked: Boolean,
    onTogglePackage: (String, Boolean) -> Unit,
    onAppTimerChange: (String, Int?) -> Unit,
) {
    val isAlwaysBlocked = ALWAYS_BLOCKED_PACKAGES.contains(app.packageName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Switch(
                checked = app.isBlocked,
                onCheckedChange = { onTogglePackage(app.packageName, it) },
                enabled = !isAlwaysBlocked
            )
        }

        if (!isAlwaysBlocked) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Checkbox(
                        checked = app.hasTimer,
                        onCheckedChange = { checked ->
                            onAppTimerChange(app.packageName, if (checked) 30 else null)
                        },
                        enabled = !isTimerLocked
                    )
                    Text("Daily Timer", style = MaterialTheme.typography.bodyMedium)
                }

                if (app.hasTimer) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Limit: ${app.timerMinutes}m",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val usedMinutes = (usageSeconds / 60).toInt()
                            Text(
                                text = "Used: ${usedMinutes}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                        Slider(
                            value = app.timerMinutes.toFloat(),
                            onValueChange = {
                                onAppTimerChange(app.packageName, it.toInt().coerceIn(5, 120))
                            },
                            valueRange = 5f..120f,
                            steps = 22,
                            enabled = !isTimerLocked
                        )
                    }
                }
            }
            if (isTimerLocked) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Limit changed today. Locked until tomorrow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Always blocked by design (cannot be disabled or timed).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

