package com.atlaspeak.presentation.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.dashboard.DashboardConsistency
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.dashboard.DashboardWidget
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.MetricValue
import com.atlaspeak.presentation.component.PeriodSelector
import com.atlaspeak.presentation.component.PeriodSelectorItem
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.component.PremiumIconBadge
import com.atlaspeak.presentation.component.SectionHeader
import com.atlaspeak.presentation.theme.AtlasBrushes
import com.atlaspeak.presentation.theme.LocalSpacing
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeRoute(
    onStartRoutine: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    HomeScreen(
        state = state,
        onPeriodSelected = viewModel::selectPeriod,
        onStartRoutine = onStartRoutine,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
    onStartRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        if (state.isLoading && state.snapshot == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.screen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(state.errorMessageRes ?: R.string.error_generic),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            DashboardContent(
                snapshot = state.snapshot,
                todayWorkout = state.todayWorkout,
                filters = state.filters,
                errorMessageRes = state.errorMessageRes,
                onPeriodSelected = onPeriodSelected,
                onStartRoutine = onStartRoutine,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    snapshot: DashboardSnapshot,
    todayWorkout: TodayWorkoutUiState?,
    filters: DashboardFilters,
    @StringRes errorMessageRes: Int?,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
    onStartRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            SectionHeader(
                overline = stringResource(R.string.home_greeting_overline),
                title = stringResource(R.string.home_greeting_title),
            )
        }
        if (errorMessageRes != null) {
            item {
                Text(
                    text = stringResource(errorMessageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        todayWorkout?.let { workout ->
            item {
                TodayWorkoutCard(
                    workout = workout,
                    onStartRoutine = { onStartRoutine(workout.routineId) },
                )
            }
        }
        item {
            WeeklyMinutesCard(snapshot.weeklyTrainingMinutes)
        }
        item {
            MetricCard(
                titleRes = R.string.home_widget_volume_title,
                icon = Icons.Filled.FitnessCenter,
                value = stringResource(R.string.home_value_kg, snapshot.totalVolumeKg),
                subtitle = stringResource(R.string.home_widget_volume_subtitle),
                period = filters.totalVolumePeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.TotalVolume, it) },
            )
        }
        item {
            ConsistencyCard(
                consistency = snapshot.consistency,
                period = filters.consistencyPeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.Consistency, it) },
            )
        }
        item {
            ChartMetricCard(
                titleRes = R.string.home_widget_body_weight_title,
                icon = Icons.Filled.MonitorWeight,
                value = snapshot.bodyWeightPoints.lastOrNull()?.let { stringResource(R.string.home_value_kg, it.value) }
                    ?: stringResource(R.string.home_value_empty),
                period = filters.bodyWeightPeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.BodyWeight, it) },
                points = snapshot.bodyWeightPoints,
                chartType = DashboardChartType.Line,
            )
        }
        item {
            ChartMetricCard(
                titleRes = R.string.home_widget_steps_title,
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                value = snapshot.dailySteps.lastOrNull()?.let { stringResource(R.string.home_value_steps, it.value.roundToInt()) }
                    ?: stringResource(R.string.home_value_empty),
                period = filters.dailyStepsPeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.DailySteps, it) },
                points = snapshot.dailySteps,
                chartType = DashboardChartType.Columns,
            )
        }
        item {
            ChartMetricCard(
                titleRes = R.string.home_widget_heart_rate_title,
                icon = Icons.Filled.Favorite,
                value = snapshot.heartRate.lastOrNull()?.let { stringResource(R.string.home_value_bpm, it.value.roundToInt()) }
                    ?: stringResource(R.string.home_value_empty),
                period = filters.heartRatePeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.HeartRate, it) },
                points = snapshot.heartRate,
                chartType = DashboardChartType.Line,
            )
        }
        item {
            MetricCard(
                titleRes = R.string.home_widget_activity_title,
                icon = Icons.Filled.AccessTime,
                value = stringResource(R.string.home_value_minutes, snapshot.totalActivitySeconds / 60),
                subtitle = stringResource(R.string.home_widget_activity_subtitle),
                period = filters.totalActivityPeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.TotalActivity, it) },
            )
        }
        item {
            MetricCard(
                titleRes = R.string.home_widget_sleep_title,
                icon = Icons.Filled.Bedtime,
                value = snapshot.averageSleepHours?.let { stringResource(R.string.home_value_hours, it) }
                    ?: stringResource(R.string.home_value_empty),
                subtitle = stringResource(R.string.home_widget_sleep_subtitle),
                period = filters.sleepPeriod,
                onPeriodSelected = { onPeriodSelected(DashboardWidget.Sleep, it) },
            )
        }
    }
}

@Composable
private fun WeeklyMinutesCard(minutes: Int, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AtlasBrushes.subtleSurface(colors))
                .padding(spacing.card),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primary.copy(alpha = 0.16f),
                        contentColor = colors.primary,
                    ) {
                        Icon(
                            modifier = Modifier.padding(spacing.sm),
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                        )
                    }
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.home_weekly_minutes_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                MetricValue(
                    value = minutes.toString(),
                    unit = stringResource(R.string.home_hero_unit_min),
                    emphasized = true,
                )
                MiniActivityBars(activeBars = if (minutes == 0) 0 else (minutes / 30).coerceIn(1, 7))
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    workout: TodayWorkoutUiState,
    onStartRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PremiumIconBadge {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = stringResource(R.string.home_today_workout_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_today_workout_body, workout.routineName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AtlasPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !workout.completed,
                onClick = onStartRoutine,
                text = stringResource(
                    if (workout.completed) {
                        R.string.home_today_workout_completed
                    } else {
                        R.string.home_today_workout_start
                    },
                ),
            )
        }
    }
}

@Composable
private fun MetricCard(
    @StringRes titleRes: Int,
    icon: ImageVector,
    value: String,
    subtitle: String,
    period: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCardScaffold(
        titleRes = titleRes,
        icon = icon,
        period = period,
        onPeriodSelected = onPeriodSelected,
        modifier = modifier,
    ) {
        MetricValue(value = value)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConsistencyCard(
    consistency: DashboardConsistency,
    period: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val value = stringResource(R.string.home_value_consistency, consistency.activeDays, consistency.targetDays)
    val subtitle = stringResource(
        if (consistency.usesWeeklyPlan) {
            R.string.home_widget_consistency_plan
        } else {
            R.string.home_widget_consistency_period
        },
    )
    DashboardCardScaffold(
        titleRes = R.string.home_widget_consistency_title,
        icon = Icons.Filled.TaskAlt,
        period = period,
        onPeriodSelected = onPeriodSelected,
        modifier = modifier,
    ) {
        MetricValue(value = value)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MiniActivityBars(
            activeBars = consistency.activeDays,
            totalBars = consistency.targetDays.coerceAtMost(7).coerceAtLeast(1),
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

@Composable
private fun MiniActivityBars(
    activeBars: Int,
    modifier: Modifier = Modifier,
    totalBars: Int = 7,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(totalBars.coerceAtLeast(1)) { index ->
            val active = index < activeBars.coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((18 + (index % 3) * 8).dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (active) {
                            colors.primary
                        } else {
                            colors.surfaceVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ChartMetricCard(
    @StringRes titleRes: Int,
    icon: ImageVector,
    value: String,
    period: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    points: List<DashboardPoint>,
    chartType: DashboardChartType,
    modifier: Modifier = Modifier,
) {
    DashboardCardScaffold(
        titleRes = titleRes,
        icon = icon,
        period = period,
        onPeriodSelected = onPeriodSelected,
        modifier = modifier,
    ) {
        MetricValue(value = value)
        if (points.isEmpty()) {
            Text(
                text = stringResource(R.string.home_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DashboardChart(
                points = points,
                type = chartType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
        }
    }
}

@Composable
private fun DashboardCardScaffold(
    @StringRes titleRes: Int,
    icon: ImageVector,
    period: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalSpacing.current
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PremiumIconBadge {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DashboardPeriodSelector(period, onPeriodSelected)
            content()
        }
    }
}

@Composable
private fun DashboardPeriodSelector(
    selectedPeriod: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
) {
    PeriodSelector(
        items = DashboardPeriod.entries.map { PeriodSelectorItem(it, it.labelRes()) },
        selected = selectedPeriod,
        onSelected = onPeriodSelected,
    )
}

@Composable
private fun DashboardChart(
    points: List<DashboardPoint>,
    type: DashboardChartType,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val labels = remember(points) { points.map { it.timestamp.formatDate() } }
    val bottomFormatter = remember(labels) {
        CartesianValueFormatter { _, value, _ ->
            labels.getOrNull(value.roundToInt()).orEmpty()
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurface),
    )
    LaunchedEffect(points, type) {
        modelProducer.runTransaction {
            when (type) {
                DashboardChartType.Line -> lineSeries {
                    series(points.indices.toList(), points.map { it.value })
                }
                DashboardChartType.Columns -> columnSeries {
                    series(points.indices.toList(), points.map { it.value })
                }
            }
        }
    }
    val chartDescription = stringResource(
        R.string.home_chart_summary,
        points.size,
        points.firstOrNull()?.timestamp?.formatDate().orEmpty(),
        points.lastOrNull()?.timestamp?.formatDate().orEmpty(),
        points.lastOrNull()?.value ?: 0.0,
    )
    val chart = when (type) {
        DashboardChartType.Line -> rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            marker = marker,
        )
        DashboardChartType.Columns -> rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            marker = marker,
        )
    }
    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier.semantics { contentDescription = chartDescription },
    )
}

@StringRes
private fun DashboardPeriod.labelRes(): Int = when (this) {
    DashboardPeriod.Week -> R.string.progress_period_week
    DashboardPeriod.Month -> R.string.progress_period_month
    DashboardPeriod.ThreeMonths -> R.string.progress_period_three_months
    DashboardPeriod.Year -> R.string.progress_period_year
    DashboardPeriod.YearToDate -> R.string.progress_period_ytd
}

private fun Long.formatDate(): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}

private enum class DashboardChartType {
    Line,
    Columns,
}
