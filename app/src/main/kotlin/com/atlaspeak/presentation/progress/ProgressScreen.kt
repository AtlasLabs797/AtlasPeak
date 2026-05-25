package com.atlaspeak.presentation.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.progress.ExerciseProgress
import com.atlaspeak.domain.model.progress.MuscleGroupProgress
import com.atlaspeak.domain.model.progress.ProgressHistoryItem
import com.atlaspeak.domain.model.progress.ProgressHistoryType
import com.atlaspeak.domain.model.progress.ProgressPeriod
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.presentation.cardio.CardioRouteMap
import com.atlaspeak.presentation.component.PeriodSelector
import com.atlaspeak.presentation.component.PeriodSelectorItem
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.theme.LocalSpacing
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressRoute(
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    ProgressScreen(
        state = state,
        onTabSelected = viewModel::selectTab,
        onPeriodSelected = viewModel::selectPeriod,
        onHistoryTypeSelected = viewModel::selectHistoryType,
        onHistorySearchChanged = viewModel::onHistorySearchChanged,
        onHistoryItemSelected = viewModel::selectHistoryItem,
    )
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onTabSelected: (ProgressTab) -> Unit,
    onPeriodSelected: (ProgressPeriod) -> Unit,
    onHistoryTypeSelected: (ProgressHistoryType) -> Unit,
    onHistorySearchChanged: (String) -> Unit,
    onHistoryItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.screen),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.screen_progress_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                PeriodChips(
                    selectedPeriod = state.selectedPeriod,
                    onPeriodSelected = onPeriodSelected,
                )
            }
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Tab(
                    selected = state.selectedTab == ProgressTab.History,
                    onClick = { onTabSelected(ProgressTab.History) },
                    text = { Text(stringResource(R.string.progress_tab_history)) },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                )
                Tab(
                    selected = state.selectedTab == ProgressTab.Exercises,
                    onClick = { onTabSelected(ProgressTab.Exercises) },
                    text = { Text(stringResource(R.string.progress_tab_exercises)) },
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = null) },
                )
                Tab(
                    selected = state.selectedTab == ProgressTab.MuscleGroups,
                    onClick = { onTabSelected(ProgressTab.MuscleGroups) },
                    text = { Text(stringResource(R.string.progress_tab_muscle_groups)) },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                )
            }
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.selectedTab) {
                    ProgressTab.History -> HistoryContent(
                        state = state,
                        onHistoryTypeSelected = onHistoryTypeSelected,
                        onHistorySearchChanged = onHistorySearchChanged,
                        onHistoryItemSelected = onHistoryItemSelected,
                    )
                    ProgressTab.Exercises -> ExerciseProgressContent(state.exerciseProgress)
                    ProgressTab.MuscleGroups -> MuscleGroupProgressContent(state.muscleGroupProgress)
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(
    selectedPeriod: ProgressPeriod,
    onPeriodSelected: (ProgressPeriod) -> Unit,
) {
    PeriodSelector(
        items = ProgressPeriod.entries.map { PeriodSelectorItem(it, it.labelRes()) },
        selected = selectedPeriod,
        onSelected = onPeriodSelected,
    )
}

@Composable
private fun HistoryContent(
    state: ProgressUiState,
    onHistoryTypeSelected: (ProgressHistoryType) -> Unit,
    onHistorySearchChanged: (String) -> Unit,
    onHistoryItemSelected: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.historyQuery,
                onValueChange = onHistorySearchChanged,
                label = { Text(stringResource(R.string.progress_history_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
        }
        item {
            HistoryTypeChips(
                selectedType = state.selectedHistoryType,
                onHistoryTypeSelected = onHistoryTypeSelected,
            )
        }
        if (state.history.isEmpty()) {
            item { EmptyState(R.string.progress_history_empty) }
        } else {
            items(state.history, key = { it.id }) { item ->
                HistoryItemCard(
                    item = item,
                    selected = item.id == state.selectedHistoryId,
                    onClick = { onHistoryItemSelected(item.id) },
                )
            }
        }
        state.selectedHistoryItem?.let { item ->
            item { HistoryDetailCard(item) }
        }
    }
}

@Composable
private fun HistoryTypeChips(
    selectedType: ProgressHistoryType,
    onHistoryTypeSelected: (ProgressHistoryType) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(ProgressHistoryType.entries, key = { it.name }) { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onHistoryTypeSelected(type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: ProgressHistoryItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val cardColors = CardDefaults.cardColors(
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    val secondaryTextColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = if (item is ProgressHistoryItem.Cardio) {
                    Icons.AutoMirrored.Filled.DirectionsRun
                } else {
                    Icons.Filled.FitnessCenter
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = item.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(item.fallbackTitleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.progress_history_type_date,
                        stringResource(item.typeLabelRes()),
                        item.startedAt.formatDate(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                )
            }
            Text(
                text = item.metricText(),
                style = MaterialTheme.typography.labelLarge,
                color = secondaryTextColor,
            )
        }
    }
}

@Composable
private fun ProgressHistoryItem.metricText(): String {
    return when (this) {
        is ProgressHistoryItem.Strength -> stringResource(
            R.string.progress_metric_volume_value,
            session.totalVolumeKg ?: 0.0,
        )
        is ProgressHistoryItem.Cardio -> stringResource(
            R.string.progress_metric_distance_value,
            session.distanceKm ?: 0.0,
        )
    }
}

@Composable
private fun HistoryDetailCard(item: ProgressHistoryItem) {
    val spacing = LocalSpacing.current
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            when (item) {
                is ProgressHistoryItem.Strength -> StrengthDetail(item.session)
                is ProgressHistoryItem.Cardio -> CardioDetail(item.session)
            }
        }
    }
}

@Composable
private fun StrengthDetail(session: WorkoutSession) {
    val spacing = LocalSpacing.current
    SectionTitle(R.string.progress_history_strength_detail)
    Text(
        text = session.routineName?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.progress_unknown_strength),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(R.string.workout_complete_duration, session.durationSeconds ?: 0))
    Text(stringResource(R.string.workout_complete_volume, session.totalVolumeKg ?: 0.0))
    session.exercises.forEach { exercise ->
        Text(
            text = exercise.exerciseName,
            style = MaterialTheme.typography.titleSmall,
        )
        exercise.sets.filter { it.completed }.forEach { set ->
            Text(
                text = stringResource(
                    R.string.workout_history_set_line,
                    set.setNumber,
                    set.actualReps ?: set.plannedReps,
                    set.weightKg ?: 0.0,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (set.isPersonalRecord) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
    }
}

@Composable
private fun CardioDetail(session: com.atlaspeak.domain.model.cardio.CardioSession) {
    SectionTitle(R.string.progress_history_cardio_detail)
    Text(session.cardioTypeName, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.cardio_complete_duration, session.durationSeconds ?: 0))
    Text(stringResource(R.string.cardio_complete_distance, session.distanceKm ?: 0.0))
    Text(stringResource(R.string.cardio_complete_avg_speed, session.avgSpeedKmh ?: 0.0))
    Text(stringResource(R.string.cardio_complete_calories, session.caloriesBurned ?: 0))
    if (session.route.isNotEmpty()) {
        CardioRouteMap(route = session.route)
    } else {
        Text(
            text = stringResource(R.string.cardio_route_not_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExerciseProgressContent(exercises: List<ExerciseProgress>) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        if (exercises.isEmpty()) {
            item { EmptyState(R.string.progress_exercises_empty) }
        } else {
            items(exercises, key = { it.exercise.id }) { progress ->
                ExerciseProgressCard(progress)
            }
        }
    }
}

@Composable
private fun ExerciseProgressCard(progress: ExerciseProgress) {
    val spacing = LocalSpacing.current
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = progress.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progress.exercise.muscleGroup.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MetricRow(
                metrics = listOf(
                    MetricValue(R.string.progress_metric_latest_weight, progress.latestMaxWeightKg, R.string.progress_metric_weight_value),
                    MetricValue(R.string.progress_metric_latest_volume, progress.latestVolumeKg, R.string.progress_metric_volume_value),
                    MetricValue(R.string.progress_metric_total_volume, progress.totalVolumeKg, R.string.progress_metric_volume_value),
                ),
            )
            ChartBlock(
                titleRes = R.string.progress_chart_weight_title,
                points = progress.points.mapNotNull { point ->
                    point.maxWeightKg?.let { ProgressChartPoint(point.startedAt, it) }
                },
            )
            ChartBlock(
                titleRes = R.string.progress_chart_volume_title,
                points = progress.points.map { ProgressChartPoint(it.startedAt, it.volumeKg) },
            )
        }
    }
}

@Composable
private fun MuscleGroupProgressContent(groups: List<MuscleGroupProgress>) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        if (groups.isEmpty()) {
            item { EmptyState(R.string.progress_groups_empty) }
        } else {
            items(groups, key = { it.muscleGroup.id }) { group ->
                MuscleGroupProgressCard(group)
            }
        }
    }
}

@Composable
private fun MuscleGroupProgressCard(group: MuscleGroupProgress) {
    val spacing = LocalSpacing.current
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = group.muscleGroup.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.progress_group_summary,
                    group.exercises.size,
                    group.totalVolumeKg,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.exercises.forEachIndexed { index, progress ->
                if (index > 0) HorizontalDivider()
                Text(
                    text = progress.exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetricRow(
                    metrics = listOf(
                        MetricValue(R.string.progress_metric_latest_weight, progress.latestMaxWeightKg, R.string.progress_metric_weight_value),
                        MetricValue(R.string.progress_metric_total_volume, progress.totalVolumeKg, R.string.progress_metric_volume_value),
                    ),
                )
                ChartBlock(
                    titleRes = R.string.progress_chart_volume_title,
                    points = progress.points.map { ProgressChartPoint(it.startedAt, it.volumeKg) },
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(metrics: List<MetricValue>) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        metrics.forEach { metric ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(metric.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metric.value?.let { stringResource(metric.valueRes, it) }
                        ?: stringResource(R.string.progress_metric_empty),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChartBlock(
    @StringRes titleRes: Int,
    points: List<ProgressChartPoint>,
    compact: Boolean = false,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
        )
        ProgressLineChart(
            points = points,
            title = stringResource(titleRes),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 140.dp else 180.dp),
        )
    }
}

@Composable
private fun ProgressLineChart(
    points: List<ProgressChartPoint>,
    title: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    if (points.isEmpty()) {
        EmptyState(R.string.progress_chart_empty)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    val dateLabels = remember(points) { points.map { it.startedAt.formatDate() } }
    val bottomFormatter = remember(dateLabels) {
        CartesianValueFormatter { _, value, _ ->
            dateLabels.getOrNull(value.roundToInt()).orEmpty()
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurface),
    )
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries {
                series(points.indices.toList(), points.map { it.value })
            }
        }
    }
    val chartDescription = stringResource(
        R.string.progress_chart_summary,
        title,
        points.size,
        points.first().startedAt.formatDate(),
        points.last().startedAt.formatDate(),
        points.last().value,
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            marker = marker,
        ),
        modelProducer = modelProducer,
        modifier = modifier.semantics { contentDescription = chartDescription },
    )
    if (points.size > 1) {
        Text(
            text = stringResource(
                R.string.progress_chart_date_range,
                points.first().startedAt.formatDate(),
                points.last().startedAt.formatDate(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EmptyState(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@StringRes
private fun ProgressPeriod.labelRes(): Int = when (this) {
    ProgressPeriod.Week -> R.string.progress_period_week
    ProgressPeriod.Month -> R.string.progress_period_month
    ProgressPeriod.ThreeMonths -> R.string.progress_period_three_months
    ProgressPeriod.Year -> R.string.progress_period_year
    ProgressPeriod.YearToDate -> R.string.progress_period_ytd
}

@StringRes
private fun ProgressHistoryType.labelRes(): Int = when (this) {
    ProgressHistoryType.All -> R.string.progress_filter_all
    ProgressHistoryType.Strength -> R.string.progress_history_strength
    ProgressHistoryType.Cardio -> R.string.progress_history_cardio
}

@StringRes
private fun ProgressHistoryItem.typeLabelRes(): Int = when (this) {
    is ProgressHistoryItem.Strength -> R.string.progress_history_strength
    is ProgressHistoryItem.Cardio -> R.string.progress_history_cardio
}

@StringRes
private fun ProgressHistoryItem.fallbackTitleRes(): Int = when (this) {
    is ProgressHistoryItem.Strength -> R.string.progress_unknown_strength
    is ProgressHistoryItem.Cardio -> R.string.progress_unknown_cardio
}

private fun Long.formatDate(): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}

private data class MetricValue(
    @StringRes val labelRes: Int,
    val value: Double?,
    @StringRes val valueRes: Int,
)

private data class ProgressChartPoint(
    val startedAt: Long,
    val value: Double,
)
