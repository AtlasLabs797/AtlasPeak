package com.atlaspeak.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
) {
    val spacing = LocalSpacing.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(items, key = { it.value.toString() }) { item ->
            val isSelected = selected == item.value
            Surface(
                onClick = { onSelected(item.value) },
                modifier = Modifier.heightIn(min = 38.dp),
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)
                    },
                ),
            ) {
                Text(
                    text = stringResource(item.labelRes),
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
