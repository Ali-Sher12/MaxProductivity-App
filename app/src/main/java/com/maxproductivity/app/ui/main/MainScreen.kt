package com.maxproductivity.app.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.maxproductivity.app.blocking.BlockingStrategy
import com.maxproductivity.app.data.LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES
import com.maxproductivity.app.data.LIMITED_SOCIAL_MAX_LIMIT_MINUTES
import com.maxproductivity.app.data.LIMITED_SOCIAL_MIN_LIMIT_MINUTES
import com.maxproductivity.app.data.OTHER_APP_MAX_LIMIT_MINUTES
import com.maxproductivity.app.data.OTHER_APP_MIN_LIMIT_MINUTES

@Composable
fun MainScreen(
    uiState: MainUiState,
    onTogglePackage: (String, Boolean) -> Unit,
    onAppTimerChange: (String, Int) -> Unit,
    onRemoveTimer: (String) -> Unit,
    onReminderTextChange: (String) -> Unit,
    onTimerLimitMessageChange: (String) -> Unit,
    onExtremelyDistractingLimitMessageChange: (String) -> Unit,
    onMottoChange: (String) -> Unit,
    onBackgroundImageChange: (String) -> Unit,
    onBlockingStrategyChange: (BlockingStrategy) -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onLock: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onClearPasswordError: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopBar(
                motto = uiState.preferences.motto,
                onOpenOnboarding = onOpenOnboarding,
                onLock = onLock,
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                    label = { Text("Block apps") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                    label = { Text("Timers") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    label = { Text("Pop-ups") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                0 -> BlockAppsTab(
                    uiState = uiState,
                    onTogglePackage = onTogglePackage,
                )

                1 -> TimersTab(
                    uiState = uiState,
                    onAppTimerChange = onAppTimerChange,
                    onRemoveTimer = onRemoveTimer,
                )

                else -> PopUpsTab(
                    uiState = uiState,
                    onReminderTextChange = onReminderTextChange,
                    onTimerLimitMessageChange = onTimerLimitMessageChange,
                    onExtremelyDistractingLimitMessageChange = onExtremelyDistractingLimitMessageChange,
                    onMottoChange = onMottoChange,
                    onBackgroundImageChange = onBackgroundImageChange,
                    onBlockingStrategyChange = onBlockingStrategyChange,
                    onRefreshPermissions = onRefreshPermissions,
                    onOpenOnboarding = onOpenOnboarding,
                    onChangePassword = onChangePassword,
                    onClearPasswordError = onClearPasswordError,
                )
            }
        }
    }
}

@Composable
private fun MainTopBar(
    motto: String,
    onOpenOnboarding: () -> Unit,
    onLock: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LongTerm",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = motto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            TextButton(onClick = onOpenOnboarding) {
                Text("Permissions")
            }
            TextButton(onClick = onLock) {
                Text("Lock")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Block apps
// ---------------------------------------------------------------------------------------------

@Composable
private fun BlockAppsTab(
    uiState: MainUiState,
    onTogglePackage: (String, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val apps = uiState.blockableApps.filter { !it.isExtremelyDistracting }
    val filtered = apps.filter {
        it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "Check an app to block it. Extremely Distracting Apps are managed in the Timers tab and can never be unblocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState("No apps found.")
            }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                BlockAppRow(app = app, onTogglePackage = onTogglePackage)
            }
        }
    }
}

@Composable
private fun BlockAppRow(
    app: BlockableApp,
    onTogglePackage: (String, Boolean) -> Unit,
) {
    val enabled = !app.isExtremelyDistracting && !app.hasTimer && !app.isTimerExpired
    val checked = app.isBlocked || app.isTimerExpired
    val status = when {
        app.isTimerExpired -> "Daily limit reached — locked until tomorrow"
        app.hasTimer -> "Timer active: ${app.timerMinutes}m — manage in Timers"
        app.isBlocked -> "Blocked"
        else -> "Allowed"
    }
    val statusColor = when {
        app.isTimerExpired -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
        app.hasTimer -> MaterialTheme.colorScheme.primary
        app.isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (app.hasTimer || app.isTimerExpired) 0.62f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Checkbox(
                checked = checked,
                onCheckedChange = { onTogglePackage(app.packageName, it) },
                enabled = enabled,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Timers
// ---------------------------------------------------------------------------------------------

@Composable
private fun TimersTab(
    uiState: MainUiState,
    onAppTimerChange: (String, Int) -> Unit,
    onRemoveTimer: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var dialogApp by remember { mutableStateOf<BlockableApp?>(null) }

    val filteredApps = uiState.blockableApps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true)
    }
    val extremelyDistractingApps = filteredApps.filter { it.isExtremelyDistracting }
    val otherAppsWithTimer = filteredApps.filter { !it.isExtremelyDistracting && it.hasTimer }
    val otherAppsWithoutTimer = filteredApps.filter { !it.isExtremelyDistracting && !it.hasTimer }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard(
                title = "Daily timers",
                text = "A timer can be set or changed once per day. The limit carries over until you change it. Blocked apps must be unblocked before a timer can be set.",
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (extremelyDistractingApps.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Extremely Distracting Apps",
                    subtitle = "Always timed (1–20m, default 15m). Cannot be unblocked.",
                )
            }
            items(extremelyDistractingApps, key = { "distracting-${it.packageName}" }) { app ->
                ExtremelyDistractingTimerCard(app = app, onSetTimer = { dialogApp = app })
            }
        }

        if (otherAppsWithTimer.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Active Timers",
                    subtitle = "Apps currently governed by a daily time limit.",
                )
            }
            items(otherAppsWithTimer, key = { "active-${it.packageName}" }) { app ->
                OtherTimerCard(
                    app = app,
                    onSetTimer = { dialogApp = app },
                    onRemoveTimer = { onRemoveTimer(app.packageName) },
                )
            }
        }

        if (otherAppsWithoutTimer.isNotEmpty()) {
            item {
                SectionHeader(
                    title = if (otherAppsWithTimer.isEmpty()) "Other Apps" else "Available Apps",
                    subtitle = "Set daily limits (1–30m).",
                )
            }
            items(otherAppsWithoutTimer, key = { "available-${it.packageName}" }) { app ->
                OtherTimerCard(
                    app = app,
                    onSetTimer = { dialogApp = app },
                    onRemoveTimer = { onRemoveTimer(app.packageName) },
                )
            }
        }

        if (extremelyDistractingApps.isEmpty() && otherAppsWithTimer.isEmpty() && otherAppsWithoutTimer.isEmpty()) {
            item {
                EmptyState("No apps matching \"$query\"")
            }
        }
    }

    val app = dialogApp
    if (app != null) {
        TimerDialog(
            app = app,
            onDismiss = { dialogApp = null },
            onConfirm = { minutes ->
                onAppTimerChange(app.packageName, minutes)
                dialogApp = null
            },
        )
    }
}

@Composable
private fun ExtremelyDistractingTimerCard(
    app: BlockableApp,
    onSetTimer: () -> Unit,
) {
    val usedMinutes = (app.displayedUsageSeconds / 60).toInt()
    val remainingMinutes = (app.timerMinutes - usedMinutes).coerceAtLeast(0)
    val locked = app.isTimerLockedForToday
    val expired = app.isTimerExpired
    val progress = if (app.timerMinutes > 0) {
        (usedMinutes.toFloat() / app.timerMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (expired) 0.7f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "${app.timerMinutes}m limit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Used: ${usedMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = if (expired) "Limit reached" else "${remainingMinutes}m remaining",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (expired) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        expired -> "Locked until tomorrow"
                        locked -> "Changed today (locked)"
                        else -> "Can change once today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expired) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onSetTimer,
                    enabled = !locked && !expired,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (locked) "Locked" else "Set Timer")
                }
            }
        }
    }
}

@Composable
private fun OtherTimerCard(
    app: BlockableApp,
    onSetTimer: () -> Unit,
    onRemoveTimer: () -> Unit,
) {
    val usedMinutes = (app.displayedUsageSeconds / 60).toInt()
    val remainingMinutes = (app.timerMinutes - usedMinutes).coerceAtLeast(0)
    val locked = app.isTimerLockedForToday
    val expired = app.isTimerExpired
    val blocked = app.isBlocked
    val progress = if (app.hasTimer && app.timerMinutes > 0) {
        (usedMinutes.toFloat() / app.timerMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (blocked || expired) 0.65f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.hasTimer) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${app.timerMinutes}m daily",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (blocked) {
                Text(
                    text = "App is blocked. Unblock it in Block apps to set a timer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                )
            } else if (app.hasTimer) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Used: ${usedMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = if (expired) "Limit reached" else "${remainingMinutes}m remaining",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            expired -> "Locked until tomorrow"
                            locked -> "Changed today (locked)"
                            else -> "Range 1–30m"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (expired) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (!expired) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onSetTimer,
                                enabled = !locked,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Change")
                            }
                            OutlinedButton(
                                onClick = onRemoveTimer,
                                enabled = !locked,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (locked) "Timer removed today (locked)" else "No timer set • Range 1–30m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onSetTimer,
                        enabled = !locked,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Set Timer")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDialog(
    app: BlockableApp,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val isExtremelyDistracting = app.isExtremelyDistracting
    val min = if (isExtremelyDistracting) LIMITED_SOCIAL_MIN_LIMIT_MINUTES else OTHER_APP_MIN_LIMIT_MINUTES
    val max = if (isExtremelyDistracting) LIMITED_SOCIAL_MAX_LIMIT_MINUTES else OTHER_APP_MAX_LIMIT_MINUTES
    val initial = app.timerMinutes.takeIf { it > 0 }
        ?: if (isExtremelyDistracting) LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES else 15
    var minutes by remember(app.packageName) {
        mutableIntStateOf(initial.coerceIn(min, max))
    }
    val usedMinutes = (app.displayedUsageSeconds / 60).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (app.hasTimer) "Change timer" else "Set timer",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "${app.label} • ${min}–${max} minutes per day",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$minutes minutes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceIn(min, max) },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = (max - min) - 1,
                )
                if (usedMinutes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You've already used ${usedMinutes}m today. The new limit keeps that usage, so you won't get extra time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(minutes) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Pop-ups
// ---------------------------------------------------------------------------------------------

@Composable
private fun PopUpsTab(
    uiState: MainUiState,
    onReminderTextChange: (String) -> Unit,
    onTimerLimitMessageChange: (String) -> Unit,
    onExtremelyDistractingLimitMessageChange: (String) -> Unit,
    onMottoChange: (String) -> Unit,
    onBackgroundImageChange: (String) -> Unit,
    onBlockingStrategyChange: (BlockingStrategy) -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onClearPasswordError: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { onBackgroundImageChange(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChangePasswordCard(
            passwordError = uiState.passwordError,
            onChangePassword = onChangePassword,
            onClearError = onClearPasswordError,
        )
        MessageEditorCard(
            title = "Blocked app message",
            subtitle = "Shown when an app you blocked is opened.",
            value = uiState.preferences.reminderText,
            onValueChange = onReminderTextChange,
        )
        MessageEditorCard(
            title = "Timer limit message",
            subtitle = "Shown when a non-Extremely Distracting app's daily timer runs out.",
            value = uiState.preferences.timerLimitMessage,
            onValueChange = onTimerLimitMessageChange,
        )
        MessageEditorCard(
            title = "Extremely Distracting Apps limit message",
            subtitle = "Shown when an Extremely Distracting App's daily limit runs out.",
            value = uiState.preferences.extremelyDistractingLimitMessage,
            onValueChange = onExtremelyDistractingLimitMessageChange,
        )
        MottoCard(
            motto = uiState.preferences.motto,
            onMottoChange = onMottoChange,
        )

        OverlayImageCard(
            uri = uiState.preferences.backgroundImageUri,
            onPickImage = { imagePicker.launch(arrayOf("image/*")) },
            onRemoveImage = { onBackgroundImageChange("") },
        )

        SettingsCard(title = "YouTube Shorts") {
            Text(
                text = "Shorts are always blocked while YouTube is open. This cannot be turned off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        SettingsCard(title = "Blocking strategy") {
            Text(
                text = if (uiState.isDeviceOwner) {
                    "Device Owner mode is active. Zero-flash blocking is available."
                } else {
                    "Accessibility mode is used by default. Enable Device Owner via ADB for zero-flash blocking."
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

        SettingsCard(title = "Permissions") {
            uiState.permissionStatuses.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (status.isGranted) "✓ Granted" else "Required",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (status.isGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenOnboarding) {
                Text("Open permission setup")
            }
        }
    }
}

@Composable
private fun MottoCard(
    motto: String,
    onMottoChange: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(motto) }
    var isSaved by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(motto) {
        text = motto
    }

    SettingsCard(title = "Motto") {
        Text(
            text = "Shown under the LongTerm title on the top bar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                isSaved = false
            },
            label = { Text("Motto") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isSaved) "Saved!" else if (text.trim() != motto) "Unsaved changes" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Button(
                onClick = {
                    onMottoChange(text.trim())
                    isSaved = true
                },
                enabled = text.trim() != motto && text.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save motto")
            }
        }
    }
}

@Composable
private fun MessageEditorCard(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(value) }
    var isSaved by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(value) {
        text = value
    }

    SettingsCard(title = title) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                isSaved = false
            },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isSaved) "Saved!" else if (text.trim() != value) "Unsaved changes" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Button(
                onClick = {
                    onValueChange(text.trim())
                    isSaved = true
                },
                enabled = text.trim() != value && text.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun OverlayImageCard(
    uri: String,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    SettingsCard(title = "Overlay image") {
        Text(
            text = "Shown behind the blocked message. Leave empty for the default gradient.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (uri.isNotBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = "Overlay preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickImage) {
                    Text("Change image")
                }
                TextButton(onClick = onRemoveImage) {
                    Text("Remove image")
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1D27),
                                Color(0xFF2B1248),
                                Color(0xFF0F1117),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Default gradient",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onPickImage) {
                Text("Pick image")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Change Password Card
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChangePasswordCard(
    passwordError: String?,
    onChangePassword: (String, String) -> Unit,
    onClearError: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Detect when the external passwordError clears (meaning changePassword succeeded)
    val prevError = remember { mutableStateOf(passwordError) }
    androidx.compose.runtime.LaunchedEffect(passwordError) {
        if (prevError.value != null && passwordError == null && expanded) {
            // Success
            successMessage = "Password changed successfully."
            expanded = false
            currentPassword = ""; newPassword = ""; confirmPassword = ""
        }
        prevError.value = passwordError
    }

    SettingsCard(title = "Change Password") {
        Text(
            text = "Update your LongTerm password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        if (successMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = successMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (!expanded) {
            OutlinedButton(
                onClick = { expanded = true; successMessage = null; onClearError() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change Password")
            }
        } else {
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it; localError = null; onClearError() },
                label = { Text("Current password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; localError = null; onClearError() },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null; onClearError() },
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            val displayError = localError ?: passwordError
            if (displayError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        expanded = false
                        currentPassword = ""; newPassword = ""; confirmPassword = ""
                        localError = null; onClearError()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        when {
                            currentPassword.isBlank() -> localError = "Enter your current password."
                            newPassword.length < 4 -> localError = "New password must be at least 4 characters."
                            newPassword != confirmPassword -> localError = "Passwords do not match."
                            else -> onChangePassword(currentPassword, newPassword)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared UI
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
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
