package com.bibin.visioneye.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VisionEyeDarkColorScheme = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = HighContrastBlack,
    secondary = HighContrastCyan,
    onSecondary = HighContrastBlack,
    background = HighContrastBlack,
    onBackground = HighContrastWhite,
    surface = HighContrastSurface,
    onSurface = HighContrastWhite,
    error = EmergencyRed,
    onError = HighContrastWhite
)

@Composable
fun VisionEyeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VisionEyeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
