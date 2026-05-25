package com.atlaspeak.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

internal val AtlasPeakLightColorScheme = lightColorScheme(
    primary = AtlasPeakGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF5B0),
    onPrimaryContainer = Color(0xFF17210A),
    secondary = Color(0xFF5C6255),
    onSecondary = Color.White,
    background = AtlasPeakCanvas,
    onBackground = Color(0xFF171914),
    surface = Color(0xFFFFFCF4),
    onSurface = Color(0xFF171914),
    surfaceVariant = Color(0xFFE9E6DC),
    onSurfaceVariant = Color(0xFF4D5248),
    outline = Color(0xFFC8C5BA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    surfaceTint = AtlasPeakGreen,
)

internal val AtlasPeakDarkColorScheme = darkColorScheme(
    primary = AtlasPeakLime,
    onPrimary = Color(0xFF172100),
    primaryContainer = Color(0xFF314600),
    onPrimaryContainer = Color(0xFFE9FF9A),
    secondary = Color(0xFFC8CBBB),
    onSecondary = Color(0xFF2D3128),
    background = AtlasPeakInk,
    onBackground = Color(0xFFEDEFE5),
    surface = Color(0xFF151812),
    onSurface = Color(0xFFEDEFE5),
    surfaceVariant = Color(0xFF23271F),
    onSurfaceVariant = Color(0xFFD0D5C7),
    outline = Color(0xFF464B40),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surfaceTint = AtlasPeakLime,
)

@Composable
fun AtlasPeakTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) AtlasPeakDarkColorScheme else AtlasPeakLightColorScheme,
            typography = AtlasPeakTypography,
            shapes = AtlasPeakShapes,
            content = content,
        )
    }
}
