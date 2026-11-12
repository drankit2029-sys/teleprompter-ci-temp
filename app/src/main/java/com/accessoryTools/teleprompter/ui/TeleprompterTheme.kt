package com.accessoryTools.teleprompter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TeleprompterColors =
    darkColorScheme(
        primary = Color(0xFFFFC857),
        onPrimary = Color(0xFF211A00),
        secondary = Color(0xFF8BD3C7),
        onSecondary = Color(0xFF00201C),
        background = Color(0xFF090A0C),
        onBackground = Color(0xFFF4F1E9),
        surface = Color(0xFF121419),
        onSurface = Color(0xFFF4F1E9),
        surfaceVariant = Color(0xFF1D2027),
        onSurfaceVariant = Color(0xFFC8C5BD),
        outline = Color(0xFF474A52),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun TeleprompterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TeleprompterColors,
        content = content,
    )
}
