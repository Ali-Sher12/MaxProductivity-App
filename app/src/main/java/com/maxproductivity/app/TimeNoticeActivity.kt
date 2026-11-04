package com.maxproductivity.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import com.maxproductivity.app.data.LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES
import com.maxproductivity.app.ui.theme.MaxProductivityTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

class TimeNoticeActivity : ComponentActivity() {

    private data class TimeNoticePayload(
        val targetPackage: String = "",
        val limitMinutes: Int = LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES,
        val remainingSeconds: Long = LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES * 60L,
    )

    private val payload = MutableStateFlow(TimeNoticePayload())

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        payload.value = readPayload(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        payload.value = readPayload(intent)

        setContent {
            val currentPayload by payload.collectAsStateWithLifecycle()
            MaxProductivityTheme {
                TimeNoticeScreen(
                    limitMinutes = currentPayload.limitMinutes,
                    remainingSeconds = currentPayload.remainingSeconds,
                    onClose = {
                        if (currentPayload.targetPackage.isNotBlank()) {
                            FocusAccessibilityService.acknowledgeSession(currentPayload.targetPackage)
                            YouTubeTrackingState.updateForeground(currentPayload.targetPackage)
                        }
                        YouTubeTrackingState.isNoticeActive = false
                        finish()
                    },
                    onBack = {
                        FocusAccessibilityService.clearAcknowledgedSession()
                        sendUserHome()
                        YouTubeTrackingState.isNoticeActive = false
                        finish()
                    },
                )
            }
        }
    }

    private fun readPayload(intent: Intent?): TimeNoticePayload {
        val targetPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val limitMinutes = intent?.getIntExtra(EXTRA_LIMIT_MINUTES, LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES)
            ?: LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES
        val remainingSeconds = intent?.getLongExtra(EXTRA_REMAINING_SECONDS, limitMinutes * 60L)
            ?: (limitMinutes * 60L)
        return TimeNoticePayload(targetPackage, limitMinutes, remainingSeconds)
    }

    override fun onResume() {
        super.onResume()
        YouTubeTrackingState.isNoticeActive = true
    }

    override fun onPause() {
        YouTubeTrackingState.isNoticeActive = false
        super.onPause()
    }

    override fun onDestroy() {
        YouTubeTrackingState.isNoticeActive = false
        super.onDestroy()
    }

    private fun sendUserHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_target_package"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
        const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"

        fun createIntent(
            context: Context,
            packageName: String,
            remainingSeconds: Long,
            limitMinutes: Int = LIMITED_SOCIAL_DEFAULT_LIMIT_MINUTES,
        ): Intent = Intent(context, TimeNoticeActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
    }
}

@Composable
private fun TimeNoticeScreen(
    limitMinutes: Int,
    remainingSeconds: Long,
    onClose: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // Fullscreen scrim blocking all touch events to the app underneath
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Consume clicks so app beneath cannot be tapped */ }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E2230))
                .padding(28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Time is limited. Make it count.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                    ),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val remainingText = if (remainingSeconds < 60) {
                    "$remainingSeconds seconds remaining today"
                } else {
                    "${(remainingSeconds + 59) / 60} minutes remaining today"
                }

                Text(
                    text = "Daily limit: ${limitMinutes}m ($remainingText)",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5B6CFF),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}
