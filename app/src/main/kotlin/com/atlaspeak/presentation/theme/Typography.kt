package com.atlaspeak.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.atlaspeak.R

private val headingFamily = FontFamily(
    Font(R.font.space_grotesk_variable, weight = FontWeight.Normal),
    Font(R.font.space_grotesk_variable, weight = FontWeight.Medium),
    Font(R.font.space_grotesk_variable, weight = FontWeight.SemiBold),
    Font(R.font.space_grotesk_variable, weight = FontWeight.Bold),
)

private val bodyFamily = FontFamily(
    Font(R.font.space_grotesk_variable, weight = FontWeight.Normal),
    Font(R.font.space_grotesk_variable, weight = FontWeight.Medium),
    Font(R.font.space_grotesk_variable, weight = FontWeight.SemiBold),
)

private val dataFamily = FontFamily(
    Font(R.font.jetbrains_mono_variable, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono_variable, weight = FontWeight.Medium),
    Font(R.font.jetbrains_mono_variable, weight = FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_variable, weight = FontWeight.Bold),
)

private fun TextStyle.withTabularNumbers() = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
)

val AtlasPeakTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = dataFamily, fontWeight = FontWeight.Bold, fontSize = 86.sp, lineHeight = 90.sp).withTabularNumbers(),
        displayMedium = base.displayMedium.copy(fontFamily = dataFamily, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 64.sp).withTabularNumbers(),
        displaySmall = base.displaySmall.copy(fontFamily = dataFamily, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp).withTabularNumbers(),
        headlineLarge = base.headlineLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp).withTabularNumbers(),
        headlineMedium = base.headlineMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp).withTabularNumbers(),
        headlineSmall = base.headlineSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp).withTabularNumbers(),
        titleLarge = base.titleLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp).withTabularNumbers(),
        titleMedium = base.titleMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp).withTabularNumbers(),
        titleSmall = base.titleSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp).withTabularNumbers(),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, lineHeight = 21.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, lineHeight = 18.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontFamily = dataFamily, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp).withTabularNumbers(),
        labelMedium = base.labelMedium.copy(fontFamily = dataFamily, fontWeight = FontWeight.Medium, lineHeight = 18.sp, letterSpacing = 1.sp).withTabularNumbers(),
        labelSmall = base.labelSmall.copy(fontFamily = dataFamily, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 1.5.sp).withTabularNumbers(),
    )
}
