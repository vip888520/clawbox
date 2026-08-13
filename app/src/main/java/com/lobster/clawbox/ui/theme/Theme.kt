package com.lobster.clawbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = Pink,
    onSecondary = Color.White,
    tertiary = Cyan,
    background = Bg,
    onBackground = TextPrimary,
    surface = Bg2,
    onSurface = TextPrimary,
    surfaceVariant = Bg3,
    onSurfaceVariant = TextMuted,
    outline = Border2,
    error = Red,
)

@Composable
fun ClawBoxTheme(content: @Composable () -> Unit) {
    // Always dark — ClawBox is a dark-first console app
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
