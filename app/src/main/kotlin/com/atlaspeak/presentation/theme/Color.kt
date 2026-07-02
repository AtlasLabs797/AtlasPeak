package com.atlaspeak.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val AtlasGround = Color(0xFF0A0A0B)
val AtlasSurface = Color(0xFF0E0E10)
val AtlasSurface2 = Color(0xFF131316)
val AtlasSurface3 = Color(0xFF1A1A1F)
val AtlasInk = Color(0xFFFAFAFA)
val AtlasInk2 = Color(0xFF9D9DA6)
// ink3/ink4 aclarados para cumplir contraste WCAG AA (≥4.5:1) sobre ground oscuro,
// donde antes fallaban (3.53:1 / 3.08:1) usados como color de texto en labels/captions.
val AtlasInk3 = Color(0xFF8A8A92)
val AtlasInk4 = Color(0xFF7E7E86)
val AtlasOnAccent = Color(0xFF0A0A0B)
val AtlasRisk = Color(0xFFFF5A4D)
val AtlasWarn = Color(0xFFE0B341)

data class AtlasColors(
    val ground: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val onAccent: Color,
    val line1: Color,
    val line2: Color,
    val line3: Color,
    val lineStrong: Color,
    val fillSoft: Color,
    val fillActive: Color,
    val spark: Color,
    val grid: Color,
    val ringTrack: Color,
    val risk: Color,
    val warn: Color,
)

val AtlasDarkColors = AtlasColors(
    ground = AtlasGround,
    surface = AtlasSurface,
    surface2 = AtlasSurface2,
    surface3 = AtlasSurface3,
    ink = AtlasInk,
    ink2 = AtlasInk2,
    ink3 = AtlasInk3,
    ink4 = AtlasInk4,
    onAccent = AtlasOnAccent,
    line1 = Color.White.copy(alpha = 0.08f),
    line2 = Color.White.copy(alpha = 0.10f),
    line3 = Color.White.copy(alpha = 0.12f),
    lineStrong = Color.White.copy(alpha = 0.16f),
    fillSoft = Color.White.copy(alpha = 0.05f),
    fillActive = Color.White.copy(alpha = 0.10f),
    spark = Color.White.copy(alpha = 0.55f),
    grid = Color.White.copy(alpha = 0.07f),
    ringTrack = Color.White.copy(alpha = 0.09f),
    risk = AtlasRisk,
    warn = AtlasWarn,
)

val AtlasLightColors = AtlasColors(
    ground = Color(0xFFFAFAF8),
    surface = Color.White,
    surface2 = Color(0xFFF4F3EF),
    surface3 = Color(0xFFEFEEE9),
    ink = AtlasOnAccent,
    ink2 = Color(0xFF5A5A60),
    ink3 = Color(0xFF6E6E76),
    // ink4 oscurecido: #A8A8AE fallaba contraste AA (2.26:1) sobre ground claro.
    ink4 = Color(0xFF71717A),
    onAccent = Color(0xFFFAFAFA),
    line1 = AtlasOnAccent.copy(alpha = 0.08f),
    line2 = AtlasOnAccent.copy(alpha = 0.12f),
    line3 = AtlasOnAccent.copy(alpha = 0.16f),
    lineStrong = AtlasOnAccent.copy(alpha = 0.22f),
    fillSoft = AtlasOnAccent.copy(alpha = 0.04f),
    fillActive = AtlasOnAccent.copy(alpha = 0.08f),
    spark = AtlasOnAccent.copy(alpha = 0.55f),
    grid = AtlasOnAccent.copy(alpha = 0.08f),
    ringTrack = AtlasOnAccent.copy(alpha = 0.10f),
    risk = Color(0xFFE0473B),
    warn = Color(0xFFB8861E),
)

val LocalAtlasColors = staticCompositionLocalOf { AtlasDarkColors }
