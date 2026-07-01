package com.atlaspeak.presentation.workout

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.core.time.ElapsedClock
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.presentation.component.AtlasBottomSheet
import com.atlaspeak.presentation.component.AtlasDialog
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSecondaryButton
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ActiveWorkoutRoute(
    onWorkoutCompleted: (String) -> Unit,
    onWorkoutDiscarded: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(state.discarded) {
        if (state.discarded) onWorkoutDiscarded()
    }

    val deletedSetMessage = stringResource(R.string.workout_set_deleted)
    val undoAction = stringResource(R.string.action_undo)
    LaunchedEffect(state.deletedSetEventId) {
        if (state.deletedSetEventId == 0L || state.deletedSetForUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedSetMessage,
            actionLabel = undoAction,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoRemoveSet()
        } else {
            viewModel.clearDeletedSetNotice()
        }
    }

    // Back del sistema durante una sesión activa: sin confirmación se abandonaba la sesión
    // dejando el servicio en primer plano notificando para siempre y una sesión huérfana.
    BackHandler(enabled = state.session != null && state.completedSessionId == null && !state.discarded) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AtlasDialog(
            onDismissRequest = { showExitDialog = false },
            title = stringResource(R.string.workout_exit_dialog_title),
            message = stringResource(R.string.workout_exit_dialog_body),
            confirmButton = {
                AtlasPrimaryButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.discardWorkout()
                    },
                    text = stringResource(R.string.workout_exit_dialog_discard),
                )
            },
            dismissButton = {
                AtlasSecondaryButton(
                    onClick = { showExitDialog = false },
                    text = stringResource(R.string.workout_exit_dialog_keep),
                )
            },
        )
    }

    if (state.pendingSetDeletion != null) {
        AtlasDialog(
            onDismissRequest = viewModel::cancelRemoveSet,
            title = stringResource(R.string.workout_delete_set_confirm_title),
            message = stringResource(R.string.workout_delete_set_confirm_body),
            confirmButton = {
                AtlasPrimaryButton(
                    onClick = viewModel::confirmRemoveSet,
                    text = stringResource(R.string.workout_delete_set_confirm),
                )
            },
            dismissButton = {
                AtlasSecondaryButton(
                    onClick = viewModel::cancelRemoveSet,
                    text = stringResource(R.string.action_cancel),
                )
            },
        )
    }

ActiveWorkoutScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        availableExercises = state.availableExercises,
        onSetCompleted = viewModel::onSetCompleted,
        onRepsTextChanged = viewModel::onRepsTextChanged,
        onWeightTextChanged = viewModel::onWeightTextChanged,
        onSetInputCommitted = viewModel::onSetInputCommitted,
        onAddSet = viewModel::addSet,
        onRemoveSet = viewModel::requestRemoveSet,
        onMoveExercise = viewModel::moveExercise,
        onAddExercise = viewModel::addExerciseDuringWorkout,
        onSkipRest = viewModel::skipRestTimer,
        onCompleteWorkout = viewModel::completeWorkout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    snackbarHostState: SnackbarHostState,
    availableExercises: List<com.atlaspeak.domain.model.workout.Exercise>,
    onSetCompleted: (WorkoutSet, Boolean, Int) -> Unit,
    onRepsTextChanged: (WorkoutSet, String) -> Unit,
    onWeightTextChanged: (WorkoutSet, String) -> Unit,
    onSetInputCommitted: (WorkoutSet) -> Unit,
    onAddSet: (ActiveWorkoutExercise) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
    onMoveExercise: (Int, Int) -> Unit,
    onAddExercise: (String) -> Unit,
    onSkipRest: () -> Unit,
    onCompleteWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val session = state.session
    var showExerciseSheet by rememberSaveable { mutableStateOf(false) }
    RestFeedbackEffect(state.restTimer, state.restFeedbackSettings)

    PremiumBackground(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.workout_active_missing_routine))
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { session.exercises.size.coerceAtLeast(1) })
                val coroutineScope = rememberCoroutineScope()
                val completionKey = session.exercises.joinToString(separator = "|") { exercise ->
                    "${exercise.exerciseId}:${exercise.sets.count { it.completed }}/${exercise.sets.size}"
                }
                val currentExercise = session.exercises.getOrNull(
                    pagerState.currentPage.coerceAtMost(session.exercises.lastIndex),
                )
                val currentExerciseComplete = currentExercise?.allSetsCompleted() == true
                val canMoveNext = currentExerciseComplete && pagerState.currentPage < session.exercises.lastIndex
                val allExercisesComplete = session.exercises.isNotEmpty() && session.exercises.all { it.allSetsCompleted() }

                LaunchedEffect(completionKey, state.restTimer == null) {
                    if (state.restTimer != null) return@LaunchedEffect
                    val currentPage = pagerState.currentPage.coerceAtMost(session.exercises.lastIndex)
                    val exercise = session.exercises.getOrNull(currentPage) ?: return@LaunchedEffect
                    if (exercise.allSetsCompleted() && currentPage < session.exercises.lastIndex) {
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }

                Box(Modifier.fillMaxSize()) {
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
                            totalVolumeKg = session.totalVolumeKg ?: 0.0,
                        )
                        ActiveWorkoutMessageText(state.message)
                        if (session.exercises.isNotEmpty()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f),
                            ) { page ->
                                ExercisePage(
                                    exercise = session.exercises[page],
                                    inputDrafts = state.inputDrafts,
                                    onSetCompleted = onSetCompleted,
                                    onRepsTextChanged = onRepsTextChanged,
                                    onWeightTextChanged = onWeightTextChanged,
                                    onSetInputCommitted = onSetInputCommitted,
                                    onAddSet = onAddSet,
                                    onRemoveSet = onRemoveSet,
                                )
                            }
                        }
                        state.restTimer?.let { timer ->
                            RestTimerPanel(
                                timer = timer,
                                currentExercise = currentExercise,
                                onSkipRest = onSkipRest,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            AtlasSecondaryButton(
                                modifier = Modifier.weight(1f),
                                onClick = { showExerciseSheet = true },
                                text = stringResource(R.string.workout_active_exercise_sheet),
                                leadingIcon = Icons.AutoMirrored.Filled.List,
                            )
                            AtlasPrimaryButton(
                                modifier = Modifier.weight(1f),
                                enabled = canMoveNext || allExercisesComplete,
                                onClick = {
                                    if (canMoveNext) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    } else {
                                        onCompleteWorkout()
                                    }
                                },
                                text = stringResource(
                                    if (canMoveNext) {
                                        R.string.workout_next_exercise_action
                                    } else if (allExercisesComplete) {
                                        R.string.workout_finish_action
                                    } else {
                                        R.string.workout_complete_sets_action
                                    },
                                ),
                            )
                        }
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(spacing.screen),
                    )
                }
if (showExerciseSheet) {
                    AtlasBottomSheet(onDismissRequest = { showExerciseSheet = false }) {
                        ExerciseSheet(
                            exercises = session.exercises,
                            availableExercises = availableExercises,
                            onMoveExercise = onMoveExercise,
                            onAddExercise = { exerciseId ->
                                onAddExercise(exerciseId)
                                showExerciseSheet = false
                            },
                            onDismiss = { showExerciseSheet = false },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ActiveWorkoutMessageText(message: ActiveWorkoutMessage?) {
    if (message == null) return
    val atlasColors = LocalAtlasColors.current
    val res = when (message) {
        ActiveWorkoutMessage.RoutineMissing -> R.string.workout_active_missing_routine
        ActiveWorkoutMessage.TimerServiceUnavailable -> R.string.workout_timer_service_unavailable
    }
    // (#22 del informe) RoutineMissing sí es un error real (rojo); TimerService
    // unavailable solo significa "el cronómetro en background no pudo arrancar"
    // (aviso), así que usamos warn (ámbar) para no generar ansiedad innecesaria.
    val tint = when (message) {
        ActiveWorkoutMessage.RoutineMissing -> atlasColors.risk
        ActiveWorkoutMessage.TimerServiceUnavailable -> atlasColors.warn
    }
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodyMedium,
        color = tint,
    )
}

@Composable
private fun ProgressCard(
    routineName: String,
    completedExercises: Int,
    totalExercises: Int,
    elapsedSeconds: Long,
    totalVolumeKg: Double,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val progress = if (totalExercises <= 0) 0f else completedExercises / totalExercises.toFloat()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.workout_live_overline),
                style = MaterialTheme.typography.labelSmall,
                color = atlasColors.ink3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = routineName.ifBlank { stringResource(R.string.screen_train_title) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = atlasColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = atlasColors.surface2,
                    contentColor = atlasColors.ink,
                    border = BorderStroke(1.dp, atlasColors.line2),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(atlasColors.ink2),
                        )
                        Text(
                            text = ElapsedClock.format(elapsedSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = atlasColors.ink,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // (#6 del informe) Si ya están todos completos no sumamos +1 (si no
            // mostraría "5+1 / 5"). En otro caso, numeramos el ejercicio actual
            // 1-indexed para humanos.
            val currentExerciseNumber = if (totalExercises > 0 && completedExercises >= totalExercises) {
                totalExercises
            } else {
                completedExercises + 1
            }
            Text(
                text = stringResource(R.string.workout_active_exercise_progress, currentExerciseNumber, totalExercises),
                style = MaterialTheme.typography.labelSmall,
                color = atlasColors.ink3,
            )
            Text(
                text = stringResource(R.string.workout_active_volume_value, totalVolumeKg),
                style = MaterialTheme.typography.labelSmall,
                color = atlasColors.ink3,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 2.dp)
                .background(atlasColors.line2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .heightIn(min = 2.dp)
                    .background(atlasColors.ink),
            )
        }
    }
}

@Composable
private fun ExercisePage(
    exercise: ActiveWorkoutExercise,
    inputDrafts: Map<String, WorkoutSetInputDraft>,
    onSetCompleted: (WorkoutSet, Boolean, Int) -> Unit,
    onRepsTextChanged: (WorkoutSet, String) -> Unit,
    onWeightTextChanged: (WorkoutSet, String) -> Unit,
    onSetInputCommitted: (WorkoutSet) -> Unit,
    onAddSet: (ActiveWorkoutExercise) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val activeSetId = exercise.sets.firstOrNull { !it.completed }?.id
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.cardGap)) {
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.exerciseName,
                                style = MaterialTheme.typography.titleLarge,
                                color = atlasColors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.workout_active_objective,
                                    exercise.sets.size,
                                    exercise.sets.firstOrNull()?.plannedReps ?: 0,
                                    exercise.restSeconds,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = atlasColors.ink3,
                            )
                            exercise.notes?.let { notes ->
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = atlasColors.ink3,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = null,
                            tint = atlasColors.ink3,
                        )
                    }
                    SetTableHeader()
                    exercise.sets.forEach { set ->
                        SetRow(
                            set = set,
                            draft = inputDrafts[set.id] ?: set.toInputDraft(),
                            restSeconds = exercise.restSeconds,
                            isActive = set.id == activeSetId,
                            onSetCompleted = onSetCompleted,
                            onRepsTextChanged = onRepsTextChanged,
                            onWeightTextChanged = onWeightTextChanged,
                            onSetInputCommitted = onSetInputCommitted,
                            onRemoveSet = onRemoveSet,
                        )
                    }
                    AtlasSecondaryButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                        onClick = { onAddSet(exercise) },
                        text = stringResource(R.string.workout_add_set),
                        leadingIcon = Icons.Filled.Add,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetTableHeader() {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(40.dp),
            text = stringResource(R.string.workout_table_set),
            style = MaterialTheme.typography.labelSmall,
            color = atlasColors.ink3,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.workout_table_kg),
            style = MaterialTheme.typography.labelSmall,
            color = atlasColors.ink3,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.workout_table_reps),
            style = MaterialTheme.typography.labelSmall,
            color = atlasColors.ink3,
        )
        Text(
            modifier = Modifier.width(82.dp),
            text = stringResource(R.string.workout_table_done),
            style = MaterialTheme.typography.labelSmall,
            color = atlasColors.ink3,
        )
    }
}

@Composable
private fun SetRow(
    set: WorkoutSet,
    draft: WorkoutSetInputDraft,
    restSeconds: Int,
    isActive: Boolean,
    onSetCompleted: (WorkoutSet, Boolean, Int) -> Unit,
    onRepsTextChanged: (WorkoutSet, String) -> Unit,
    onWeightTextChanged: (WorkoutSet, String) -> Unit,
    onSetInputCommitted: (WorkoutSet) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val haptics = LocalHapticFeedback.current
    val rowColor = when {
        isActive -> atlasColors.fillActive
        set.completed -> atlasColors.fillSoft
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .heightIn(min = 54.dp)
            .padding(horizontal = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 38.dp)
                .background(if (isActive) atlasColors.ink else Color.Transparent),
        )
        Text(
            modifier = Modifier.width(28.dp),
            text = set.setNumber.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = atlasColors.ink,
        )
        TelemetryInput(
            modifier = Modifier.weight(1f),
            value = draft.weightText,
            onValueChange = { onWeightTextChanged(set, it) },
            placeholder = set.weightKg?.toString().orEmpty(),
            keyboardType = KeyboardType.Decimal,
            contentDescription = stringResource(R.string.workout_weight_series_cd, set.setNumber),
            onCommit = { onSetInputCommitted(set) },
        )
        TelemetryInput(
            modifier = Modifier.weight(1f),
            value = draft.repsText,
            onValueChange = { onRepsTextChanged(set, it) },
            placeholder = set.plannedReps.toString(),
            keyboardType = KeyboardType.Number,
            contentDescription = stringResource(R.string.workout_reps_series_cd, set.setNumber),
            onCommit = { onSetInputCommitted(set) },
        )
        val completionContentDescription = stringResource(
            if (set.completed) R.string.workout_set_completed_cd else R.string.workout_set_incomplete_cd,
            set.setNumber,
        )
        Surface(
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    contentDescription = completionContentDescription
                },
            onClick = {
                val completed = !set.completed
                if (completed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSetCompleted(set, completed, restSeconds)
            },
            shape = MaterialTheme.shapes.medium,
            color = if (set.completed) atlasColors.ink else Color.Transparent,
            contentColor = if (set.completed) atlasColors.onAccent else atlasColors.ink2,
            border = BorderStroke(1.dp, if (set.completed) atlasColors.ink else atlasColors.lineStrong),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (set.completed) {
                    Text(
                        text = stringResource(R.string.symbol_check),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        IconButton(onClick = { onRemoveSet(set) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.workout_remove_set_cd),
                tint = atlasColors.ink3,
            )
        }
    }
}

@Composable
private fun TelemetryInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    contentDescription: String,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atlasColors = LocalAtlasColors.current
    BasicTextField(
        modifier = modifier
            .commitOnFocusLost(onCommit)
            .semantics { this.contentDescription = contentDescription }
            .heightIn(min = 44.dp),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = atlasColors.ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(atlasColors.ink),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank() && placeholder.isNotBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = atlasColors.ink4,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun RestTimerPanel(
    timer: RestTimerUiState,
    currentExercise: ActiveWorkoutExercise?,
    onSkipRest: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val upcomingSet = currentExercise?.sets?.firstOrNull { !it.completed }
    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RestCountdownRing(timer)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.workout_rest_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = atlasColors.ink3,
                )
                Text(
                    text = if (upcomingSet != null) {
                        stringResource(
                            R.string.workout_rest_next_set,
                            upcomingSet.setNumber,
                            upcomingSet.weightKg ?: 0.0,
                        )
                    } else {
                        currentExercise?.exerciseName.orEmpty()
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = atlasColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AtlasSecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSkipRest,
                    text = stringResource(R.string.action_skip),
                )
            }
        }
    }
}

@Composable
private fun RestCountdownRing(timer: RestTimerUiState) {
    val atlasColors = LocalAtlasColors.current
    val restDescription = stringResource(R.string.workout_rest_progress_cd, timer.remainingSeconds)
    val animatedProgress by animateFloatAsState(
        targetValue = timer.progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "",
    )
    Box(
        modifier = Modifier
            .size(86.dp)
            .semantics {
                contentDescription = restDescription
                progressBarRangeInfo = ProgressBarRangeInfo(timer.progress, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 7.dp,
            color = atlasColors.ringTrack,
            trackColor = atlasColors.ringTrack,
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 7.dp,
            color = atlasColors.ink,
            trackColor = Color.Transparent,
        )
        Text(
            text = ElapsedClock.format(timer.remainingSeconds.toLong()),
            style = MaterialTheme.typography.titleLarge,
            color = atlasColors.ink,
        )
    }
}

@Composable
private fun ExerciseSheet(
    exercises: List<ActiveWorkoutExercise>,
    availableExercises: List<com.atlaspeak.domain.model.workout.Exercise>,
    onMoveExercise: (Int, Int) -> Unit,
    onAddExercise: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var showAddDialog by remember { mutableStateOf(false) }
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
        AtlasSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showAddDialog = true },
            text = stringResource(R.string.workout_add_exercise),
            leadingIcon = Icons.Filled.Add,
        )
    }
    if (showAddDialog) {
        AddExerciseDialog(
            availableExercises = availableExercises,
            currentExerciseIds = exercises.map { it.exerciseId }.toSet(),
            onDismiss = { showAddDialog = false },
            onPick = { exerciseId ->
                onAddExercise(exerciseId)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddExerciseDialog(
    availableExercises: List<com.atlaspeak.domain.model.workout.Exercise>,
    currentExerciseIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val filtered = availableExercises
        .filter { !it.isArchived }
        .sortedBy { it.muscleGroup.name }
    AtlasDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.workout_add_exercise_dialog_title),
        confirmButton = {
            AtlasSecondaryButton(
                onClick = onDismiss,
                text = stringResource(R.string.action_cancel),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_add_exercise_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = atlasColors.ink3,
                )
            } else {
                filtered.forEach { exercise ->
                    val alreadyAdded = exercise.id in currentExerciseIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (alreadyAdded) atlasColors.fillSoft else atlasColors.surface2)
                            .clickable(enabled = !alreadyAdded) { onPick(exercise.id) }
                            .padding(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (alreadyAdded) atlasColors.ink3 else atlasColors.ink,
                            )
                            Text(
                                text = exercise.muscleGroup.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = atlasColors.ink3,
                            )
                        }
                        if (alreadyAdded) {
                            Text(
                                text = stringResource(R.string.workout_add_exercise_already_added),
                                style = MaterialTheme.typography.labelSmall,
                                color = atlasColors.ink3,
                            )
                        }
                    }
                }
            }
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
    val atlasColors = LocalAtlasColors.current
    var dragOffset by remember(exercise.exerciseId) { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(exercise.exerciseId, first, last) {
                // Umbral en dp convertido a px: un literal en px era demasiado
                // sensible en pantallas de alta densidad.
                val thresholdPx = 56.dp.toPx()
                detectVerticalDragGestures(
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = { dragOffset = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                    when {
                        dragOffset <= -thresholdPx && !first -> {
                            onMoveExercise(index, -1)
                            dragOffset = 0f
                        }
                        dragOffset >= thresholdPx && !last -> {
                            onMoveExercise(index, 1)
                            dragOffset = 0f
                        }
                    }
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = atlasColors.fillSoft,
        contentColor = atlasColors.ink,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, atlasColors.line2),
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = null,
                tint = atlasColors.ink3,
            )
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.workout_ordered_exercise, index + 1, exercise.exerciseName),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(enabled = !first, onClick = { onMoveExercise(index, -1) }) {
                Icon(
                    Icons.Filled.ExpandLess,
                    contentDescription = stringResource(R.string.workout_move_exercise_up_cd),
                    tint = if (first) atlasColors.ink4 else atlasColors.ink2,
                )
            }
            IconButton(enabled = !last, onClick = { onMoveExercise(index, 1) }) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.workout_move_exercise_down_cd),
                    tint = if (last) atlasColors.ink4 else atlasColors.ink2,
                )
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
    // (#7 del informe) ToneGenerator es caro (allocate + inicializa audio HAL). Lo
    // creamos una sola vez por pantalla de workout y lo liberamos al salir.
    val toneGenerator = remember {
        if (settings.soundEnabled) ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60) else null
    }
    DisposableEffect(toneGenerator) {
        onDispose { toneGenerator?.release() }
    }
    LaunchedEffect(restTimer?.id, restTimer?.remainingSeconds) {
        if (restTimer == null) return@LaunchedEffect
        if (restTimer.remainingSeconds != 0) return@LaunchedEffect
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
        if (settings.soundEnabled && toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (_: RuntimeException) {
                // ToneGenerator puede soltar RuntimeException si el HAL no responde.
                // Lo silenciamos: la vibración ya cubrió la señal háptica.
            }
        }
    }
}

private fun ActiveWorkoutExercise.allSetsCompleted(): Boolean {
    return sets.isNotEmpty() && sets.all { it.completed }
}

private fun WorkoutSet.toInputDraft(): WorkoutSetInputDraft {
    return WorkoutSetInputDraft(
        weightText = weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
        repsText = actualReps?.toString().orEmpty(),
    )
}

/**
 * Confirma el borrador solo al PERDER el foco: onFocusChanged también se emite al montar el
 * campo (sin foco), y confirmar ahí pisaría con el modelo lo que el usuario está tecleando.
 */
private fun Modifier.commitOnFocusLost(onCommit: () -> Unit): Modifier = composed {
    var hadFocus by remember { mutableStateOf(false) }
    onFocusChanged { focusState ->
        if (hadFocus && !focusState.isFocused) onCommit()
        hadFocus = focusState.isFocused
    }
}
