package com.atlaspeak.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object AtlasMotion {
    const val DurationFast: Int = 140
    const val DurationMedium: Int = 220

    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)
}
