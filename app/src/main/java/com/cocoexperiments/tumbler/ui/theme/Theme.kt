package com.cocoexperiments.tumbler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFB9C7E8),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE3E7EF),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFE3E7EF),
    onSurfaceVariant = Color(0xFFA9B1C2),
    surfaceContainer = Color(0xFF151A24),
    surfaceContainerHigh = Color(0xFF1C2230),
    surfaceContainerHighest = Color(0xFF262D3D),
    outlineVariant = Color(0xFF444B5C),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0D),
    errorContainer = Color(0xFF6B1F26),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** The app is dark-only: it is a lock screen and should never flash a bright surface. */
@Composable
fun TumblerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography(), content = content)
}
