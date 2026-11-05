package com.maxproductivity.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maxproductivity.app.util.CommitmentPhrase

@Composable
fun CommitmentConfirmDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var firstEntry by remember { mutableStateOf("") }
    var secondEntry by remember { mutableStateOf("") }
    val firstMatches = CommitmentPhrase.matches(firstEntry)
    val secondMatches = CommitmentPhrase.matches(secondEntry)
    val canConfirm = firstMatches && secondMatches

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Type this phrase twice:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = CommitmentPhrase.DISPLAY,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = firstEntry,
                    onValueChange = { firstEntry = it },
                    label = { Text("First confirmation") },
                    isError = firstEntry.isNotBlank() && !firstMatches,
                    supportingText = if (firstEntry.isNotBlank() && !firstMatches) {
                        { Text("Phrase does not match.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = secondEntry,
                    onValueChange = { secondEntry = it },
                    label = { Text("Second confirmation") },
                    isError = secondEntry.isNotBlank() && !secondMatches,
                    supportingText = if (secondEntry.isNotBlank() && !secondMatches) {
                        { Text("Phrase does not match.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Case and punctuation are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmed,
                enabled = canConfirm,
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
