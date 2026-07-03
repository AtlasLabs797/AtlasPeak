package com.atlaspeak.presentation.workout

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.model.workout.completedVolumeKg
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import com.atlaspeak.domain.usecase.workout.CompleteWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.DiscardWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.StartWorkoutSessionUseCase
import com.atlaspeak.presentation.navigation.AppRoute
import com.atlaspeak.service.WorkoutForegroundService
import com.atlaspeak.service.WorkoutTimerRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val startWorkoutSessionUseCase: StartWorkoutSessionUseCase,
    private val completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase,
    private val discardWorkoutSessionUseCase: DiscardWorkoutSessionUseCase,
    private val workoutRepository: WorkoutRepository,
    private val workoutSettingsRepository: WorkoutSettingsRepository,
    private val exerciseRepository: ExerciseRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val routineId: String = requireNotNull(savedStateHandle[AppRoute.ActiveWorkout.ROUTINE_ID])
    private val mutableState = MutableStateFlow(ActiveWorkoutUiState())
    val state: StateFlow<ActiveWorkoutUiState> = mutableState.asStateFlow()
    private var restJob: Job? = null
    private var exerciseOrder: List<String> = emptyList()

    init {
        startWorkout()
        loadRestFeedbackSettings()
        loadAvailableExercisesForQuickAdd()
        viewModelScope.launch {
            WorkoutTimerRegistry.state.collect { timer ->
                mutableState.update { current ->
                    val currentSessionId = current.session?.id
                    when {
                        timer.failed && timer.sessionId == currentSessionId -> {
                            current.copy(message = ActiveWorkoutMessage.TimerServiceUnavailable)
                        }
                        timer.sessionId == currentSessionId -> current.copy(elapsedSeconds = timer.elapsedSeconds)
                        else -> current
                    }
                }
            }
        }
    }

    fun onSetCompleted(set: WorkoutSet, completed: Boolean, restSeconds: Int) {
        viewModelScope.launch {
            val completedAt = if (completed) System.currentTimeMillis() else null
            val previousMax = if (completed && set.weightKg != null && completedAt != null) {
                workoutRepository.maxCompletedWeightBefore(set.exerciseId, completedAt)
            } else {
                null
            }
            val updated = set.copy(
                completed = completed,
                actualReps = if (completed) set.actualReps ?: set.plannedReps else set.actualReps,
                completedAt = completedAt,
                isPersonalRecord = if (completed) {
                    set.isPersonalRecord || set.weightKg?.let { weight -> weight > (previousMax ?: 0.0) } == true
                } else {
                    false
                },
            )
            workoutRepository.upsertSet(updated)
            reloadSession()
            if (completed) {
                val session = mutableState.value.session
                if (session != null && session.exercises.flatMap { it.sets }.all { it.completed }) {
                    completeWorkout()
                } else {
                    startRestTimer(restSeconds)
                }
            }
        }
    }

    fun onActualRepsChanged(set: WorkoutSet, value: String) {
        val reps = value.filter { it.isDigit() }.take(3).toIntOrNull()
        viewModelScope.launch {
            workoutRepository.upsertSet(set.copy(actualReps = reps))
            reloadSession()
        }
    }

    fun onWeightChanged(set: WorkoutSet, value: String) {
        val weight = value.decimalInput().toDoubleOrNull()
        viewModelScope.launch {
            workoutRepository.upsertSet(set.copy(weightKg = weight))
            reloadSession()
        }
    }

    fun onRepsTextChanged(set: WorkoutSet, value: String) {
        val sanitized = value.filter { it.isDigit() }.take(3)
        mutableState.update { state ->
            state.copy(
                inputDrafts = state.inputDrafts + (
                    set.id to (state.inputDrafts[set.id] ?: set.toInputDraft()).copy(repsText = sanitized)
                    ),
            )
        }
    }

    fun onWeightTextChanged(set: WorkoutSet, value: String) {
        val sanitized = value.decimalInput()
        mutableState.update { state ->
            state.copy(
                inputDrafts = state.inputDrafts + (
                    set.id to (state.inputDrafts[set.id] ?: set.toInputDraft()).copy(weightText = sanitized)
                    ),
            )
        }
    }

    fun onSetInputCommitted(set: WorkoutSet) {
        val draft = mutableState.value.inputDrafts[set.id] ?: return
        viewModelScope.launch {
            workoutRepository.upsertSet(
                set.copy(
                    actualReps = draft.repsText.ifBlank { null }?.toIntOrNull(),
                    weightKg = draft.weightText.ifBlank { null }?.toDoubleOrNull(),
                ),
            )
            mutableState.update { it.copy(inputDrafts = it.inputDrafts - set.id) }
            reloadSession()
        }
    }

    fun addSet(exercise: ActiveWorkoutExercise) {
        val sessionId = mutableState.value.session?.id ?: return
        val lastSet = exercise.sets.maxByOrNull { it.setNumber }
        viewModelScope.launch {
            workoutRepository.upsertSet(
                WorkoutSet(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    setNumber = (lastSet?.setNumber ?: 0) + 1,
                    plannedReps = lastSet?.plannedReps ?: 10,
                    actualReps = null,
                    weightKg = lastSet?.weightKg,
                    completed = false,
                    completedAt = null,
                    isPersonalRecord = false,
                ),
            )
            reloadSession()
        }
    }

    /**
     * (#16 del informe) Añade un ejercicio extra durante una sesión activa. Crea 3
     * sets vacíos planificados (10 reps) y lo inserta al final de la rutina. Se
     * evita duplicar el mismo ejercicio si ya está en la sesión.
     */
    fun addExerciseDuringWorkout(exerciseId: String) {
        val session = mutableState.value.session ?: return
        if (session.exercises.any { it.exerciseId == exerciseId }) return
        viewModelScope.launch {
            val exercise = exerciseRepository.exercises().firstOrNull { it.id == exerciseId } ?: return@launch
            val newSets = (1..DEFAULT_NEW_EXERCISE_SETS).map { setNumber ->
                WorkoutSet(
                    id = UUID.randomUUID().toString(),
                    sessionId = session.id,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    setNumber = setNumber,
                    plannedReps = DEFAULT_NEW_EXERCISE_REPS,
                    actualReps = null,
                    weightKg = null,
                    completed = false,
                    completedAt = null,
                    isPersonalRecord = false,
                )
            }
            newSets.forEach { workoutRepository.upsertSet(it) }
            reloadSession()
        }
    }

    private fun loadAvailableExercisesForQuickAdd() {
        viewModelScope.launch {
            val exercises = runCatching { exerciseRepository.exercises() }.getOrDefault(emptyList())
            mutableState.update { it.copy(availableExercises = exercises) }
        }
    }

    fun requestRemoveSet(set: WorkoutSet) {
        if (set.hasEnteredData()) {
            mutableState.update { it.copy(pendingSetDeletion = set) }
        } else {
            deleteSetWithUndo(set)
        }
    }

    fun cancelRemoveSet() {
        mutableState.update { it.copy(pendingSetDeletion = null) }
    }

    fun confirmRemoveSet() {
        val set = mutableState.value.pendingSetDeletion ?: return
        deleteSetWithUndo(set)
    }

    fun undoRemoveSet() {
        val set = mutableState.value.deletedSetForUndo ?: return
        viewModelScope.launch {
            workoutRepository.upsertSet(set)
            mutableState.update {
                it.copy(
                    deletedSetForUndo = null,
                    pendingSetDeletion = null,
                )
            }
            reloadSession()
        }
    }

    fun clearDeletedSetNotice() {
        mutableState.update { it.copy(deletedSetForUndo = null) }
    }

    fun moveExercise(index: Int, offset: Int) {
        val session = mutableState.value.session ?: return
        val nextIndex = index + offset
        if (index !in session.exercises.indices || nextIndex !in session.exercises.indices) return
        val reordered = session.exercises.toMutableList().apply {
            add(nextIndex, removeAt(index))
        }.mapIndexed { orderIndex, exercise -> exercise.copy(orderIndex = orderIndex) }
        exerciseOrder = reordered.map { it.exerciseId }
        mutableState.update {
            it.copy(
                session = session.copy(
                    exercises = reordered,
                ),
            )
        }
    }

    fun skipRestTimer() {
        restJob?.cancel()
        restJob = null
        mutableState.update { it.copy(restTimer = null) }
    }

    fun completeWorkout() {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            val summary = completeWorkoutSessionUseCase(session.id) ?: return@launch
            ContextCompat.startForegroundService(context, WorkoutForegroundService.stopIntent(context))
            mutableState.update { it.copy(summary = summary, restTimer = null, completedSessionId = session.id) }
        }
    }

    fun discardWorkout() {
        val session = mutableState.value.session ?: return
        viewModelScope.launch {
            discardWorkoutSessionUseCase(session.id)
            ContextCompat.startForegroundService(context, WorkoutForegroundService.stopIntent(context))
            restJob?.cancel()
            mutableState.update {
                it.copy(
                    session = null,
                    restTimer = null,
                    inputDrafts = emptyMap(),
                    pendingSetDeletion = null,
                    deletedSetForUndo = null,
                    discarded = true,
                )
            }
        }
    }

    fun startTimerServiceIfPermitted() {
        val session = mutableState.value.session ?: return
        if (mutableState.value.timerServiceStartHandled) return
        mutableState.update { it.copy(timerServiceStartHandled = true) }
        startTimerService(session)
    }

    fun onTimerPermissionDenied() {
        mutableState.update {
            it.copy(
                timerServiceStartHandled = true,
                message = ActiveWorkoutMessage.TimerServiceUnavailable,
            )
        }
    }

    override fun onCleared() {
        restJob?.cancel()
        super.onCleared()
    }

    private fun startWorkout() {
        viewModelScope.launch {
            val sessionId = startWorkoutSessionUseCase(routineId)
            if (sessionId == null) {
                mutableState.update { it.copy(isLoading = false, message = ActiveWorkoutMessage.RoutineMissing) }
                return@launch
            }
            val session = workoutRepository.session(sessionId)
            if (session != null) exerciseOrder = session.exercises.map { it.exerciseId }
            mutableState.update { it.copy(isLoading = false, session = session?.withCurrentExerciseOrder()) }
        }
    }

    private fun loadRestFeedbackSettings() {
        viewModelScope.launch {
            mutableState.update { it.copy(restFeedbackSettings = workoutSettingsRepository.restTimerFeedbackSettings()) }
        }
    }

    private fun startTimerService(session: WorkoutSession) {
        try {
            ContextCompat.startForegroundService(
                context,
                WorkoutForegroundService.startIntent(context, session.id, session.startTime),
            )
        } catch (_: SecurityException) {
            mutableState.update { it.copy(message = ActiveWorkoutMessage.TimerServiceUnavailable) }
        }
    }

    private suspend fun reloadSession() {
        val id = mutableState.value.session?.id ?: return
        mutableState.update { it.copy(session = workoutRepository.session(id)?.withCurrentExerciseOrder()) }
    }

    private fun deleteSetWithUndo(set: WorkoutSet) {
        viewModelScope.launch {
            workoutRepository.deleteSet(set.id)
            mutableState.update {
                it.copy(
                    inputDrafts = it.inputDrafts - set.id,
                    pendingSetDeletion = null,
                    deletedSetForUndo = set,
                    deletedSetEventId = it.deletedSetEventId + 1,
                )
            }
            reloadSession()
        }
    }

    private fun WorkoutSession.withCurrentExerciseOrder(): WorkoutSession {
        if (exerciseOrder.isEmpty()) return this
        val orderIndex = exerciseOrder.withIndex().associate { it.value to it.index }
        return copy(
            exercises = exercises.sortedWith(
                compareBy<ActiveWorkoutExercise> { orderIndex[it.exerciseId] ?: Int.MAX_VALUE }
                    .thenBy { it.orderIndex },
            ).mapIndexed { index, exercise -> exercise.copy(orderIndex = index) },
        )
    }

    private fun startRestTimer(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        restJob?.cancel()
        val timerId = UUID.randomUUID().toString()
        val endsAt = System.currentTimeMillis() + totalSeconds * 1000L
        restJob = viewModelScope.launch {
            while (true) {
                val remaining = ((endsAt - System.currentTimeMillis()) / 1000L).toInt().coerceIn(0, totalSeconds)
                mutableState.update {
                    it.copy(restTimer = RestTimerUiState(timerId, totalSeconds, remaining))
                }
                if (remaining == 0) break
                delay(250)
            }
            delay(420)
            mutableState.update { it.copy(restTimer = null) }
        }
    }

    private companion object {
        const val DEFAULT_NEW_EXERCISE_SETS = 3
        const val DEFAULT_NEW_EXERCISE_REPS = 10
    }
}

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val elapsedSeconds: Long = 0L,
    val restTimer: RestTimerUiState? = null,
    val summary: com.atlaspeak.domain.model.workout.WorkoutSummary? = null,
    val completedSessionId: String? = null,
    val discarded: Boolean = false,
    val inputDrafts: Map<String, WorkoutSetInputDraft> = emptyMap(),
    val pendingSetDeletion: WorkoutSet? = null,
    val deletedSetForUndo: WorkoutSet? = null,
    val deletedSetEventId: Long = 0L,
    val availableExercises: List<Exercise> = emptyList(),
    val timerServiceStartHandled: Boolean = false,
    val restFeedbackSettings: RestTimerFeedbackSettings = RestTimerFeedbackSettings(
        soundEnabled = true,
        vibrationEnabled = true,
    ),
    val message: ActiveWorkoutMessage? = null,
) {
    val completedExerciseCount: Int = session?.exercises?.count { exercise ->
        exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }
    } ?: 0

    val totalExerciseCount: Int = session?.exercises?.size ?: 0

    val exerciseCompletionProgress: Float =
        if (totalExerciseCount <= 0) 0f else completedExerciseCount / totalExerciseCount.toFloat()

    val liveTotalVolumeKg: Double = session?.completedVolumeKg() ?: 0.0
}

data class RestTimerUiState(
    val id: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
) {
    val progress: Float = if (totalSeconds <= 0) 0f else remainingSeconds / totalSeconds.toFloat()
}

data class WorkoutSetInputDraft(
    val weightText: String,
    val repsText: String,
)

enum class ActiveWorkoutMessage {
    RoutineMissing,
    TimerServiceUnavailable,
}

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

private fun WorkoutSet.toInputDraft(): WorkoutSetInputDraft {
    return WorkoutSetInputDraft(
        weightText = weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
        repsText = actualReps?.toString().orEmpty(),
    )
}

private fun WorkoutSet.hasEnteredData(): Boolean {
    return completed || actualReps != null || weightKg != null
}
