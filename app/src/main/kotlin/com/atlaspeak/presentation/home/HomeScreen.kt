package com.atlaspeak.presentation.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.dashboard.DashboardWidget
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.MonochromeBarChart
import com.atlaspeak.presentation.component.MonochromeSparkline
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.AtlasBrushes
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeRoute(
    onStartRoutine: (String) -> Unit,
    onStartCardio: (String, Int) -> Unit,
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
        onStartCardio = onStartCardio,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartCardio: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.snapshot == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = atlasColors.ink)
                }
            }
            state.snapshot == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.screen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(state.errorMessageRes ?: R.string.error_generic),
                        style = MaterialTheme.typography.bodyLarge,
                        color = atlasColors.ink,
                    )
                }
            }
            else -> {
                DashboardContent(
                    greetingName = state.greetingName,
                    snapshot = state.snapshot,
                    todayWorkout = state.todayWorkout,
                    filters = state.filters,
                    errorMessageRes = state.errorMessageRes,
                    onPeriodSelected = onPeriodSelected,
                    onStartRoutine = onStartRoutine,
                    onStartCardio = onStartCardio,
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    greetingName: String?,
    snapshot: DashboardSnapshot,
    todayWorkout: TodayWorkoutUiState?,
    filters: DashboardFilters,
    @StringRes errorMessageRes: Int?,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartCardio: (String, Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.screen,
            top = spacing.lg,
            end = spacing.screen,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            HomeHeader(greetingName = greetingName)
        }
        errorMessageRes?.let { messageRes ->
            item {
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = atlasColors.risk,
                )
            }
        }
        item {
            WeeklyLoadHero(snapshot = snapshot)
        }
        item {
            MetricCarousel(
                snapshot = snapshot,
                filters = filters,
                onPeriodSelected = onPeriodSelected,
            )
        }
        todayWorkout?.let { workout ->
            item {
                TodayWorkoutCard(
                    workout = workout,
                    onStart = {
                        if (workout.type == TodayWorkoutType.Cardio) {
                            val cardioTypeId = workout.cardioTypeId ?: return@TodayWorkoutCard
                            onStartCardio(cardioTypeId, workout.cardioTargetDurationSec ?: 0)
                        } else {
                            val routineId = workout.routineId ?: return@TodayWorkoutCard
                            onStartRoutine(routineId)
                        }
                    },
                )
            }
        }
        item {
            SecondaryMetrics(snapshot = snapshot)
        }
    }
}

@Composable
private fun HomeHeader(greetingName: String?) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = currentDateLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = atlasColors.ink3,
            )
            Text(
                text = greetingName?.let { stringResource(R.string.home_greeting_name, it) }
                    ?: stringResource(R.string.home_greeting_title),
                style = MaterialTheme.typography.headlineMedium,
                color = atlasColors.ink,
            )
        }
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = atlasColors.ink2,
        )
    }
}

@Composable
private fun WeeklyLoadHero(snapshot: DashboardSnapshot) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.home_weekly_load_overline).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = atlasColors.ink3,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = snapshot.weeklyTrainingMinutes.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = atlasColors.ink,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.home_hero_unit_min),
                modifier = Modifier.padding(start = spacing.sm, bottom = 13.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = atlasColors.ink3,
            )
        }
        Text(
            text = stringResource(
                R.string.home_delta_vs_plan,
                snapshot.consistency.activeDays,
                snapshot.consistency.targetDays.coerceAtLeast(1),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = atlasColors.ink,
        )
        MonochromeBarChart(
            values = weeklyLoadBars(snapshot),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        )
    }
}

@Composable
private fun MetricCarousel(
    snapshot: DashboardSnapshot,
    filters: DashboardFilters,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
) {
    val spacing = LocalSpacing.current
    val tiles = listOf(
        DashboardTile(
            labelRes = R.string.home_widget_volume_title,
            value = formatWhole(snapshot.totalVolumeKg),
            unitRes = R.string.unit_kg,
            icon = Icons.Filled.FitnessCenter,
            points = emptyList(),
            onClick = { onPeriodSelected(DashboardWidget.TotalVolume, filters.totalVolumePeriod.next()) },
        ),
        DashboardTile(
            labelRes = R.string.home_widget_steps_title,
            value = snapshot.dailySteps.lastOrNull()?.value?.roundToInt()?.toString()
                ?: stringResource(R.string.home_value_empty),
            unitRes = R.string.unit_steps,
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            points = snapshot.dailySteps,
            onClick = { onPeriodSelected(DashboardWidget.DailySteps, filters.dailyStepsPeriod.next()) },
        ),
        DashboardTile(
            labelRes = R.string.home_widget_heart_rate_title,
            value = snapshot.heartRate.lastOrNull()?.value?.roundToInt()?.toString()
                ?: stringResource(R.string.home_value_empty),
            unitRes = R.string.unit_bpm,
            icon = Icons.Filled.Favorite,
            points = snapshot.heartRate,
            onClick = { onPeriodSelected(DashboardWidget.HeartRate, filters.heartRatePeriod.next()) },
        ),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        contentPadding = PaddingValues(end = spacing.lg),
    ) {
        items(tiles) { tile ->
            MetricTile(tile = tile)
        }
    }
}

@Composable
private fun MetricTile(tile: DashboardTile) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(
        modifier = Modifier
            .width(164.dp)
            .height(138.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = tile.onClick)
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = atlasColors.ink2,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = tile.value,
                    style = MaterialTheme.typography.displaySmall,
                    color = atlasColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(tile.unitRes),
                    modifier = Modifier.padding(start = spacing.xs, bottom = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = atlasColors.ink3,
                    maxLines = 1,
                )
            }
            Text(
                text = stringResource(tile.labelRes).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = atlasColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tile.points.isNotEmpty()) {
                MonochromeSparkline(
                    values = tile.points.map { it.value.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    workout: TodayWorkoutUiState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AtlasBrushes.subtleSurface(MaterialTheme.colorScheme))
                .padding(spacing.card),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.home_today_session_overline).uppercase(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = atlasColors.ink3,
                    )
                    Icon(
                        imageVector = if (workout.type == TodayWorkoutType.Cardio) {
                            Icons.AutoMirrored.Filled.DirectionsBike
                        } else {
                            Icons.Filled.FitnessCenter
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = atlasColors.ink2,
                    )
                }
                Text(
                    text = workout.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = atlasColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_today_workout_body, workout.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = atlasColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AtlasPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !workout.completed,
                    onClick = onStart,
                    leadingIcon = Icons.Filled.PlayArrow,
                    text = stringResource(
                        if (workout.completed) {
                            R.string.home_today_workout_completed
                        } else if (workout.type == TodayWorkoutType.Cardio) {
                            R.string.home_today_cardio_start
                        } else {
                            R.string.home_today_workout_start
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun SecondaryMetrics(snapshot: DashboardSnapshot) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CompactMetricRow(
            labelRes = R.string.home_widget_activity_title,
            value = stringResource(R.string.home_value_minutes, snapshot.totalActivitySeconds / 60),
            icon = Icons.Filled.Schedule,
        )
        CompactMetricRow(
            labelRes = R.string.home_widget_consistency_title,
            value = stringResource(
                R.string.home_value_consistency,
                snapshot.consistency.activeDays,
                snapshot.consistency.targetDays.coerceAtLeast(1),
            ),
            icon = Icons.Filled.FitnessCenter,
        )
    }
}

@Composable
private fun CompactMetricRow(
    @StringRes labelRes: Int,
    value: String,
    icon: ImageVector,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(atlasColors.surface)
            .padding(spacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Surface(
            shape = CircleShape,
            color = atlasColors.fillActive,
            contentColor = atlasColors.ink2,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(spacing.xs)
                    .size(18.dp),
            )
        }
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = atlasColors.ink2,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = atlasColors.ink,
        )
    }
}

private data class DashboardTile(
    @StringRes val labelRes: Int,
    val value: String,
    @StringRes val unitRes: Int,
    val icon: ImageVector,
    val points: List<DashboardPoint>,
    val onClick: () -> Unit,
)

private fun DashboardPeriod.next(): DashboardPeriod = when (this) {
    DashboardPeriod.Week -> DashboardPeriod.Month
    DashboardPeriod.Month -> DashboardPeriod.ThreeMonths
    DashboardPeriod.ThreeMonths -> DashboardPeriod.Year
    DashboardPeriod.Year -> DashboardPeriod.YearToDate
    DashboardPeriod.YearToDate -> DashboardPeriod.Week
}

private fun weeklyLoadBars(snapshot: DashboardSnapshot): List<Float> {
    val activeDays = snapshot.consistency.activeDays.coerceAtLeast(0)
    val targetDays = snapshot.consistency.targetDays.coerceIn(1, 7)
    return List(7) { index ->
        when {
            index < activeDays -> 0.45f + (index % 3) * 0.18f
            index < targetDays -> 0.24f
            else -> 0.12f
        }
    }
}

private fun formatWhole(value: Double): String =
    "%,d".format(Locale.US, value.roundToInt()).replace(',', ' ')

private fun currentDateLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE dd · MMMM", Locale.getDefault())
    return Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(ZoneId.systemDefault())
        .format(formatter)
        .uppercase(Locale.getDefault())
}
