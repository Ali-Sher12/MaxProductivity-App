package com.maxproductivity.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxproductivity.app.data.AppPreferences
import com.maxproductivity.app.ui.theme.MaxProductivityTheme

class BlockOverlayActivity : ComponentActivity() {

    // This Activity is declared launchMode="singleInstance" in the manifest so it can render
    // above everything, including other tasks. That means when a second block event fires while
    // an earlier instance is still alive in the background (e.g. the user pressed the physical
    // Home button instead of tapping our "Return Home" button, so finish() was never called),
    // Android reuses the existing instance and calls onNewIntent() instead of onCreate(). Without
    // this override, the Compose content already drawn from the *previous* block event just sits
    // there — this is what caused the reminder message/image to sometimes look "stuck" or blank,
    // and made tap-to-close feel unreliable, since the visible screen wasn't actually the one
    // reacting to the latest intent/preferences. recreate() forces a real onCreate() every time,
    // so the screen is always built fresh from the current message, image, and click handlers.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intentMessage = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
        val intentBackgroundUri = intent.getStringExtra(EXTRA_BACKGROUND_URI).orEmpty()

        setContent {
            val preferences by MaxProductivityApp.get(this@BlockOverlayActivity)
                .preferencesRepository
                .preferencesFlow
                .collectAsStateWithLifecycle(initialValue = AppPreferences())

            val message = preferences.reminderText.ifBlank {
                intentMessage ?: getString(R.string.block_overlay_default_message)
            }
            val backgroundUri = preferences.backgroundImageUri.ifBlank { intentBackgroundUri }

            MaxProductivityTheme {
                BlockOverlayScreen(
                    message = message,
                    backgroundImageUri = backgroundUri,
                    onReturnHome = {
                        performGlobalActionHome()
                        finish()
                    },
                )
            }
        }
    }

    private fun performGlobalActionHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    companion object {
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_BACKGROUND_URI = "extra_background_uri"

        fun createIntent(
            context: Context,
            message: String,
            blockedPackage: String,
        ): Intent = Intent(context, BlockOverlayActivity::class.java).apply {
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_PACKAGE, blockedPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@Composable
private fun BlockOverlayScreen(
    message: String,
    backgroundImageUri: String,
    onReturnHome: () -> Unit,
) {
    BackHandler(onBack = onReturnHome)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onReturnHome,
            ),
    ) {
        if (backgroundImageUri.isNotBlank()) {
            AsyncImage(
                model = backgroundImageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1D27),
                                Color(0xFF2B1248),
                                Color(0xFF0F1117),
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(28.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onReturnHome,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5B6CFF),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Return Home")
                    }
                }
            }
        }
    }
}
