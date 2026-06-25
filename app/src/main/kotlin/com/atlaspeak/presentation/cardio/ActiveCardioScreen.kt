package com.atlaspeak.presentation.cardio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSecondaryButton
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun ActiveCardioRoute(
    onCardioCompleted: (String) -> Unit,
    onCardioCancelled: () -> Unit,
    viewModel: ActiveCardioViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.startTrackingService(locationAllowed = granted)
    }

    LaunchedEffect(state.session?.id, state.trackerServiceStartHandled) {
        val session = state.session ?: return@LaunchedEffect
        if (state.trackerServiceStartHandled) return@LaunchedEffect
        if (
            session.hasGps &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            viewModel.startTrackingService(locationAllowed = true)
        }
    }

    LaunchedEffect(state.completedSessionId) {
        state.completedSessionId?.let(onCardioCompleted)
    }

    LaunchedEffect(state.cancelled) {
        if (state.cancelled) onCardioCancelled()
    }

    BackHandler(enabled = state.session != null && state.completedSessionId == null) {
        viewModel.cancelCardio()
    }

    ActiveCardioScreen(
        state = state,
        onManualDistanceChanged = viewModel::onManualDistanceChanged,
        onManualSpeedChanged = viewModel::onManualSpeedChanged,
        onCompleteCardio = viewModel::completeCardio,
        onCancelCardio = viewModel::cancelCardio,
    )
}

@Composable
fun ActiveCardioScreen(
    state: ActiveCardioUiState,
    onManualDistanceChanged: (String) -> Unit,
    onManualSpeedChanged: (String) -> Unit,
    onCompleteCardio: () -> Unit,
    onCancelCardio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val session = state.session
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.cardio_active_missing_session))
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.screen),
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            ) {
                CardioHeaderCard(state)
                ActiveCardioMessageText(state.message)
                CardioMetricsGrid(state)
                if (state.requiresManualMetrics) {
                    ManualDistanceCard(
                        distance = state.manualDistanceKm,
                        speed = state.manualAvgSpeedKmh,
                        onDistanceChange = onManualDistanceChanged,
                        onSpeedChange = onManualSpeedChanged,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    AtlasSecondaryButton(
                        modifier = Modifier.weight(1f),
                        onClick = onCancelCardio,
                        text = stringResource(R.string.action_cancel),
                    )
                    AtlasPrimaryButton(
                        modifier = Modifier.weight(1f),
                        onClick = onCompleteCardio,
                        enabled = !state.completionInProgress,
                        text = stringResource(R.string.cardio_finish_action),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardioHeaderCard(state: ActiveCardioUiState) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val session = state.session ?: return
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                    contentDescription = null,
                    tint = atlasColors.ink2,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.workout_live_overline).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = atlasColors.ink3,
                    )
                    Text(
                        text = session.cardioTypeName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = atlasColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(if (state.mode is CardioMode.Countdown) R.string.cardio_active_mode_countdown else R.string.cardio_active_mode_timer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = atlasColors.ink2,
                    )
                }
            }
            Text(
                text = formatElapsed(state.elapsedSeconds),
                style = MaterialTheme.typography.displayMedium,
                color = atlasColors.ink,
            )
            state.remainingSeconds?.let { remaining ->
                Text(
                    text = stringResource(R.string.cardio_active_remaining, formatElapsed(remaining)),
                    style = MaterialTheme.typography.titleMedium,
                    color = atlasColors.ink2,
                )
            }
        }
    }
}

@Composable
private fun CardioMetricsGrid(state: ActiveCardioUiState) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Filled.Route, contentDescription = null, tint = atlasColors.ink2) },
                labelRes = R.string.cardio_metric_distance,
                value = stringResource(R.string.cardio_metric_distance_value, state.distanceKm),
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Filled.Speed, contentDescription = null, tint = atlasColors.ink2) },
                labelRes = R.string.cardio_metric_current_speed,
                value = stringResource(R.string.cardio_metric_speed_value, state.currentSpeedKmh ?: 0.0),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Filled.Timer, contentDescription = null, tint = atlasColors.ink2) },
                labelRes = R.string.cardio_metric_avg_speed,
                value = stringResource(R.string.cardio_metric_speed_value, state.averageSpeedKmh ?: 0.0),
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = atlasColors.ink2) },
                labelRes = R.string.cardio_metric_points,
                value = state.route.size.toString(),
            )
        }
    }
}

@Composable
private fun MetricCard(
    labelRes: Int,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = modifier.fillMaxWidth().heightIn(min = 104.dp)) {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            icon()
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = atlasColors.ink3,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = atlasColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ManualDistanceCard(
    distance: String,
    speed: String,
    onDistanceChange: (String) -> Unit,
    onSpeedChange: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.cardio_manual_distance_title),
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(R.string.cardio_manual_distance_body),
                style = MaterialTheme.typography.bodySmall,
                color = atlasColors.ink2,
            )
            AtlasTextField(
                modifier = Modifier.fillMaxWidth(),
                value = distance,
                onValueChange = onDistanceChange,
                label = stringResource(R.string.cardio_manual_distance_label),
                keyboardType = KeyboardType.Decimal,
            )
            AtlasTextField(
                modifier = Modifier.fillMaxWidth(),
                value = speed,
                onValueChange = onSpeedChange,
                label = stringResource(R.string.cardio_manual_speed_label),
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}

@Composable
private fun ActiveCardioMessageText(message: ActiveCardioMessage?) {
    if (message == null) return
    val atlasColors = LocalAtlasColors.current
    val res = when (message) {
        ActiveCardioMessage.SessionMissing -> R.string.cardio_active_missing_session
        ActiveCardioMessage.LocationPermissionDenied -> R.string.cardio_location_permission_denied
        ActiveCardioMessage.TrackerUnavailable -> R.string.cardio_tracker_unavailable
        ActiveCardioMessage.ManualMetricsRequired -> R.string.cardio_manual_metrics_required
    }
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodyMedium,
        color = atlasColors.risk,
    )
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
