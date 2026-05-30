package com.atlaspeak.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AtlasBrushes {
    fun heroGradient(colors: ColorScheme): Brush = Brush.linearGradient(
        0f to colors.primary.copy(alpha = 0.88f),
        0.46f to colors.primaryContainer.copy(alpha = 0.74f),
        1f to colors.surface.copy(alpha = 0.95f),
    )

    fun subtleSurface(colors: ColorScheme): Brush = Brush.verticalGradient(
        0f to colors.surface,
        1f to colors.surfaceVariant.copy(alpha = 0.55f),
    )

    fun spotlight(colors: ColorScheme, center: Offset = Offset(160f, 0f), radius: Float = 920f): Brush =
        Brush.radialGradient(
            colors = listOf(colors.primary.copy(alpha = 0.18f), Color.Transparent),
            center = center,
            radius = radius,
        )

    fun accentBorder(colors: ColorScheme): Brush = Brush.linearGradient(
        listOf(
            colors.primary.copy(alpha = 0.55f),
            colors.outline.copy(alpha = 0.35f),
        ),
    )
}
