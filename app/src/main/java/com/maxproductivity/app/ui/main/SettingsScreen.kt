package com.maxproductivity.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maxproductivity.app.data.ED_NIGHT_BLOCK_END_HOUR
import com.maxproductivity.app.data.ED_NIGHT_BLOCK_START_HOUR

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onNightBlockChange: (Boolean) -> Unit,
    onAddExtremelyDistracting: (String) -> Unit,
    onRemoveExtremelyDistracting: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val builtIn = uiState.blockableApps.filter { it.isBuiltInExtremelyDistracting }
    val userEd = uiState.blockableApps.filter {
        it.isExtremelyDistracting && !it.isBuiltInExtremelyDistracting
    }
    val addable = uiState.blockableApps.filter { app ->
        !app.isExtremelyDistracting &&
            (
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
                )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Night block for ED apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Completely block all Extremely Distracting Apps from " +
                                "${formatHour(ED_NIGHT_BLOCK_START_HOUR)} to " +
                                "${formatHour(ED_NIGHT_BLOCK_END_HOUR)} every day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = uiState.preferences.nightBlockEdApps,
                        onCheckedChange = onNightBlockChange,
                    )
                }
            }
        }

        item {
            Text(
                text = "Extremely Distracting Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Built-in apps cannot be removed. Apps you add can be removed later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        items(builtIn, key = { "builtin-${it.packageName}" }) { app ->
            EdListRow(
                app = app,
                locked = true,
                actionLabel = "Built-in",
                onAction = {},
            )
        }

        if (userEd.isNotEmpty()) {
            item {
                Text(
                    text = "Added by you",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(userEd, key = { "user-ed-${it.packageName}" }) { app ->
                EdListRow(
                    app = app,
                    locked = false,
                    actionLabel = "Remove",
                    onAction = { onRemoveExtremelyDistracting(app.packageName) },
                )
            }
        }

        item {
            Text(
                text = "Add an app",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps to add") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (addable.isEmpty()) {
            item {
                Text(
                    text = if (query.isBlank()) "Every listed app is already Extremely Distracting." else "No matching apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(addable, key = { "addable-${it.packageName}" }) { app ->
                EdListRow(
                    app = app,
                    locked = false,
                    actionLabel = "Add",
                    onAction = { onAddExtremelyDistracting(app.packageName) },
                    addStyle = true,
                )
            }
        }
    }
}

@Composable
private fun EdListRow(
    app: BlockableApp,
    locked: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    addStyle: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        ),
        shape = RoundedCornerShape(14.dp),
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
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                    Text(
                        text = "  $actionLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            } else if (addStyle) {
                Button(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                    Text(actionLabel)
                }
            } else {
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun formatHour(hour: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val twelve = when {
        hour == 0 || hour == 12 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$twelve:00 $suffix"
}
