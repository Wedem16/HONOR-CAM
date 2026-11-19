package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HonorDarkColorScheme = darkColorScheme(
    primary = HonorOrange,
    onPrimary = Color.Black,
    primaryContainer = HonorOrangeDim,
    onPrimaryContainer = HonorOrangeLight,
    secondary = HonorOrangeLight,
    onSecondary = Color.Black,
    tertiary = HonorLevelGreen,
    background = HonorDarkBg,
    onBackground = HonorTextPrimary,
    surface = HonorSurface,
    onSurface = HonorTextPrimary,
    surfaceVariant = HonorCard,
    onSurfaceVariant = HonorTextSecondary,
    error = HonorRecRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HonorDarkColorScheme,
        typography = Typography,
        content = content
    )
}
