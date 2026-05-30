package com.atlaspeak.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

object AtlasMotion {
    const val DurationInstant: Int = 80
    const val DurationFast: Int = 140
    const val DurationMedium: Int = 220
    const val DurationSlow: Int = 320
    const val DurationDeliberate: Int = 480

    val StandardEasing: Easing = FastOutSlowInEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)
    val DecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val AccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}
