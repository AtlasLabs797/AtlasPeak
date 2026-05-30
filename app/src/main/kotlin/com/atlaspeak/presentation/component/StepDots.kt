package com.atlaspeak.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.AtlasMotion
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun StepDots(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val isCurrent = index == current
            val isPast = index < current
            val width by animateDpAsState(
                targetValue = when {
                    isCurrent -> 28.dp
                    else -> 8.dp
                },
                animationSpec = tween(AtlasMotion.DurationMedium, easing = AtlasMotion.EmphasizedEasing),
                label = "",
            )
            val color by animateColorAsState(
                targetValue = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                },
                animationSpec = tween(AtlasMotion.DurationMedium, easing = AtlasMotion.EmphasizedEasing),
                label = "",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}
