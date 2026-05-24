package com.atlaspeak.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeAccessibilityTest {
    @Test
    fun `theme text color pairs meet WCAG AA contrast`() {
        val pairs = listOf(
            "light onPrimary/primary" to (AtlasPeakLightColorScheme.onPrimary to AtlasPeakLightColorScheme.primary),
            "light onPrimaryContainer/primaryContainer" to
                (AtlasPeakLightColorScheme.onPrimaryContainer to AtlasPeakLightColorScheme.primaryContainer),
            "light onSecondary/secondary" to (AtlasPeakLightColorScheme.onSecondary to AtlasPeakLightColorScheme.secondary),
            "light onBackground/background" to (AtlasPeakLightColorScheme.onBackground to AtlasPeakLightColorScheme.background),
            "light onSurface/surface" to (AtlasPeakLightColorScheme.onSurface to AtlasPeakLightColorScheme.surface),
            "light onSurfaceVariant/surfaceVariant" to
                (AtlasPeakLightColorScheme.onSurfaceVariant to AtlasPeakLightColorScheme.surfaceVariant),
            "light onError/error" to (AtlasPeakLightColorScheme.onError to AtlasPeakLightColorScheme.error),
            "dark onPrimary/primary" to (AtlasPeakDarkColorScheme.onPrimary to AtlasPeakDarkColorScheme.primary),
            "dark onPrimaryContainer/primaryContainer" to
                (AtlasPeakDarkColorScheme.onPrimaryContainer to AtlasPeakDarkColorScheme.primaryContainer),
            "dark onSecondary/secondary" to (AtlasPeakDarkColorScheme.onSecondary to AtlasPeakDarkColorScheme.secondary),
            "dark onBackground/background" to (AtlasPeakDarkColorScheme.onBackground to AtlasPeakDarkColorScheme.background),
            "dark onSurface/surface" to (AtlasPeakDarkColorScheme.onSurface to AtlasPeakDarkColorScheme.surface),
            "dark onSurfaceVariant/surfaceVariant" to
                (AtlasPeakDarkColorScheme.onSurfaceVariant to AtlasPeakDarkColorScheme.surfaceVariant),
            "dark onError/error" to (AtlasPeakDarkColorScheme.onError to AtlasPeakDarkColorScheme.error),
        )

        val failures = pairs.mapNotNull { (name, colors) ->
            val ratio = colors.first.contrastRatio(colors.second)
            if (ratio < WCAG_AA_NORMAL_TEXT) "$name=${"%.2f".format(ratio)}" else null
        }

        assertTrue(failures.isEmpty(), failures.joinToString())
    }

    private fun Color.contrastRatio(background: Color): Double {
        val foregroundLuminance = relativeLuminance()
        val backgroundLuminance = background.relativeLuminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val normalized = value.toDouble()
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private companion object {
        const val WCAG_AA_NORMAL_TEXT = 4.5
    }
}
