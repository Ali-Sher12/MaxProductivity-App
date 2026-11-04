package com.maxproductivity.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun CreatePasswordScreen(
    error: String?,
    onCreatePassword: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    PasswordScaffold(
        title = "Set your password",
        subtitle = "This password protects LongTerm's settings. Set it once now — it cannot be recovered later.",
        error = localError ?: error,
        fields = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null },
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        buttonText = "Set Password & Continue",
        onClick = {
            when {
                password.length < 4 -> localError = "Password must be at least 4 characters."
                password != confirmPassword -> localError = "Passwords do not match."
                else -> onCreatePassword(password)
            }
        },
    )
}

@Composable
fun UnlockScreen(
    error: String?,
    onUnlock: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (error != null) {
            isChecking = false
        }
    }

    val attemptUnlock = {
        if (!isChecking && password.isNotBlank()) {
            isChecking = true
            keyboardController?.hide()
            onUnlock(password)
        }
    }

    PasswordScaffold(
        title = "Welcome back",
        subtitle = "Enter your password to open LongTerm.",
        error = error,
        fields = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { attemptUnlock() }
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        buttonText = "Unlock",
        isLoading = isChecking,
        onClick = attemptUnlock,
    )
}

@Composable
private fun PasswordScaffold(
    title: String,
    subtitle: String,
    error: String?,
    fields: @Composable () -> Unit,
    buttonText: String,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "LongTerm",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Spacer(modifier = Modifier.height(20.dp))
                fields()
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onClick,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(buttonText)
                    }
                }
            }
        }
    }
}
