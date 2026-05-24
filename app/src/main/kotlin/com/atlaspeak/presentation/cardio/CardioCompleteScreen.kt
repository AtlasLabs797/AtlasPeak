package com.atlaspeak.presentation.cardio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.presentation.theme.LocalSpacing
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun CardioCompleteRoute(
    onDone: () -> Unit,
    viewModel: CardioCompleteViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    CardioCompleteScreen(state = state, onDone = onDone)
}

@Composable
fun CardioCompleteScreen(
    state: CardioCompleteUiState,
    onDone: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val session = state.session
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.cardio_complete_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when {
                state.isLoading -> CircularProgressIndicator()
                session == null -> Text(stringResource(R.string.state_empty_title))
                else -> CardioSummary(session)
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                onClick = onDone,
            ) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Composable
private fun CardioSummary(session: CardioSession) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = session.cardioTypeName,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.cardio_complete_duration, session.durationSeconds ?: 0))
            Text(stringResource(R.string.cardio_complete_distance, session.distanceKm ?: 0.0))
            Text(stringResource(R.string.cardio_complete_avg_speed, session.avgSpeedKmh ?: 0.0))
            Text(stringResource(R.string.cardio_complete_max_speed, session.maxSpeedKmh ?: 0.0))
            Text(stringResource(R.string.cardio_complete_calories, session.caloriesBurned ?: 0))
            if (session.route.isNotEmpty()) {
                RouteMap(route = session.route)
            } else {
                Text(
                    text = stringResource(R.string.cardio_route_not_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RouteMap(route: List<LocationPoint>) {
    val points = route.map { LatLng(it.latitude, it.longitude) }
    if (points.isEmpty()) return
    val routeColor = MaterialTheme.colorScheme.primary
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 15f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
        ) {
            Polyline(
                points = points,
                color = routeColor,
                width = 8f,
            )
        }
    }
}
