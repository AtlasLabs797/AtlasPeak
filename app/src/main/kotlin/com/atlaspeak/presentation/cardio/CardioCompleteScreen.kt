package com.atlaspeak.presentation.cardio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.component.formatDurationSeconds
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

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
    val atlasColors = LocalAtlasColors.current
    val session = state.session
    PremiumBackground(modifier = Modifier.fillMaxSize()) {
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
                color = atlasColors.ink,
            )
            when {
                state.isLoading -> CircularProgressIndicator()
                session == null -> Text(
                    text = stringResource(R.string.state_empty_title),
                    color = atlasColors.ink2,
                )
                else -> CardioSummary(session)
            }
            AtlasPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDone,
                text = stringResource(R.string.action_continue),
            )
        }
    }
}

@Composable
private fun CardioSummary(session: CardioSession) {
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
                text = session.cardioTypeName,
                style = MaterialTheme.typography.titleLarge,
                color = atlasColors.ink,
            )
            SummaryTile(
                text = stringResource(
                    R.string.cardio_complete_duration,
                    formatDurationSeconds((session.durationSeconds ?: 0).toLong()),
                ),
            )
            SummaryTile(text = stringResource(R.string.cardio_complete_distance, session.distanceKm ?: 0.0))
            SummaryTile(text = stringResource(R.string.cardio_complete_avg_speed, session.avgSpeedKmh ?: 0.0))
            SummaryTile(text = stringResource(R.string.cardio_complete_max_speed, session.maxSpeedKmh ?: 0.0))
            SummaryTile(text = stringResource(R.string.cardio_complete_calories, session.caloriesBurned ?: 0))
            if (session.route.isNotEmpty()) {
                CardioRouteMap(route = session.route)
            } else {
                Text(
                    text = stringResource(R.string.cardio_route_not_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = atlasColors.ink2,
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(text: String) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = atlasColors.fillSoft,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, atlasColors.line1),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.labelLarge,
            color = atlasColors.ink,
        )
    }
}
