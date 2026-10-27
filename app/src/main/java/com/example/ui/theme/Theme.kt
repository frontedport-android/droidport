package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GamerDarkColorScheme = darkColorScheme(
    primary = GamerCyan,
    onPrimary = GamerDarkBg,
    primaryContainer = Color(0xFF003845),
    onPrimaryContainer = Color(0xFF80F7FF),
    secondary = GamerPurple,
    onSecondary = GamerDarkBg,
    secondaryContainer = Color(0xFF3B1E54),
    onSecondaryContainer = Color(0xFFE8D0FF),
    tertiary = GamerPink,
    background = GamerDarkBg,
    onBackground = GamerOnSurface,
    surface = GamerSurface,
    onSurface = GamerOnSurface,
    surfaceVariant = GamerSurfaceVariant,
    onSurfaceVariant = GamerOnSurfaceVariant,
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to dark gamer theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GamerDarkColorScheme,
        typography = Typography,
        content = content
    )
}
