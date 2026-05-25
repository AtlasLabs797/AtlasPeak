package com.atlaspeak.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun WorkoutCompleteRoute(
    onDone: () -> Unit,
    viewModel: WorkoutCompleteViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    WorkoutCompleteScreen(state = state, onDone = onDone)
}

@Composable
fun WorkoutCompleteScreen(
    state: WorkoutCompleteUiState,
    onDone: () -> Unit,
) {
    val spacing = LocalSpacing.current
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
                text = stringResource(R.string.workout_complete_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when {
                state.isLoading -> CircularProgressIndicator()
                session == null -> Text(stringResource(R.string.state_empty_title))
                else -> PremiumCard {
                    Column(
                        modifier = Modifier.padding(spacing.card),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Text(
                            text = session.routineName.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(stringResource(R.string.workout_complete_duration, session.durationSeconds ?: 0))
                        Text(stringResource(R.string.workout_complete_volume, session.totalVolumeKg ?: 0.0))
                        Text(
                            stringResource(
                                R.string.workout_complete_sets,
                                session.exercises.flatMap { it.sets }.count { it.completed },
                            ),
                        )
                        Text(
                            stringResource(
                                R.string.workout_complete_prs,
                                session.exercises.flatMap { it.sets }.count { it.isPersonalRecord },
                            ),
                        )
                    }
                }
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
