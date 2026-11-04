package com.maxproductivity.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFFF5D061) // Warm off-yellow
private val Secondary = Color(0xFFE8C872) // Muted golden off-yellow
private val Tertiary = Color(0xFFFFE082)
private val Background = Color(0xFF000000) // Pitch black
private val Surface = Color(0xFF121212) // Deep dark surface
private val SurfaceVariant = Color(0xFF1C1C1C)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = Color(0xFF1C1708),
    onSecondary = Color(0xFF1C1708),
    onTertiary = Color(0xFF1C1708),
    onBackground = Color(0xFFF0F0F0),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFFC7C7C7),
    primaryContainer = Color(0xFF2E260A),
    onPrimaryContainer = Primary,
)

private val LightColorScheme = DarkColorScheme

@Composable
fun MaxProductivityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
