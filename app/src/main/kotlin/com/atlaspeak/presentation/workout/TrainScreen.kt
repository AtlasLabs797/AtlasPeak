package com.atlaspeak.presentation.workout

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun TrainRoute(
    onStartRoutine: (String) -> Unit,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    TrainScreen(
        state = state,
        onTabSelected = viewModel::selectTab,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onMuscleGroupFilterChanged = viewModel::onMuscleGroupFilterChanged,
        onNewExerciseNameChanged = viewModel::onNewExerciseNameChanged,
        onNewExerciseGroupChanged = viewModel::onNewExerciseGroupChanged,
        onCreateCustomExercise = viewModel::createCustomExercise,
        onEditExercise = viewModel::startEditingExercise,
        onCancelExerciseEditing = viewModel::cancelExerciseEditing,
        onArchiveExercise = viewModel::archiveExercise,
        onRoutineNameChanged = viewModel::onRoutineNameChanged,
        onRoutineColorChanged = viewModel::onRoutineColorChanged,
        onAddExerciseToDraft = viewModel::addExerciseToDraft,
        onRemoveDraftItem = viewModel::removeDraftItem,
        onMoveDraftItem = viewModel::moveDraftItem,
        onDraftSetsChanged = viewModel::onDraftSetsChanged,
        onDraftRepsChanged = viewModel::onDraftRepsChanged,
        onDraftWeightChanged = viewModel::onDraftWeightChanged,
        onDraftRestChanged = viewModel::onDraftRestChanged,
        onCreateRoutine = viewModel::createRoutine,
        onEditRoutine = viewModel::startEditingRoutine,
        onCancelRoutineEditing = viewModel::cancelRoutineEditing,
        onStartRoutine = onStartRoutine,
        onSelectRoutine = viewModel::selectRoutine,
        onSelectWorkoutSession = viewModel::selectWorkoutSession,
        onArchiveRoutine = viewModel::archiveRoutine,
    )
}

@Composable
fun TrainScreen(
    state: TrainUiState,
    onTabSelected: (TrainTab) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onMuscleGroupFilterChanged: (Int?) -> Unit,
    onNewExerciseNameChanged: (String) -> Unit,
    onNewExerciseGroupChanged: (Int) -> Unit,
    onCreateCustomExercise: () -> Unit,
    onEditExercise: (Exercise) -> Unit,
    onCancelExerciseEditing: () -> Unit,
    onArchiveExercise: (String) -> Unit,
    onRoutineNameChanged: (String) -> Unit,
    onRoutineColorChanged: (String) -> Unit,
    onAddExerciseToDraft: (Exercise) -> Unit,
    onRemoveDraftItem: (Int) -> Unit,
    onMoveDraftItem: (Int, Int) -> Unit,
    onDraftSetsChanged: (Int, String) -> Unit,
    onDraftRepsChanged: (Int, String) -> Unit,
    onDraftWeightChanged: (Int, String) -> Unit,
    onDraftRestChanged: (Int, String) -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onCancelRoutineEditing: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onSelectRoutine: (String) -> Unit,
    onSelectWorkoutSession: (String) -> Unit,
    onArchiveRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.screen),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.screen_train_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                MessageText(state.message)
            }
            PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == TrainTab.Exercises,
                    onClick = { onTabSelected(TrainTab.Exercises) },
                    text = { Text(stringResource(R.string.workout_tab_exercises)) },
                    icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
                )
                Tab(
                    selected = state.selectedTab == TrainTab.Routines,
                    onClick = { onTabSelected(TrainTab.Routines) },
                    text = { Text(stringResource(R.string.workout_tab_routines)) },
                    icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                )
                Tab(
                    selected = state.selectedTab == TrainTab.History,
                    onClick = { onTabSelected(TrainTab.History) },
                    text = { Text(stringResource(R.string.workout_tab_history)) },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                )
            }
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.selectedTab) {
                    TrainTab.Exercises -> ExerciseLibraryContent(
                        state = state,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onMuscleGroupFilterChanged = onMuscleGroupFilterChanged,
                        onNewExerciseNameChanged = onNewExerciseNameChanged,
                        onNewExerciseGroupChanged = onNewExerciseGroupChanged,
                        onCreateCustomExercise = onCreateCustomExercise,
                        onEditExercise = onEditExercise,
                        onCancelExerciseEditing = onCancelExerciseEditing,
                        onArchiveExercise = onArchiveExercise,
                    )
                    TrainTab.Routines -> RoutineContent(
                        state = state,
                        onRoutineNameChanged = onRoutineNameChanged,
                        onRoutineColorChanged = onRoutineColorChanged,
                        onAddExerciseToDraft = onAddExerciseToDraft,
                        onRemoveDraftItem = onRemoveDraftItem,
                        onMoveDraftItem = onMoveDraftItem,
                        onDraftSetsChanged = onDraftSetsChanged,
                        onDraftRepsChanged = onDraftRepsChanged,
                        onDraftWeightChanged = onDraftWeightChanged,
                        onDraftRestChanged = onDraftRestChanged,
                        onCreateRoutine = onCreateRoutine,
                        onEditRoutine = onEditRoutine,
                        onCancelRoutineEditing = onCancelRoutineEditing,
                        onStartRoutine = onStartRoutine,
                        onSelectRoutine = onSelectRoutine,
                        onArchiveRoutine = onArchiveRoutine,
                    )
                    TrainTab.History -> WorkoutHistoryContent(
                        state = state,
                        onSelectWorkoutSession = onSelectWorkoutSession,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryContent(
    state: TrainUiState,
    onSelectWorkoutSession: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item { SectionTitle(R.string.workout_history_title) }
        if (state.workoutSessions.isEmpty()) {
            item { EmptyState(R.string.workout_history_empty) }
        } else {
            items(state.workoutSessions, key = { it.id }) { session ->
                WorkoutSessionCard(
                    session = session,
                    selected = session.id == state.selectedWorkoutSessionId,
                    onClick = { onSelectWorkoutSession(session.id) },
                )
            }
        }
        state.selectedWorkoutSession?.let { session ->
            item { WorkoutSessionDetailCard(session) }
        }
    }
}

@Composable
private fun ExerciseLibraryContent(
    state: TrainUiState,
    onSearchQueryChanged: (String) -> Unit,
    onMuscleGroupFilterChanged: (Int?) -> Unit,
    onNewExerciseNameChanged: (String) -> Unit,
    onNewExerciseGroupChanged: (Int) -> Unit,
    onCreateCustomExercise: () -> Unit,
    onEditExercise: (Exercise) -> Unit,
    onCancelExerciseEditing: () -> Unit,
    onArchiveExercise: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            SearchField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
            )
        }
        item {
            MuscleGroupChips(
                groups = state.muscleGroups,
                selectedGroupId = state.selectedMuscleGroupId,
                includeAll = true,
                onSelected = onMuscleGroupFilterChanged,
            )
        }
        item {
            CustomExerciseCard(
                state = state,
                onNameChanged = onNewExerciseNameChanged,
                onGroupChanged = onNewExerciseGroupChanged,
                onSave = onCreateCustomExercise,
                onCancel = onCancelExerciseEditing,
            )
        }
        if (state.exercises.isEmpty()) {
            item { EmptyState(R.string.workout_no_exercises) }
        } else {
            items(state.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    showArchive = !exercise.isPreset,
                    onEdit = { onEditExercise(exercise) },
                    onArchive = { onArchiveExercise(exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun RoutineContent(
    state: TrainUiState,
    onRoutineNameChanged: (String) -> Unit,
    onRoutineColorChanged: (String) -> Unit,
    onAddExerciseToDraft: (Exercise) -> Unit,
    onRemoveDraftItem: (Int) -> Unit,
    onMoveDraftItem: (Int, Int) -> Unit,
    onDraftSetsChanged: (Int, String) -> Unit,
    onDraftRepsChanged: (Int, String) -> Unit,
    onDraftWeightChanged: (Int, String) -> Unit,
    onDraftRestChanged: (Int, String) -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onCancelRoutineEditing: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onSelectRoutine: (String) -> Unit,
    onArchiveRoutine: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            RoutineBuilderCard(
                state = state,
                onRoutineNameChanged = onRoutineNameChanged,
                onRoutineColorChanged = onRoutineColorChanged,
                onAddExerciseToDraft = onAddExerciseToDraft,
                onRemoveDraftItem = onRemoveDraftItem,
                onMoveDraftItem = onMoveDraftItem,
                onDraftSetsChanged = onDraftSetsChanged,
                onDraftRepsChanged = onDraftRepsChanged,
                onDraftWeightChanged = onDraftWeightChanged,
                onDraftRestChanged = onDraftRestChanged,
                onCreateRoutine = onCreateRoutine,
                onCancelRoutineEditing = onCancelRoutineEditing,
            )
        }
        item {
            SectionTitle(R.string.workout_routine_list_title)
        }
        if (state.routines.isEmpty()) {
            item { EmptyState(R.string.workout_no_routines) }
        } else {
            items(state.routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    selected = routine.id == state.selectedRoutineId,
                    onClick = { onSelectRoutine(routine.id) },
                    onArchive = { onArchiveRoutine(routine.id) },
                )
            }
        }
        state.selectedRoutine?.let { routine ->
            item {
                RoutineDetailCard(
                    routine = routine,
                    onEdit = { onEditRoutine(routine) },
                    onStart = { onStartRoutine(routine.id) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.workout_search_exercises)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
}

@Composable
private fun CustomExerciseCard(
    state: TrainUiState,
    onNameChanged: (String) -> Unit,
    onGroupChanged: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ElevatedCard(colors = CardDefaults.elevatedCardColors()) {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SectionTitle(
                if (state.editingExerciseId == null) {
                    R.string.workout_custom_exercise_title
                } else {
                    R.string.workout_edit_exercise_title
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.newExerciseName,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.workout_exercise_name_label)) },
                singleLine = true,
            )
            MuscleGroupChips(
                groups = state.muscleGroups,
                selectedGroupId = state.newExerciseGroupId,
                includeAll = false,
                onSelected = { groupId -> if (groupId != null) onGroupChanged(groupId) },
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                onClick = onSave,
                enabled = state.newExerciseName.isNotBlank() && state.newExerciseGroupId != null,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = spacing.xs),
                    text = stringResource(R.string.workout_save_exercise),
                )
            }
            if (state.editingExerciseId != null) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget),
                    onClick = onCancel,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    showArchive: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = exercise.muscleGroup.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(if (exercise.isPreset) R.string.workout_exercise_preset else R.string.workout_exercise_custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showArchive) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.workout_edit_exercise_cd),
                    )
                }
                IconButton(onClick = onArchive) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = stringResource(R.string.workout_archive_exercise_cd),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineBuilderCard(
    state: TrainUiState,
    onRoutineNameChanged: (String) -> Unit,
    onRoutineColorChanged: (String) -> Unit,
    onAddExerciseToDraft: (Exercise) -> Unit,
    onRemoveDraftItem: (Int) -> Unit,
    onMoveDraftItem: (Int, Int) -> Unit,
    onDraftSetsChanged: (Int, String) -> Unit,
    onDraftRepsChanged: (Int, String) -> Unit,
    onDraftWeightChanged: (Int, String) -> Unit,
    onDraftRestChanged: (Int, String) -> Unit,
    onCreateRoutine: () -> Unit,
    onCancelRoutineEditing: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ElevatedCard {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SectionTitle(
                if (state.editingRoutineId == null) {
                    R.string.workout_routine_builder_title
                } else {
                    R.string.workout_edit_routine_title
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.routineName,
                onValueChange = onRoutineNameChanged,
                label = { Text(stringResource(R.string.workout_routine_name_label)) },
                singleLine = true,
            )
            RoutineColorChips(
                selectedColorTag = state.routineColorTag,
                onSelected = onRoutineColorChanged,
            )
            Text(
                text = stringResource(R.string.workout_routine_duration, state.draftDurationMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.draftItems.isEmpty()) {
                EmptyState(R.string.workout_routine_draft_empty)
            } else {
                state.draftItems.forEachIndexed { index, item ->
                    DraftExerciseCard(
                        index = index,
                        item = item,
                        first = index == 0,
                        last = index == state.draftItems.lastIndex,
                        onRemove = { onRemoveDraftItem(index) },
                        onMoveUp = { onMoveDraftItem(index, -1) },
                        onMoveDown = { onMoveDraftItem(index, 1) },
                        onSetsChanged = { onDraftSetsChanged(index, it) },
                        onRepsChanged = { onDraftRepsChanged(index, it) },
                        onWeightChanged = { onDraftWeightChanged(index, it) },
                        onRestChanged = { onDraftRestChanged(index, it) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.workout_add_exercise_to_routine),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(state.exercises, key = { it.id }) { exercise ->
                    FilterChip(
                        selected = false,
                        onClick = { onAddExerciseToDraft(exercise) },
                        label = { Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                onClick = onCreateRoutine,
                enabled = state.routineName.isNotBlank(),
            ) {
                Text(stringResource(R.string.workout_save_routine))
            }
            if (state.editingRoutineId != null) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget),
                    onClick = onCancelRoutineEditing,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun DraftExerciseCard(
    index: Int,
    item: RoutineDraftItem,
    first: Boolean,
    last: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSetsChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onRestChanged: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    var dragOffset by remember(item.key) { mutableStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.key, first, last) {
                detectVerticalDragGestures(
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = { dragOffset = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                    when {
                        dragOffset <= -DRAG_REORDER_THRESHOLD_PX && !first -> {
                            onMoveUp()
                            dragOffset = 0f
                        }
                        dragOffset >= DRAG_REORDER_THRESHOLD_PX && !last -> {
                            onMoveDown()
                            dragOffset = 0f
                        }
                    }
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.workout_ordered_exercise, index + 1, item.exerciseName),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onMoveUp,
                    enabled = !first,
                ) {
                    Icon(Icons.Filled.ExpandLess, contentDescription = stringResource(R.string.workout_move_exercise_up_cd))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !last,
                ) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.workout_move_exercise_down_cd))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workout_remove_exercise_cd))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                CompactNumberField(
                    modifier = Modifier.weight(1f),
                    value = item.sets,
                    onValueChange = onSetsChanged,
                    labelRes = R.string.workout_sets_label,
                )
                CompactNumberField(
                    modifier = Modifier.weight(1f),
                    value = item.reps,
                    onValueChange = onRepsChanged,
                    labelRes = R.string.workout_reps_label,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                CompactNumberField(
                    modifier = Modifier.weight(1f),
                    value = item.weightKg,
                    onValueChange = onWeightChanged,
                    labelRes = R.string.workout_weight_label,
                    keyboardType = KeyboardType.Decimal,
                )
                CompactNumberField(
                    modifier = Modifier.weight(1f),
                    value = item.restSeconds,
                    onValueChange = onRestChanged,
                    labelRes = R.string.workout_rest_label,
                )
            }
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun RoutineCard(
    routine: Routine,
    selected: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ColorSwatch(routine.colorTag)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.workout_routine_summary, routine.exercises.size, routine.estimatedDurationMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = stringResource(R.string.workout_archive_routine_cd),
                )
            }
        }
    }
}

@Composable
private fun RoutineDetailCard(
    routine: Routine,
    onEdit: () -> Unit,
    onStart: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ElevatedCard {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SectionTitle(R.string.workout_routine_detail_title)
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                onClick = onStart,
            ) {
                Text(stringResource(R.string.action_start))
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                onClick = onEdit,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = spacing.xs),
                    text = stringResource(R.string.action_edit),
                )
            }
            if (routine.exercises.isEmpty()) {
                EmptyState(R.string.workout_routine_draft_empty)
            } else {
                routine.exercises.forEach { exercise ->
                    Text(
                        text = stringResource(
                            R.string.workout_routine_exercise_line,
                            exercise.orderIndex + 1,
                            exercise.exerciseName,
                            exercise.sets,
                            exercise.reps,
                            exercise.restSeconds,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutSessionCard(
    session: com.atlaspeak.domain.model.workout.WorkoutSession,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = session.routineName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.workout_history_session_summary,
                    session.durationSeconds ?: 0,
                    session.totalVolumeKg ?: 0.0,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutSessionDetailCard(session: com.atlaspeak.domain.model.workout.WorkoutSession) {
    val spacing = LocalSpacing.current
    ElevatedCard {
        Column(
            modifier = Modifier.padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SectionTitle(R.string.workout_history_detail_title)
            session.exercises.forEach { exercise ->
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                )
                exercise.sets.forEach { set ->
                    Text(
                        text = stringResource(
                            R.string.workout_history_set_line,
                            set.setNumber,
                            set.actualReps ?: set.plannedReps,
                            set.weightKg ?: 0.0,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (set.isPersonalRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleGroupChips(
    groups: List<MuscleGroup>,
    selectedGroupId: Int?,
    includeAll: Boolean,
    onSelected: (Int?) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (includeAll) {
            item {
                FilterChip(
                    selected = selectedGroupId == null,
                    onClick = { onSelected(null) },
                    label = { Text(stringResource(R.string.workout_filter_all)) },
                )
            }
        }
        items(groups, key = { it.id }) { group ->
            FilterChip(
                selected = selectedGroupId == group.id,
                onClick = { onSelected(group.id) },
                label = { Text(group.name) },
            )
        }
    }
}

@Composable
private fun RoutineColorChips(
    selectedColorTag: String,
    onSelected: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(R.string.workout_routine_color_label),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            items(routineColorTags, key = { it.hex }) { colorTag ->
                FilterChip(
                    selected = selectedColorTag == colorTag.hex,
                    onClick = { onSelected(colorTag.hex) },
                    label = { Text(stringResource(colorTag.labelRes)) },
                    leadingIcon = { ColorSwatch(colorTag.hex) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(hex: String?) {
    val color = routineColorTags.firstOrNull { it.hex == hex }?.color ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color = color, shape = MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EmptyState(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MessageText(message: TrainUiMessage?) {
    if (message == null) return
    val res = when (message) {
        TrainUiMessage.ExerciseSaved -> R.string.workout_exercise_saved
        TrainUiMessage.RoutineSaved -> R.string.workout_routine_saved
        TrainUiMessage.InvalidExercise -> R.string.workout_invalid_exercise
        TrainUiMessage.InvalidRoutine -> R.string.workout_invalid_routine
    }
    val isError = message == TrainUiMessage.InvalidExercise || message == TrainUiMessage.InvalidRoutine
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

private data class RoutineColorTag(
    val hex: String,
    @StringRes val labelRes: Int,
    val color: Color,
)

private val routineColorTags = listOf(
    RoutineColorTag("#E53935", R.string.workout_color_red, Color(0xFFE53935)),
    RoutineColorTag("#2E7D32", R.string.workout_color_green, Color(0xFF2E7D32)),
    RoutineColorTag("#1565C0", R.string.workout_color_blue, Color(0xFF1565C0)),
    RoutineColorTag("#6A1B9A", R.string.workout_color_purple, Color(0xFF6A1B9A)),
)

private const val DRAG_REORDER_THRESHOLD_PX = 56f
