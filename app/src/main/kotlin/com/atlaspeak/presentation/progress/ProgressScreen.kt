package com.atlaspeak.presentation.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.atlaspeak.presentation.component.AtlasChip
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.PeriodSelector
import com.atlaspeak.presentation.component.PeriodSelectorItem
import com.atlaspeak.presentation.component.MonochromeAreaChart
import com.atlaspeak.presentation.component.MonochromeBarChart
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.component.formatDurationSeconds
import com.atlaspeak.presentation.theme.LocalAtlasColors
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
    val atlasColors = LocalAtlasColors.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screen, vertical = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            ) {
                ProgressHeader()
                PeriodChips(
                    selectedPeriod = state.selectedPeriod,
                    onPeriodSelected = onPeriodSelected,
                )
                if (!state.isLoading) {
                    StatsOverview(state = state)
                }
            }
            ProgressTabSelector(selected = state.selectedTab, onTabSelected = onTabSelected)
            state.errorMessageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = atlasColors.risk,
                    modifier = Modifier.padding(horizontal = spacing.screen),
                )
            }
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = atlasColors.ink)
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
private fun ProgressHeader() {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.progress_stats_overline).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = atlasColors.ink3,
            )
            Text(
                text = stringResource(R.string.progress_title_evolution),
                style = MaterialTheme.typography.headlineMedium,
                color = atlasColors.ink,
            )
        }
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = atlasColors.ink2,
        )
    }
}

@Composable
private fun ProgressTabSelector(
    selected: ProgressTab,
    onTabSelected: (ProgressTab) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = spacing.screen)
            .clip(MaterialTheme.shapes.large)
            .background(atlasColors.fillSoft)
            .padding(spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        ProgressTab.entries.forEach { tab ->
            val active = selected == tab
            androidx.compose.material3.Surface(
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(tab) },
                shape = MaterialTheme.shapes.medium,
                color = if (active) atlasColors.ink else Color.Transparent,
                contentColor = if (active) atlasColors.onAccent else atlasColors.ink2,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tab.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsOverview(state: ProgressUiState) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.cardGap)) {
        VolumeHero(state.exerciseProgress)
        StrengthFeature(state.exerciseProgress)
        WeeklyLoadBars(state.history)
    }
}

@Composable
private fun VolumeHero(exercises: List<ExerciseProgress>) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val points = remember(exercises) { volumeSeries(exercises) }
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.progress_volume_total_overline).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = atlasColors.ink3,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatWhole(points.sumOf { it.toDouble() }),
                    style = MaterialTheme.typography.displayMedium,
                    color = atlasColors.ink,
                )
                Text(
                    text = stringResource(R.string.unit_kg),
                    modifier = Modifier.padding(start = spacing.xs, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = atlasColors.ink3,
                )
            }
            MonochromeAreaChart(
                values = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
            )
        }
    }
}

@Composable
private fun StrengthFeature(exercises: List<ExerciseProgress>) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val strongest = remember(exercises) {
        exercises.maxByOrNull { it.latestMaxWeightKg ?: 0.0 }
    }
    val points = strongest?.points.orEmpty().mapNotNull { it.maxWeightKg?.toFloat() }
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.progress_strength_overline).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = atlasColors.ink3,
                    )
                    Text(
                        text = strongest?.exercise?.name ?: stringResource(R.string.progress_metric_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = atlasColors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RecordChip()
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = strongest?.latestMaxWeightKg?.let { "%.1f".format(Locale.US, it) }
                        ?: stringResource(R.string.home_value_empty),
                    style = MaterialTheme.typography.displaySmall,
                    color = atlasColors.ink,
                )
                Text(
                    text = stringResource(R.string.unit_kg),
                    modifier = Modifier.padding(start = spacing.xs, bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = atlasColors.ink3,
                )
            }
            MonochromeAreaChart(
                values = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp),
            )
        }
    }
}

@Composable
private fun RecordChip() {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(atlasColors.fillSoft)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = atlasColors.ink,
        )
        Text(
            text = stringResource(R.string.progress_record_chip).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = atlasColors.ink,
        )
    }
}

@Composable
private fun WeeklyLoadBars(history: List<ProgressHistoryItem>) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val bars = remember(history) { weeklyHistoryBars(history) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(R.string.progress_weekly_load_overline).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = atlasColors.ink3,
        )
        MonochromeBarChart(
            values = bars,
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp),
        )
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
            AtlasTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.historyQuery,
                onValueChange = onHistorySearchChanged,
                label = stringResource(R.string.progress_history_search),
                keyboardType = KeyboardType.Text,
                leadingIcon = Icons.Filled.Search,
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
            AtlasChip(
                selected = selectedType == type,
                onClick = { onHistoryTypeSelected(type) },
                text = stringResource(type.labelRes()),
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
    val atlasColors = LocalAtlasColors.current
    val secondaryTextColor = if (selected) atlasColors.ink else atlasColors.ink3
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) atlasColors.fillActive else atlasColors.surface,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (selected) atlasColors.ink else atlasColors.line2),
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
                tint = if (selected) atlasColors.ink else atlasColors.ink2,
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
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
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
    val atlasColors = LocalAtlasColors.current
    SectionTitle(R.string.progress_history_strength_detail)
    Text(
        text = session.routineName?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.progress_unknown_strength),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(
            R.string.workout_complete_duration,
            formatDurationSeconds((session.durationSeconds ?: 0).toLong()),
        ),
    )
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
                    atlasColors.ink
                } else {
                    atlasColors.ink3
                },
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = spacing.xs),
            color = atlasColors.line1,
        )
    }
}

@Composable
private fun CardioDetail(session: com.atlaspeak.domain.model.cardio.CardioSession) {
    val atlasColors = LocalAtlasColors.current
    SectionTitle(R.string.progress_history_cardio_detail)
    Text(session.cardioTypeName, style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(
            R.string.cardio_complete_duration,
            formatDurationSeconds((session.durationSeconds ?: 0).toLong()),
        ),
    )
    Text(stringResource(R.string.cardio_complete_distance, session.distanceKm ?: 0.0))
    Text(stringResource(R.string.cardio_complete_avg_speed, session.avgSpeedKmh ?: 0.0))
    Text(stringResource(R.string.cardio_complete_calories, session.caloriesBurned ?: 0))
    if (session.route.isNotEmpty()) {
        CardioRouteMap(route = session.route)
    } else {
        Text(
            text = stringResource(R.string.cardio_route_not_saved),
            style = MaterialTheme.typography.bodySmall,
            color = atlasColors.ink3,
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
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
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
                color = atlasColors.ink3,
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
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
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
                color = atlasColors.ink3,
            )
            group.exercises.forEachIndexed { index, progress ->
                if (index > 0) HorizontalDivider(color = atlasColors.line1)
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
    val atlasColors = LocalAtlasColors.current
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
                    color = atlasColors.ink3,
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
    val validPoints = remember(points) { points.filter { it.value.isFinite() } }
    if (validPoints.isEmpty()) {
        EmptyState(R.string.progress_chart_empty)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    val dateLabels = remember(validPoints) { validPoints.map { it.startedAt.formatDate() } }
    val bottomFormatter = remember(dateLabels) {
        CartesianValueFormatter { _, value, _ ->
            dateLabels.getOrNull(value.roundToInt()).orEmpty()
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurface),
    )
    LaunchedEffect(validPoints) {
        modelProducer.runTransaction {
            lineSeries {
                series(validPoints.indices.toList(), validPoints.map { it.value })
            }
        }
    }
    val chartDescription = stringResource(
        R.string.progress_chart_summary,
        title,
        validPoints.size,
        validPoints.first().startedAt.formatDate(),
        validPoints.last().startedAt.formatDate(),
        validPoints.last().value,
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
    if (validPoints.size > 1) {
        val atlasColors = LocalAtlasColors.current
        Text(
            text = stringResource(
                R.string.progress_chart_date_range,
                validPoints.first().startedAt.formatDate(),
                validPoints.last().startedAt.formatDate(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = atlasColors.ink3,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    val atlasColors = LocalAtlasColors.current
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = atlasColors.ink,
    )
}

@Composable
private fun EmptyState(@StringRes textRes: Int) {
    val atlasColors = LocalAtlasColors.current
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = atlasColors.ink3,
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

private fun ProgressTab.icon() = when (this) {
    ProgressTab.History -> Icons.Filled.History
    ProgressTab.Exercises -> Icons.Filled.Analytics
    ProgressTab.MuscleGroups -> Icons.Filled.Groups
}

private fun volumeSeries(exercises: List<ExerciseProgress>): List<Float> {
    return exercises
        .flatMap { it.points }
        .groupBy { it.startedAt }
        .toSortedMap()
        .values
        .map { points -> points.sumOf { it.volumeKg }.toFloat() }
        .ifEmpty { listOf(0f) }
}

private fun weeklyHistoryBars(history: List<ProgressHistoryItem>): List<Float> {
    val byDay = history
        .groupBy {
            Instant.ofEpochMilli(it.startedAt)
                .atZone(ZoneId.systemDefault())
                .dayOfWeek
                .value
        }
    return (1..7).map { day ->
        byDay[day].orEmpty().sumOf { item ->
            when (item) {
                is ProgressHistoryItem.Strength -> item.session.totalVolumeKg ?: 0.0
                is ProgressHistoryItem.Cardio -> ((item.durationSeconds ?: 0) / 60.0).coerceAtLeast(0.0)
            }
        }.toFloat()
    }
}

private fun formatWhole(value: Double): String =
    "%,d".format(Locale.US, value.roundToInt()).replace(',', ' ')

private data class MetricValue(
    @StringRes val labelRes: Int,
    val value: Double?,
    @StringRes val valueRes: Int,
)

private data class ProgressChartPoint(
    val startedAt: Long,
    val value: Double,
)
