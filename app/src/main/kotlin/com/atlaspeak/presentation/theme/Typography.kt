package com.atlaspeak.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val headingFamily = FontFamily.SansSerif
private val bodyFamily = FontFamily.SansSerif

private fun TextStyle.withTabularNumbers() = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    fontFeatureSettings = "tnum",
)

val AtlasPeakTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal),
        bodySmall = base.bodySmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium).withTabularNumbers(),
        labelMedium = base.labelMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium).withTabularNumbers(),
        labelSmall = base.labelSmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium).withTabularNumbers(),
    )
}
