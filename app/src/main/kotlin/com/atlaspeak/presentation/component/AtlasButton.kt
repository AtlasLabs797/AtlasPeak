package com.atlaspeak.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.LocalSpacing

private val AtlasButtonShape = RoundedCornerShape(16.dp)

@Composable
fun AtlasPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val spacing = LocalSpacing.current
    Button(
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        onClick = onClick,
        shape = AtlasButtonShape,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        ButtonRow(text, leadingIcon, iconContentDescription)
    }
}

@Composable
fun AtlasSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val spacing = LocalSpacing.current
    OutlinedButton(
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        onClick = onClick,
        shape = AtlasButtonShape,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
        ),
    ) {
        ButtonRow(text, leadingIcon, iconContentDescription)
    }
}

@Composable
fun AtlasGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val spacing = LocalSpacing.current
    TextButton(
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        onClick = onClick,
        shape = AtlasButtonShape,
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs),
    ) {
        ButtonRow(text, leadingIcon, iconContentDescription)
    }
}

@Composable
private fun ButtonRow(
    text: String,
    leadingIcon: ImageVector?,
    iconContentDescription: String?,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
