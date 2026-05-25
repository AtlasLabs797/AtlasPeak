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
    letterSpacing = 0.sp,
)

val AtlasPeakTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp).withTabularNumbers(),
        displayMedium = base.displayMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp).withTabularNumbers(),
        displaySmall = base.displaySmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp).withTabularNumbers(),
        headlineLarge = base.headlineLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp).withTabularNumbers(),
        headlineMedium = base.headlineMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp).withTabularNumbers(),
        headlineSmall = base.headlineSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp).withTabularNumbers(),
        titleLarge = base.titleLarge.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp).withTabularNumbers(),
        titleMedium = base.titleMedium.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp).withTabularNumbers(),
        titleSmall = base.titleSmall.copy(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp).withTabularNumbers(),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, lineHeight = 21.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, lineHeight = 18.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, lineHeight = 20.sp).withTabularNumbers(),
        labelMedium = base.labelMedium.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, lineHeight = 18.sp).withTabularNumbers(),
        labelSmall = base.labelSmall.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, lineHeight = 16.sp).withTabularNumbers(),
    )
}
