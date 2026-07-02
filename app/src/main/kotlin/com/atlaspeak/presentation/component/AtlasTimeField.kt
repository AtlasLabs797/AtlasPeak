package com.atlaspeak.presentation.component

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing
import java.util.Locale

@Composable
fun AtlasTimeField(
    value: String,
    onTimeSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val initialTime = remember(value) { value.parseHourMinute() ?: DEFAULT_TIME }

    OutlinedButton(
        enabled = enabled,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onTimeSelected(String.format(Locale.US, "%02d:%02d", hour, minute))
                },
                initialTime.first,
                initialTime.second,
                true,
            ).show()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, atlasColors.lineStrong),
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = atlasColors.surface,
            contentColor = atlasColors.ink,
            disabledContainerColor = atlasColors.surface,
            disabledContentColor = atlasColors.ink3,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = atlasColors.ink3,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = atlasColors.ink,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = atlasColors.ink3,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = atlasColors.ink2,
            )
        }
    }
}

private fun String.parseHourMinute(): Pair<Int, Int>? {
    val parts = split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}

private val DEFAULT_TIME = 18 to 0
