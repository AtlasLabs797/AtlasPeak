package com.atlaspeak.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.LocalAtlasColors
import kotlin.math.max

@Composable
fun MonochromeSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    showArea: Boolean = false,
    showEndPoint: Boolean = false,
) {
    val atlasColors = LocalAtlasColors.current
    val normalized = remember(values) { values.filter { it.isFinite() } }
    Canvas(modifier = modifier) {
        if (normalized.isEmpty()) return@Canvas
        val minValue = normalized.minOrNull() ?: 0f
        val maxValue = normalized.maxOrNull() ?: 0f
        val range = max(maxValue - minValue, 1f)
        val bottom = size.height - 4.dp.toPx()
        val top = 4.dp.toPx()
        val usableHeight = max(bottom - top, 1f)
        val step = if (normalized.size == 1) 0f else size.width / (normalized.lastIndex)
        fun point(index: Int, value: Float): Offset {
            val x = if (normalized.size == 1) size.width else index * step
            val y = bottom - ((value - minValue) / range) * usableHeight
            return Offset(x, y)
        }
        val path = Path()
        normalized.forEachIndexed { index, value ->
            val point = point(index, value)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        if (showArea && normalized.size > 1) {
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    0f to atlasColors.ink.copy(alpha = 0.16f),
                    1f to atlasColors.ink.copy(alpha = 0f),
                ),
            )
        }
        drawPath(
            path = path,
            color = atlasColors.ink,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.cornerPathEffect(10.dp.toPx()),
            ),
        )
        if (showEndPoint) {
            val point = point(normalized.lastIndex, normalized.last())
            drawCircle(color = atlasColors.ink, radius = 3.5.dp.toPx(), center = point)
        }
    }
}

@Composable
fun MonochromeAreaChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    gridFraction: Float = 0.56f,
) {
    val atlasColors = LocalAtlasColors.current
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * gridFraction.coerceIn(0.1f, 0.9f)
            drawLine(
                color = atlasColors.grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
            )
        }
        MonochromeSparkline(
            values = values,
            modifier = Modifier.fillMaxSize(),
            showArea = true,
            showEndPoint = true,
        )
    }
}

@Composable
fun MonochromeBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = values.indices.maxByOrNull { values[it] } ?: -1,
    footer: @Composable (BoxScope.() -> Unit)? = null,
) {
    val atlasColors = LocalAtlasColors.current
    val safeValues = remember(values) { values.map { if (it.isFinite()) max(it, 0f) else 0f } }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (safeValues.isEmpty()) return@Canvas
            val gap = 10.dp.toPx()
            val maxValue = max(safeValues.maxOrNull() ?: 1f, 1f)
            val barWidth = (size.width - gap * (safeValues.size - 1)) / safeValues.size
            safeValues.forEachIndexed { index, value ->
                val barHeight = (value / maxValue) * (size.height - 4.dp.toPx())
                val left = index * (barWidth + gap)
                val top = size.height - barHeight
                drawRoundRect(
                    color = if (index == selectedIndex) atlasColors.ink else atlasColors.ink.copy(alpha = 0.22f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }
        }
        footer?.invoke(this)
    }
}

@Composable
fun MiniChartHeight(content: @Composable () -> Unit) {
    Box(modifier = Modifier.height(88.dp)) {
        content()
    }
}
