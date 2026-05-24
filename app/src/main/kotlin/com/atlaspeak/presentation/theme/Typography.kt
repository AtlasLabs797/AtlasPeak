package com.atlaspeak.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.atlaspeak.R

private val headingFamily = FontFamily(
    Font(R.font.poppins_semibold, weight = FontWeight.SemiBold),
    Font(R.font.poppins_bold, weight = FontWeight.Bold),
)

private val bodyFamily = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.Normal),
    Font(R.font.inter_variable, weight = FontWeight.Medium),
)

private fun TextStyle.withTabularNumbers() = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    fontFeatureSettings = "tnum",
)

val AtlasPeakTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold).withTabularNumbers(),
        displayMedium = base.displayMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold).withTabularNumbers(),
        displaySmall = base.displaySmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold).withTabularNumbers(),
        headlineLarge = base.headlineLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold).withTabularNumbers(),
        headlineMedium = base.headlineMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold).withTabularNumbers(),
        headlineSmall = base.headlineSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold).withTabularNumbers(),
        titleLarge = base.titleLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold).withTabularNumbers(),
        titleMedium = base.titleMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold).withTabularNumbers(),
        titleSmall = base.titleSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold).withTabularNumbers(),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal),
        bodySmall = base.bodySmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium).withTabularNumbers(),
        labelMedium = base.labelMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium).withTabularNumbers(),
        labelSmall = base.labelSmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium).withTabularNumbers(),
    )
}
