package com.atlaspeak.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun AtlasChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Surface(
        modifier = modifier
            .heightIn(min = spacing.minTouchTarget)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (selected) atlasColors.ink else Color.Transparent,
        contentColor = if (selected) atlasColors.onAccent else atlasColors.ink2,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (selected) atlasColors.ink else atlasColors.lineStrong),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun AtlasListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingContentDescription: String? = null,
    trailingIcon: ImageVector? = Icons.Filled.ChevronRight,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = atlasColors.surface,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, atlasColors.line2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(spacing.card),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                PremiumIconBadge(filled = false) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = leadingContentDescription,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = atlasColors.ink,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = atlasColors.ink2,
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            } else if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = atlasColors.ink3,
                )
            }
        }
    }
}

@Composable
fun AtlasSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingContentDescription: String? = null,
) {
    val atlasColors = LocalAtlasColors.current
    AtlasListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leadingIcon = leadingIcon,
        leadingContentDescription = leadingContentDescription,
        trailingIcon = null,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = atlasColors.onAccent,
                    checkedTrackColor = atlasColors.ink,
                    uncheckedThumbColor = atlasColors.ink3,
                    uncheckedTrackColor = atlasColors.fillActive,
                    uncheckedBorderColor = atlasColors.lineStrong,
                    disabledCheckedThumbColor = atlasColors.ink3,
                    disabledCheckedTrackColor = atlasColors.fillSoft,
                    disabledUncheckedThumbColor = atlasColors.ink4,
                    disabledUncheckedTrackColor = atlasColors.fillSoft,
                ),
            )
        },
    )
}

data class AtlasDropdownItem<T>(
    val value: T,
    val label: String,
)

@Composable
fun <T> AtlasDropdown(
    label: String,
    selectedLabel: String,
    items: List<AtlasDropdownItem<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            enabled = enabled,
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
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
                        text = selectedLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = atlasColors.ink,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = atlasColors.ink2,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = atlasColors.surface3,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = atlasColors.ink,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(item.value)
                    },
                )
            }
        }
    }
}

@Composable
fun AtlasDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val atlasColors = LocalAtlasColors.current
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        containerColor = atlasColors.surface3,
        iconContentColor = atlasColors.ink,
        titleContentColor = atlasColors.ink,
        textContentColor = atlasColors.ink2,
        confirmButton = confirmButton,
        dismissButton = { dismissButton?.invoke() },
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = iconContentDescription,
                )
            }
        },
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = message?.let {
            {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        containerColor = atlasColors.surface3,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        dragHandle = dragHandle ?: {
            BottomSheetDefaults.DragHandle(color = atlasColors.ink3)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen, vertical = spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            content = content,
        )
    }
}

enum class AtlasStatusTone {
    Info,
    Error,
}

@Composable
fun AtlasStatusMessage(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: AtlasStatusTone = AtlasStatusTone.Info,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val accent = if (tone == AtlasStatusTone.Error) atlasColors.risk else atlasColors.ink2
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = atlasColors.surface,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (tone == AtlasStatusTone.Error) accent else atlasColors.line2),
    ) {
        Row(
            modifier = Modifier.padding(spacing.card),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (tone == AtlasStatusTone.Error) accent else atlasColors.ink,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tone == AtlasStatusTone.Error) accent else atlasColors.ink2,
                )
            }
        }
    }
}
