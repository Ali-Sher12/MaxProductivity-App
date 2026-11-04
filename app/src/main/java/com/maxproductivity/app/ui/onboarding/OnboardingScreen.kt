package com.maxproductivity.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maxproductivity.app.util.PermissionStatus
import com.maxproductivity.app.util.PermissionType

@Composable
fun OnboardingScreen(
    permissionStatuses: List<PermissionStatus>,
    onOpenPermission: (PermissionType) -> Unit,
    onContinue: () -> Unit,
) {
    val allGranted = permissionStatuses.all { it.isGranted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Welcome to LongTerm",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Grant the permissions below to enable on-device app blocking, YouTube Shorts detection, and daily YouTube limits.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )

        permissionStatuses.forEach { status ->
            PermissionCard(
                status = status,
                onGrant = { onOpenPermission(status.type) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (allGranted) "Continue" else "Grant all permissions to continue")
        }
    }
}

@Composable
private fun PermissionCard(
    status: PermissionStatus,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = status.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (status.isGranted) "Granted" else "Required",
                    color = if (status.isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            if (!status.isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Settings")
                }
            }
        }
    }
}
