package com.atlaspeak.presentation.workout

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import com.atlaspeak.domain.usecase.workout.CompleteWorkoutSessionUseCase
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
    private val workoutRepository: WorkoutRepository,
    private val workoutSettingsRepository: WorkoutSettingsRepository,
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
            val updated = set.copy(
                completed = completed,
                actualReps = if (completed) set.actualReps ?: set.plannedReps else set.actualReps,
                completedAt = if (completed) System.currentTimeMillis() else null,
                isPersonalRecord = if (completed) set.isPersonalRecord else false,
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

    fun removeSet(set: WorkoutSet) {
        viewModelScope.launch {
            workoutRepository.deleteSet(set.id)
            reloadSession()
        }
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
        restJob = viewModelScope.launch {
            for (remaining in totalSeconds downTo 0) {
                mutableState.update {
                    it.copy(restTimer = RestTimerUiState(timerId, totalSeconds, remaining))
                }
                delay(if (remaining > 0) 1000 else 420)
            }
            mutableState.update { it.copy(restTimer = null) }
        }
    }
}

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val elapsedSeconds: Long = 0L,
    val restTimer: RestTimerUiState? = null,
    val summary: com.atlaspeak.domain.model.workout.WorkoutSummary? = null,
    val completedSessionId: String? = null,
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
}

data class RestTimerUiState(
    val id: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
) {
    val progress: Float = if (totalSeconds <= 0) 0f else remainingSeconds / totalSeconds.toFloat()
}

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
