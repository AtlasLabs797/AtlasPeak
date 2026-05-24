package com.atlaspeak.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
            FilterChip(
                selected = selected == item.value,
                onClick = { onSelected(item.value) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}
