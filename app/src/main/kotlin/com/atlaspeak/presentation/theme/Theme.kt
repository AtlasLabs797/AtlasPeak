package com.atlaspeak.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

internal val AtlasPeakLightColorScheme = lightColorScheme(
    primary = AtlasLightColors.ink,
    onPrimary = AtlasLightColors.onAccent,
    primaryContainer = AtlasLightColors.surface3,
    onPrimaryContainer = AtlasLightColors.ink,
    secondary = AtlasLightColors.ink2,
    onSecondary = AtlasLightColors.onAccent,
    background = AtlasLightColors.ground,
    onBackground = AtlasLightColors.ink,
    surface = AtlasLightColors.surface,
    onSurface = AtlasLightColors.ink,
    surfaceVariant = AtlasLightColors.surface2,
    onSurfaceVariant = AtlasLightColors.ink2,
    outline = AtlasLightColors.lineStrong,
    error = AtlasLightColors.risk,
    onError = AtlasLightColors.ink,
    surfaceTint = AtlasLightColors.ink,
)

internal val AtlasPeakDarkColorScheme = darkColorScheme(
    primary = AtlasDarkColors.ink,
    onPrimary = AtlasDarkColors.onAccent,
    primaryContainer = AtlasDarkColors.surface3,
    onPrimaryContainer = AtlasDarkColors.ink,
    secondary = AtlasDarkColors.ink2,
    onSecondary = AtlasDarkColors.onAccent,
    background = AtlasDarkColors.ground,
    onBackground = AtlasDarkColors.ink,
    surface = AtlasDarkColors.surface,
    onSurface = AtlasDarkColors.ink,
    surfaceVariant = AtlasDarkColors.surface2,
    onSurfaceVariant = AtlasDarkColors.ink2,
    outline = AtlasDarkColors.lineStrong,
    error = AtlasDarkColors.risk,
    onError = AtlasDarkColors.onAccent,
    surfaceTint = AtlasDarkColors.ink,
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
    val atlasColors = if (darkTheme) AtlasDarkColors else AtlasLightColors
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalAtlasColors provides atlasColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) AtlasPeakDarkColorScheme else AtlasPeakLightColorScheme,
            typography = AtlasPeakTypography,
            shapes = AtlasPeakShapes,
            content = content,
        )
    }
}
