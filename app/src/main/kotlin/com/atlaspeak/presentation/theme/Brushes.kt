package com.atlaspeak.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush

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
}
