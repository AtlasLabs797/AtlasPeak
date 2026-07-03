package com.atlaspeak.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AtlasBrushes {
    fun heroGradient(colors: ColorScheme): Brush = Brush.linearGradient(
        0f to colors.surfaceVariant.copy(alpha = 0.92f),
        0.55f to colors.surface.copy(alpha = 0.98f),
        1f to colors.background,
    )

    fun subtleSurface(colors: ColorScheme): Brush = Brush.verticalGradient(
        0f to colors.surfaceVariant.copy(alpha = 0.72f),
        1f to colors.surface,
    )

    fun spotlight(colors: ColorScheme, center: Offset = Offset(160f, 0f), radius: Float = 920f): Brush =
        Brush.radialGradient(
            colors = listOf(colors.onBackground.copy(alpha = 0.08f), Color.Transparent),
            center = center,
            radius = radius,
        )

    fun accentBorder(colors: ColorScheme): Brush = Brush.linearGradient(
        listOf(
            colors.onBackground.copy(alpha = 0.16f),
            colors.outline.copy(alpha = 0.35f),
        ),
    )
}
