package com.atlaspeak.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.LocalAtlasColors

@Composable
fun PremiumBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val atlasColors = LocalAtlasColors.current
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to atlasColors.ground,
                    0.72f to atlasColors.ground,
                    1f to atlasColors.surface.copy(alpha = 0.72f),
                ),
            ),
        content = content,
    )
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues? = null,
    content: @Composable () -> Unit,
) {
    val atlasColors = LocalAtlasColors.current
    Surface(
        modifier = modifier,
        shape = shape,
        color = atlasColors.surface,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, atlasColors.line2),
    ) {
        if (contentPadding != null) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
fun PremiumIconBadge(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    filled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val atlasColors = LocalAtlasColors.current
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = if (filled) atlasColors.ink else atlasColors.fillActive,
        contentColor = if (filled) atlasColors.onAccent else atlasColors.ink2,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (filled) atlasColors.lineStrong else atlasColors.line2),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
