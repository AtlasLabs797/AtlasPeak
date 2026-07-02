package com.atlaspeak.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val screen: Dp = 24.dp,
    val card: Dp = 16.dp,
    val cardGap: Dp = 16.dp,
    val compact: Dp = 8.dp,
    val minTouchTarget: Dp = 44.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
