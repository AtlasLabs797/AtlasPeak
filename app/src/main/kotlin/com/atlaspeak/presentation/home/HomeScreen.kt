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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
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
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        viewModel.refresh(syncBefore = true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    HomeScreen(
        state = state,
        onPeriodSelected = viewModel::selectPeriod,
        onRetry = { viewModel.refresh() },
        onStartRoutine = onStartRoutine,
        onStartCardio = onStartCardio,
        onOpenHealthConnectPermissions = {
            healthConnectPermissionLauncher.launch(viewModel.requiredHealthConnectPermissions())
        },
        onDismissHealthConnect = viewModel::dismissHealthConnectSyncStatus,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
    onRetry: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartCardio: (String, Int) -> Unit,
    onOpenHealthConnectPermissions: () -> Unit,
    onDismissHealthConnect: () -> Unit,
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Text(
                            text = stringResource(state.errorMessageRes ?: R.string.error_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            color = atlasColors.ink,
                        )
                        AtlasPrimaryButton(
                            onClick = onRetry,
                            text = stringResource(R.string.action_retry),
                        )
                    }
                }
            }
            else -> {
                DashboardContent(
                    greetingName = state.greetingName,
                    snapshot = state.snapshot,
                    todayWorkouts = state.todayWorkouts,
                    filters = state.filters,
                    errorMessageRes = state.errorMessageRes,
                    healthConnectSync = state.healthConnectSync,
                    onOpenHealthConnectPermissions = onOpenHealthConnectPermissions,
                    onDismissHealthConnect = onDismissHealthConnect,
                    onPeriodSelected = onPeriodSelected,
                    onRetry = onRetry,
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
    todayWorkouts: List<TodayWorkoutUiState>,
    filters: DashboardFilters,
    @StringRes errorMessageRes: Int?,
    healthConnectSync: HomeHealthConnectSync,
    onOpenHealthConnectPermissions: () -> Unit,
    onDismissHealthConnect: () -> Unit,
    onPeriodSelected: (DashboardWidget, DashboardPeriod) -> Unit,
    onRetry: () -> Unit,
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
        if (healthConnectSync !is HomeHealthConnectSync.Idle) {
            item {
                HealthConnectStatusBanner(
                    status = healthConnectSync,
                    onOpenPermissions = onOpenHealthConnectPermissions,
                    onDismiss = onDismissHealthConnect,
                )
            }
        }
        errorMessageRes?.let { messageRes ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = atlasColors.risk,
                    )
                    AtlasPrimaryButton(
                        onClick = onRetry,
                        text = stringResource(R.string.action_retry),
                    )
                }
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
        items(
            items = todayWorkouts,
            key = { workout -> "${workout.type}_${workout.orderIndex}_${workout.title}" },
        ) { workout ->
            TodayWorkoutCard(
                workout = workout,
                onStart = {
                    if (!workout.canStart) return@TodayWorkoutCard
                    if (workout.type == TodayWorkoutType.Cardio) {
                        val cardioTypeId = workout.cardioTypeId ?: return@TodayWorkoutCard
                        val targetSeconds = workout.cardioTargetDurationSec ?: return@TodayWorkoutCard
                        onStartCardio(cardioTypeId, targetSeconds)
                    } else {
                        val routineId = workout.routineId ?: return@TodayWorkoutCard
                        onStartRoutine(routineId)
                    }
                },
            )
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
    val weeklyBars = weeklyLoadBars(snapshot)
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
        if (weeklyBars.any { it > 0f }) {
            MonochromeBarChart(
                values = weeklyBars,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.home_weekly_load_empty),
                style = MaterialTheme.typography.bodySmall,
                color = atlasColors.ink3,
            )
        }
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
            points = snapshot.volumePoints,
            periodLabel = periodLabel(filters.totalVolumePeriod),
            onClick = { onPeriodSelected(DashboardWidget.TotalVolume, filters.totalVolumePeriod.next()) },
        ),
        DashboardTile(
            labelRes = R.string.home_widget_steps_title,
            value = snapshot.dailySteps.lastOrNull()?.value?.roundToInt()?.toString()
                ?: stringResource(R.string.home_value_empty),
            unitRes = R.string.unit_steps,
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            points = snapshot.dailySteps,
            periodLabel = periodLabel(filters.dailyStepsPeriod),
            onClick = { onPeriodSelected(DashboardWidget.DailySteps, filters.dailyStepsPeriod.next()) },
        ),
        DashboardTile(
            labelRes = R.string.home_widget_heart_rate_title,
            value = snapshot.heartRate.lastOrNull()?.value?.roundToInt()?.toString()
                ?: stringResource(R.string.home_value_empty),
            unitRes = R.string.unit_bpm,
            icon = Icons.Filled.Favorite,
            points = snapshot.heartRate,
            periodLabel = periodLabel(filters.heartRatePeriod),
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
            .height(154.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = tile.onClick)
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = atlasColors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = tile.periodLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = atlasColors.ink3,
                    maxLines = 1,
                )
            }
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
private fun periodLabel(period: DashboardPeriod): String = when (period) {
    DashboardPeriod.Week -> stringResource(R.string.dashboard_period_week)
    DashboardPeriod.Month -> stringResource(R.string.dashboard_period_month)
    DashboardPeriod.ThreeMonths -> stringResource(R.string.dashboard_period_three_months)
    DashboardPeriod.Year -> stringResource(R.string.dashboard_period_year)
    DashboardPeriod.YearToDate -> stringResource(R.string.dashboard_period_year_to_date)
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
                    text = workout.statusMessageRes?.let { stringResource(it) }
                        ?: stringResource(R.string.home_today_workout_body, workout.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (workout.statusMessageRes != null) atlasColors.risk else atlasColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AtlasPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !workout.completed && workout.canStart,
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
    val periodLabel: String,
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
    // Antes (#5 del informe): fórmula determinista inventada que no reflejaba los
    // minutos reales. Ahora pintamos minutos/día normalizados al máximo de la
    // semana, con hueco para los días futuros (0f) y los días sin entrenar (0.04f
    // base para que la barra sea visible pero no engañe).
    val today = java.time.LocalDate.now()
    val mondayThisWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val pointsByDate = snapshot.weeklyMinutesPoints.associateBy { point ->
        java.time.Instant.ofEpochMilli(point.timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }
    val maxMinutes = pointsByDate.values.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 0.0
    return (0L until 7L).map { offset ->
        val date = mondayThisWeek.plusDays(offset)
        val minutes = pointsByDate[date]?.value ?: 0.0
        when {
            maxMinutes <= 0.0 -> 0f
            else -> (minutes / maxMinutes).toFloat().coerceIn(0f, 1f)
        }
    }
}

private fun formatWhole(value: Double): String =
    "%,d".format(Locale.US, value.roundToInt()).replace(',', ' ')

/**
 * BUG-095 (Fase 6 P1): banner discreto que muestra al usuario el estado real de
 * la sincronizacion con Health Connect. Solo aparece cuando hay algo que
 * comunicar (Syncing/Success/Partial/MissingPermissions/UpdateRequired/
 * Unavailable/Failed) y permite descartar el aviso cuando es accionable.
 */
@Composable
private fun HealthConnectStatusBanner(
    status: HomeHealthConnectSync,
    onOpenPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val atlasColors = LocalAtlasColors.current
    val (icon, text, actionRes) = when (status) {
        HomeHealthConnectSync.Idle -> Triple(Icons.Default.CheckCircle, null, null)
        HomeHealthConnectSync.Syncing -> Triple(
            Icons.Default.Schedule,
            stringResource(R.string.home_health_connect_syncing),
            null,
        )
        is HomeHealthConnectSync.Success -> Triple(
            Icons.Default.CheckCircle,
            stringResource(R.string.home_health_connect_success, formatRelativeAgo(status.timestampMillis)),
            null,
        )
        is HomeHealthConnectSync.PartialSuccess -> Triple(
            Icons.Default.Warning,
            stringResource(R.string.home_health_connect_partial),
            null,
        )
        HomeHealthConnectSync.MissingPermissions -> Triple(
            Icons.Default.Warning,
            stringResource(R.string.home_health_connect_missing_permissions),
            R.string.home_health_connect_missing_permissions_action,
        )
        HomeHealthConnectSync.UpdateRequired -> Triple(
            Icons.Default.Warning,
            stringResource(R.string.home_health_connect_update_required),
            null,
        )
        HomeHealthConnectSync.Unavailable -> Triple(
            Icons.Default.Error,
            stringResource(R.string.home_health_connect_unavailable),
            null,
        )
        is HomeHealthConnectSync.Failed -> Triple(
            Icons.Default.Error,
            stringResource(R.string.home_health_connect_failed),
            null,
        )
    }
    if (text == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = atlasColors.ink2,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = atlasColors.ink2,
        )
        if (actionRes != null) {
            AtlasPrimaryButton(
                onClick = onOpenPermissions,
                text = stringResource(actionRes),
            )
        }
        AtlasPrimaryButton(
            onClick = onDismiss,
            text = stringResource(R.string.home_health_connect_dismiss),
        )
    }
}

/**
 * Formatea el tiempo transcurrido desde `timestampMillis` hasta ahora en
 * formato corto ("instantes", "5 min", "2 h"). Solo se usa para mensajes
 * discretos de la UI.
 */
@Composable
private fun formatRelativeAgo(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestampMillis).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    return when {
        minutes < 1L -> stringResource(R.string.home_health_connect_success_just_now)
        minutes < 60L -> stringResource(R.string.home_health_connect_minutes_short, minutes.toInt())
        else -> stringResource(R.string.home_health_connect_hours_short, hours.toInt())
    }
}

private fun currentDateLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE dd · MMMM", Locale.getDefault())
    return Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(ZoneId.systemDefault())
        .format(formatter)
        .uppercase(Locale.getDefault())
}
