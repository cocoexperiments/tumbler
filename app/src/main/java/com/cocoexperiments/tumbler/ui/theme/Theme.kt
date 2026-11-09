package com.cocoexperiments.tumbler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Brand.Palm,
    onPrimary = Brand.OnPalm,
    primaryContainer = Brand.PalmDeep,
    onPrimaryContainer = Brand.OnPalmDeep,
    secondary = Color(0xFFB4CCBC),
    background = Brand.Ink,
    onBackground = Brand.Cream,
    surface = Brand.Ink,
    onSurface = Brand.Cream,
    onSurfaceVariant = Brand.Husk,
    surfaceContainer = Brand.Surface1,
    surfaceContainerHigh = Brand.Surface2,
    surfaceContainerHighest = Brand.Surface3,
    outlineVariant = Brand.Outline,
    error = Brand.Error,
    onError = Color(0xFF3B0A0D),
    errorContainer = Color(0xFF6B1F26),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** The app is dark-only: it is a lock screen and should never flash a bright surface. */
@Composable
fun TumblerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography(), content = content)
}
