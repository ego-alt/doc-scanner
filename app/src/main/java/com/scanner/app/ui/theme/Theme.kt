package com.scanner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = Amber,
    background = ScanSurfaceDark,
    surface = ScanSurfaceDark
)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Amber,
    background = ScanSurfaceLight,
    surface = ScanSurfaceLight
)

@Composable
fun ScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ScannerTypography,
        content = content
    )
}
