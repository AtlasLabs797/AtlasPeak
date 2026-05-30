package com.atlaspeak.presentation.component

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.AtlasMotion
import com.atlaspeak.presentation.theme.LocalSpacing

data class PeriodSelectorItem<T>(
    val value: T,
    @StringRes val labelRes: Int,
)

@Composable
fun <T> PeriodSelector(
    items: List<PeriodSelectorItem<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val trackShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(spacing.xxs),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            items(items, key = { it.value.toString() }) { item ->
                val isSelected = selected == item.value
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(AtlasMotion.DurationMedium, easing = AtlasMotion.EmphasizedEasing),
                    label = "",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(AtlasMotion.DurationMedium, easing = AtlasMotion.EmphasizedEasing),
                    label = "",
                )
                Surface(
                    onClick = { onSelected(item.value) },
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .sizeIn(minWidth = 48.dp),
                    shape = trackShape,
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        text = stringResource(item.labelRes),
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
