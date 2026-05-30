package com.atlaspeak.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.AtlasBrushes
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
    val colors = MaterialTheme.colorScheme
    val session = state.session
    PremiumBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(AtlasBrushes.heroGradient(colors))
                    .padding(spacing.lg),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.workout_complete_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onPrimary,
                    )
                }
            }
            when {
                state.isLoading -> CircularProgressIndicator()
                session == null -> Text(stringResource(R.string.state_empty_title))
                else -> PremiumCard(modifier = Modifier.fillMaxWidth()) {
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
            Spacer(Modifier.height(spacing.xs))
            AtlasPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDone,
                text = stringResource(R.string.action_continue),
            )
        }
    }
}
