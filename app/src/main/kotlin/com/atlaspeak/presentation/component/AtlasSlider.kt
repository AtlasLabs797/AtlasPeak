package com.atlaspeak.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun AtlasSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = atlasColors.ink,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = atlasColors.ink,
                    activeTrackColor = atlasColors.ink,
                    inactiveTrackColor = atlasColors.fillActive,
                    activeTickColor = atlasColors.onAccent.copy(alpha = 0.5f),
                    inactiveTickColor = atlasColors.lineStrong,
                ),
            )
        }
    }
}
