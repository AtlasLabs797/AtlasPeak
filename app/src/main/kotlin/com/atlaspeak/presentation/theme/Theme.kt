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

private val LightColorScheme = lightColorScheme(
    primary = AtlasRed,
    onPrimary = Color.White,
    secondary = Color(0xFF665F5D),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF5F5F5F),
    outline = Color(0xFFD6D6D6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = AtlasRedDark,
    onPrimary = Color(0xFF3A0907),
    secondary = Color(0xFFD3C7C4),
    onSecondary = Color(0xFF362F2D),
    background = Color(0xFF121212),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFC9C9C9),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
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
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AtlasPeakTypography,
            shapes = AtlasPeakShapes,
            content = content,
        )
    }
}
