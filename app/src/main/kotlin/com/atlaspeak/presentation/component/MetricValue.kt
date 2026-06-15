package com.atlaspeak.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun MetricValue(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    emphasized: Boolean = false,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = value,
            style = if (emphasized) {
                MaterialTheme.typography.displayMedium
            } else {
                MaterialTheme.typography.displaySmall
            },
            color = atlasColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelLarge,
                color = atlasColors.ink3,
                fontSize = 15.sp,
            )
        }
    }
}
