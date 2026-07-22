package com.maxproductivity.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import com.maxproductivity.app.ui.theme.MaxProductivityTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: getString(R.string.block_overlay_default_message)
        val backgroundUri = runBlocking {
            MaxProductivityApp.get(this@BlockOverlayActivity)
                .preferencesRepository
                .preferencesFlow
                .first()
                .backgroundImageUri
        }.ifBlank {
            intent.getStringExtra(EXTRA_BACKGROUND_URI).orEmpty()
        }

        setContent {
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
    Box(modifier = Modifier.fillMaxSize()) {
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
