package com.atlaspeak.presentation.workout

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.presentation.theme.LocalSpacing
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutRoute(
    onWorkoutCompleted: (String) -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startTimerServiceIfPermitted()
        } else {
            viewModel.onTimerPermissionDenied()
        }
    }

    LaunchedEffect(state.session?.id, state.timerServiceStartHandled) {
        if (state.session == null || state.timerServiceStartHandled) return@LaunchedEffect
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            viewModel.startTimerServiceIfPermitted()
        }
    }

    LaunchedEffect(state.completedSessionId) {
        state.completedSessionId?.let(onWorkoutCompleted)
    }

    ActiveWorkoutScreen(
        state = state,
        onSetCompleted = viewModel::onSetCompleted,
        onActualRepsChanged = viewModel::onActualRepsChanged,
        onWeightChanged = viewModel::onWeightChanged,
        onAddSet = viewModel::addSet,
        onRemoveSet = viewModel::removeSet,
        onMoveExercise = viewModel::moveExercise,
        onSkipRest = viewModel::skipRestTimer,
        onCompleteWorkout = viewModel::completeWorkout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    onSetCompleted: (WorkoutSet, Boolean, Int) -> Unit,
    onActualRepsChanged: (WorkoutSet, String) -> Unit,
    onWeightChanged: (WorkoutSet, String) -> Unit,
    onAddSet: (ActiveWorkoutExercise) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
    onMoveExercise: (Int, Int) -> Unit,
    onSkipRest: () -> Unit,
    onCompleteWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val session = state.session
    var showExerciseSheet by remember { mutableStateOf(false) }
    RestFeedbackEffect(state.restTimer, state.restFeedbackSettings)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.workout_active_missing_routine))
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { session.exercises.size.coerceAtLeast(1) })
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
                ) {
                    ProgressCard(
                        routineName = session.routineName.orEmpty(),
                        completedExercises = state.completedExerciseCount,
                        totalExercises = state.totalExerciseCount,
                        elapsedSeconds = state.elapsedSeconds,
                    )
                    ActiveWorkoutMessageText(state.message)
                    if (session.exercises.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            ExercisePage(
                                exercise = session.exercises[page],
                                onSetCompleted = onSetCompleted,
                                onActualRepsChanged = onActualRepsChanged,
                                onWeightChanged = onWeightChanged,
                                onAddSet = onAddSet,
                                onRemoveSet = onRemoveSet,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = spacing.minTouchTarget),
                            onClick = { showExerciseSheet = true },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            Text(
                                modifier = Modifier.padding(start = spacing.xs),
                                text = stringResource(R.string.workout_active_exercise_sheet),
                            )
                        }
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = spacing.minTouchTarget),
                            onClick = onCompleteWorkout,
                        ) {
                            Text(stringResource(R.string.workout_finish_action))
                        }
                    }
                }
                state.restTimer?.let { timer ->
                    RestTimerOverlay(timer = timer, onSkipRest = onSkipRest)
                }
                if (showExerciseSheet) {
                    ModalBottomSheet(onDismissRequest = { showExerciseSheet = false }) {
                        ExerciseSheet(
                            exercises = session.exercises,
                            onMoveExercise = onMoveExercise,
                            onDismiss = { showExerciseSheet = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutMessageText(message: ActiveWorkoutMessage?) {
    if (message == null) return
    val res = when (message) {
        ActiveWorkoutMessage.RoutineMissing -> R.string.workout_active_missing_routine
        ActiveWorkoutMessage.TimerServiceUnavailable -> R.string.workout_timer_service_unavailable
    }
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ProgressCard(
    routineName: String,
    completedExercises: Int,
    totalExercises: Int,
    elapsedSeconds: Long,
) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = routineName.ifBlank { stringResource(R.string.screen_train_title) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.workout_active_progress, completedExercises, totalExercises),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = formatElapsed(elapsedSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ExercisePage(
    exercise: ActiveWorkoutExercise,
    onSetCompleted: (WorkoutSet, Boolean, Int) -> Unit,
    onActualRepsChanged: (WorkoutSet, String) -> Unit,
    onWeightChanged: (WorkoutSet, String) -> Unit,
    onAddSet: (ActiveWorkoutExercise) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.cardGap)) {
        item {
            Text(
                text = exercise.exerciseName,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        itemsIndexed(exercise.sets, key = { _, set -> set.id }) { _, set ->
            SetRow(
                set = set,
                restSeconds = exercise.restSeconds,
                onSetCompleted = onSetCompleted,
                onActualRepsChanged = onActualRepsChanged,
                onWeightChanged = onWeightChanged,
                onRemoveSet = onRemoveSet,
            )
        }
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                onClick = { onAddSet(exercise) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = spacing.xs),
                    text = stringResource(R.string.workout_add_set),
                )
            }
        }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSet,
    restSeconds: Int,
    onSetCompleted: (WorkoutSet, Boolean, Int) -> Unit,
    onActualRepsChanged: (WorkoutSet, String) -> Unit,
    onWeightChanged: (WorkoutSet, String) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Checkbox(
                    checked = set.completed,
                    onCheckedChange = { checked -> onSetCompleted(set, checked, restSeconds) },
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.workout_set_number, set.setNumber),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { onRemoveSet(set) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workout_remove_set_cd))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = (set.actualReps ?: set.plannedReps).toString(),
                    onValueChange = { onActualRepsChanged(set, it) },
                    label = { Text(stringResource(R.string.workout_reps_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = set.weightKg?.toString().orEmpty(),
                    onValueChange = { onWeightChanged(set, it) },
                    label = { Text(stringResource(R.string.workout_weight_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
}

@Composable
private fun RestTimerOverlay(
    timer: RestTimerUiState,
    onSkipRest: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card {
                Column(
                    modifier = Modifier.padding(spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    val restDescription = stringResource(R.string.workout_rest_progress_cd, timer.remainingSeconds)
                    Box(
                        modifier = Modifier.semantics {
                            contentDescription = restDescription
                            progressBarRangeInfo = ProgressBarRangeInfo(timer.progress, 0f..1f)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { timer.progress },
                            modifier = Modifier.size(156.dp),
                            strokeWidth = 6.dp,
                        )
                        Text(
                            text = timer.remainingSeconds.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                    Text(
                        text = stringResource(R.string.workout_rest_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(
                        modifier = Modifier.heightIn(min = spacing.minTouchTarget),
                        onClick = onSkipRest,
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSheet(
    exercises: List<ActiveWorkoutExercise>,
    onMoveExercise: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.workout_active_exercise_sheet),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        exercises.forEachIndexed { index, exercise ->
            ExerciseSheetRow(
                index = index,
                exercise = exercise,
                first = index == 0,
                last = index == exercises.lastIndex,
                onMoveExercise = onMoveExercise,
            )
        }
    }
}

@Composable
private fun ExerciseSheetRow(
    index: Int,
    exercise: ActiveWorkoutExercise,
    first: Boolean,
    last: Boolean,
    onMoveExercise: (Int, Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    var dragOffset by remember(exercise.exerciseId) { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(exercise.exerciseId, first, last) {
                detectVerticalDragGestures(
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = { dragOffset = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                    when {
                        dragOffset <= -56f && !first -> {
                            onMoveExercise(index, -1)
                            dragOffset = 0f
                        }
                        dragOffset >= 56f && !last -> {
                            onMoveExercise(index, 1)
                            dragOffset = 0f
                        }
                    }
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(Icons.Filled.DragHandle, contentDescription = null)
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.workout_ordered_exercise, index + 1, exercise.exerciseName),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(enabled = !first, onClick = { onMoveExercise(index, -1) }) {
                Icon(Icons.Filled.ExpandLess, contentDescription = stringResource(R.string.workout_move_exercise_up_cd))
            }
            IconButton(enabled = !last, onClick = { onMoveExercise(index, 1) }) {
                Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.workout_move_exercise_down_cd))
            }
        }
    }
}

@Composable
private fun RestFeedbackEffect(
    restTimer: RestTimerUiState?,
    settings: RestTimerFeedbackSettings,
) {
    val context = LocalContext.current
    LaunchedEffect(restTimer?.id) {
        if (restTimer == null) return@LaunchedEffect
        if (settings.vibrationEnabled) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120)
            }
        }
        if (settings.soundEnabled) {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                delay(140)
            } finally {
                toneGenerator.release()
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
