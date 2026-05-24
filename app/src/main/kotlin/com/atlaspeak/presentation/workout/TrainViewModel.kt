package com.atlaspeak.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExerciseInput
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.usecase.workout.ExerciseUseCase
import com.atlaspeak.domain.usecase.workout.RoutineUseCase
import com.atlaspeak.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TrainViewModel @Inject constructor(
    private val exerciseUseCase: ExerciseUseCase,
    private val routineUseCase: RoutineUseCase,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TrainUiState())
    val state: StateFlow<TrainUiState> = mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun selectTab(tab: TrainTab) {
        mutableState.update { it.copy(selectedTab = tab, message = null) }
    }

    fun onSearchQueryChanged(query: String) {
        mutableState.update { it.copy(searchQuery = query, message = null) }
        refreshExercises()
    }

    fun onMuscleGroupFilterChanged(groupId: Int?) {
        mutableState.update { it.copy(selectedMuscleGroupId = groupId, message = null) }
        refreshExercises()
    }

    fun onNewExerciseNameChanged(name: String) {
        mutableState.update { it.copy(newExerciseName = name, message = null) }
    }

    fun onNewExerciseGroupChanged(groupId: Int) {
        mutableState.update { it.copy(newExerciseGroupId = groupId, message = null) }
    }

    fun createCustomExercise() {
        val snapshot = mutableState.value
        val groupId = snapshot.newExerciseGroupId
        if (groupId == null) {
            mutableState.update { it.copy(message = TrainUiMessage.InvalidExercise) }
            return
        }
        viewModelScope.launch {
            val saved = exerciseUseCase.createOrUpdateCustomExercise(
                name = snapshot.newExerciseName,
                muscleGroupId = groupId,
                id = snapshot.editingExerciseId ?: UUID.randomUUID().toString(),
            )
            mutableState.update {
                it.copy(
                    newExerciseName = if (saved) "" else it.newExerciseName,
                    editingExerciseId = if (saved) null else it.editingExerciseId,
                    message = if (saved) TrainUiMessage.ExerciseSaved else TrainUiMessage.InvalidExercise,
                )
            }
            refreshAll()
        }
    }

    fun startEditingExercise(exercise: Exercise) {
        if (exercise.isPreset) return
        mutableState.update {
            it.copy(
                selectedTab = TrainTab.Exercises,
                editingExerciseId = exercise.id,
                newExerciseName = exercise.name,
                newExerciseGroupId = exercise.muscleGroup.id,
                message = null,
            )
        }
    }

    fun cancelExerciseEditing() {
        mutableState.update {
            it.copy(
                editingExerciseId = null,
                newExerciseName = "",
                message = null,
            )
        }
    }

    fun archiveExercise(id: String) {
        viewModelScope.launch {
            exerciseUseCase.archiveExercise(id)
            refreshAll()
        }
    }

    fun onRoutineNameChanged(name: String) {
        mutableState.update { it.copy(routineName = name, message = null) }
    }

    fun onRoutineColorChanged(colorTag: String) {
        mutableState.update { it.copy(routineColorTag = colorTag, message = null) }
    }

    fun addExerciseToDraft(exercise: Exercise) {
        mutableState.update {
            it.copy(
                draftItems = it.draftItems + RoutineDraftItem(
                    key = UUID.randomUUID().toString(),
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                ),
                message = null,
            )
        }
    }

    fun removeDraftItem(index: Int) {
        mutableState.update {
            it.copy(
                draftItems = it.draftItems.filterIndexed { itemIndex, _ -> itemIndex != index },
                message = null,
            )
        }
    }

    fun moveDraftItem(index: Int, offset: Int) {
        val nextIndex = index + offset
        mutableState.update {
            if (index !in it.draftItems.indices || nextIndex !in it.draftItems.indices) {
                it
            } else {
                it.copy(draftItems = it.draftItems.toMutableList().apply {
                    add(nextIndex, removeAt(index))
                })
            }
        }
    }

    fun onDraftSetsChanged(index: Int, value: String) {
        updateDraftItem(index) { it.copy(sets = value.onlyDigits()) }
    }

    fun onDraftRepsChanged(index: Int, value: String) {
        updateDraftItem(index) { it.copy(reps = value.onlyDigits()) }
    }

    fun onDraftWeightChanged(index: Int, value: String) {
        updateDraftItem(index) { it.copy(weightKg = value.decimalInput()) }
    }

    fun onDraftRestChanged(index: Int, value: String) {
        updateDraftItem(index) { it.copy(restSeconds = value.onlyDigits()) }
    }

    fun createRoutine() {
        val snapshot = mutableState.value
        viewModelScope.launch {
            val saved = routineUseCase.createOrUpdateRoutine(
                name = snapshot.routineName,
                colorTag = snapshot.routineColorTag,
                exercises = snapshot.draftInputs(),
                id = snapshot.editingRoutineId ?: UUID.randomUUID().toString(),
            )
            mutableState.update {
                it.copy(
                    routineName = if (saved) "" else it.routineName,
                    draftItems = if (saved) emptyList() else it.draftItems,
                    editingRoutineId = if (saved) null else it.editingRoutineId,
                    message = if (saved) TrainUiMessage.RoutineSaved else TrainUiMessage.InvalidRoutine,
                )
            }
            refreshAll()
        }
    }

    fun startEditingRoutine(routine: Routine) {
        mutableState.update {
            it.copy(
                selectedTab = TrainTab.Routines,
                editingRoutineId = routine.id,
                routineName = routine.name,
                routineColorTag = routine.colorTag ?: DEFAULT_ROUTINE_COLOR_TAG,
                draftItems = routine.exercises.sortedBy { exercise -> exercise.orderIndex }.map { exercise ->
                    RoutineDraftItem(
                        key = exercise.id,
                        exerciseId = exercise.exerciseId,
                        exerciseName = exercise.exerciseName,
                        sets = exercise.sets.toString(),
                        reps = exercise.reps.toString(),
                        weightKg = exercise.weightKg?.toString().orEmpty(),
                        restSeconds = exercise.restSeconds.toString(),
                    )
                },
                message = null,
            )
        }
    }

    fun cancelRoutineEditing() {
        mutableState.update {
            it.copy(
                editingRoutineId = null,
                routineName = "",
                draftItems = emptyList(),
                routineColorTag = DEFAULT_ROUTINE_COLOR_TAG,
                message = null,
            )
        }
    }

    fun selectRoutine(id: String) {
        mutableState.update { it.copy(selectedRoutineId = id, selectedTab = TrainTab.Routines, message = null) }
    }

    fun selectWorkoutSession(id: String) {
        mutableState.update { it.copy(selectedWorkoutSessionId = id, selectedTab = TrainTab.History, message = null) }
    }

    fun archiveRoutine(id: String) {
        viewModelScope.launch {
            routineUseCase.archiveRoutine(id)
            mutableState.update { it.copy(selectedRoutineId = null, message = null) }
            refreshAll()
        }
    }

    private fun updateDraftItem(index: Int, transform: (RoutineDraftItem) -> RoutineDraftItem) {
        mutableState.update {
            if (index !in it.draftItems.indices) {
                it
            } else {
                it.copy(
                    draftItems = it.draftItems.mapIndexed { itemIndex, item ->
                        if (itemIndex == index) transform(item) else item
                    },
                    message = null,
                )
            }
        }
    }

    private fun refreshAll() {
        viewModelScope.launch {
            val groups = exerciseUseCase.muscleGroups()
            val snapshot = mutableState.value
            val exercises = exerciseUseCase.library(snapshot.searchQuery, snapshot.selectedMuscleGroupId)
            val routines = routineUseCase.routines()
            val workoutSessions = workoutRepository.sessions().filter { it.completed }
            val selectedRoutineId = snapshot.selectedRoutineId
                ?.takeIf { id -> routines.any { it.id == id } }
                ?: routines.firstOrNull()?.id
            val selectedWorkoutSessionId = snapshot.selectedWorkoutSessionId
                ?.takeIf { id -> workoutSessions.any { it.id == id } }
                ?: workoutSessions.firstOrNull()?.id
            mutableState.update {
                it.copy(
                    isLoading = false,
                    muscleGroups = groups,
                    exercises = exercises,
                    routines = routines,
                    workoutSessions = workoutSessions,
                    selectedRoutineId = selectedRoutineId,
                    selectedWorkoutSessionId = selectedWorkoutSessionId,
                    newExerciseGroupId = it.newExerciseGroupId ?: groups.firstOrNull()?.id,
                )
            }
        }
    }

    private fun refreshExercises() {
        viewModelScope.launch {
            val snapshot = mutableState.value
            val exercises = exerciseUseCase.library(snapshot.searchQuery, snapshot.selectedMuscleGroupId)
            mutableState.update { it.copy(exercises = exercises, isLoading = false) }
        }
    }
}

data class TrainUiState(
    val isLoading: Boolean = true,
    val selectedTab: TrainTab = TrainTab.Exercises,
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val selectedRoutineId: String? = null,
    val selectedWorkoutSessionId: String? = null,
    val searchQuery: String = "",
    val selectedMuscleGroupId: Int? = null,
    val editingExerciseId: String? = null,
    val newExerciseName: String = "",
    val newExerciseGroupId: Int? = null,
    val editingRoutineId: String? = null,
    val routineName: String = "",
    val routineColorTag: String = DEFAULT_ROUTINE_COLOR_TAG,
    val draftItems: List<RoutineDraftItem> = emptyList(),
    val message: TrainUiMessage? = null,
) {
    val selectedRoutine: Routine? = routines.firstOrNull { it.id == selectedRoutineId }
    val selectedWorkoutSession: WorkoutSession? = workoutSessions.firstOrNull { it.id == selectedWorkoutSessionId }
    val draftDurationMinutes: Int = RoutineUseCase.estimatedDurationMinutes(draftInputs())

    fun draftInputs(): List<RoutineExerciseInput> = draftItems.map { it.toInput() }
}

data class RoutineDraftItem(
    val key: String,
    val exerciseId: String,
    val exerciseName: String,
    val sets: String = "3",
    val reps: String = "10",
    val weightKg: String = "",
    val restSeconds: String = "90",
) {
    fun toInput(): RoutineExerciseInput = RoutineExerciseInput(
        exerciseId = exerciseId,
        sets = sets.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        reps = reps.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        weightKg = weightKg.toDoubleOrNull()?.takeIf { it >= 0.0 },
        restSeconds = restSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

enum class TrainTab {
    Exercises,
    Routines,
    History,
}

enum class TrainUiMessage {
    ExerciseSaved,
    RoutineSaved,
    InvalidExercise,
    InvalidRoutine,
}

const val DEFAULT_ROUTINE_COLOR_TAG = "#E53935"

private fun String.onlyDigits(): String = filter { it.isDigit() }.take(3)

private fun String.decimalInput(): String {
    val builder = StringBuilder()
    var dotSeen = false
    for (char in this) {
        when {
            char.isDigit() -> builder.append(char)
            (char == '.' || char == ',') && !dotSeen -> {
                builder.append('.')
                dotSeen = true
            }
        }
    }
    return builder.toString().take(6)
}
