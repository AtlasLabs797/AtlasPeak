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
import com.atlaspeak.domain.usecase.workout.ActiveSessionStartResult
import com.atlaspeak.domain.usecase.workout.CompleteWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.DiscardWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.ResumeWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.StartWorkoutSessionUseCase
import com.atlaspeak.presentation.navigation.AppRoute
import com.atlaspeak.service.WorkoutForegroundService
import com.atlaspeak.service.WorkoutRestTimerState
import com.atlaspeak.service.WorkoutTimerRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@HiltViewModel
class ActiveWorkoutViewModel(
    savedStateHandle: SavedStateHandle,
    private val startWorkoutSessionUseCase: StartWorkoutSessionUseCase,
    private val resumeWorkoutSessionUseCase: ResumeWorkoutSessionUseCase,
    private val completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase,
    private val discardWorkoutSessionUseCase: DiscardWorkoutSessionUseCase,
    private val workoutRepository: WorkoutRepository,
    private val workoutSettingsRepository: WorkoutSettingsRepository,
    private val exerciseRepository: ExerciseRepository,
    @ApplicationContext private val context: Context,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        startWorkoutSessionUseCase: StartWorkoutSessionUseCase,
        resumeWorkoutSessionUseCase: ResumeWorkoutSessionUseCase,
        completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase,
        discardWorkoutSessionUseCase: DiscardWorkoutSessionUseCase,
        workoutRepository: WorkoutRepository,
        workoutSettingsRepository: WorkoutSettingsRepository,
        exerciseRepository: ExerciseRepository,
        @ApplicationContext context: Context,
    ) : this(
        savedStateHandle = savedStateHandle,
        startWorkoutSessionUseCase = startWorkoutSessionUseCase,
        resumeWorkoutSessionUseCase = resumeWorkoutSessionUseCase,
        completeWorkoutSessionUseCase = completeWorkoutSessionUseCase,
        discardWorkoutSessionUseCase = discardWorkoutSessionUseCase,
        workoutRepository = workoutRepository,
        workoutSettingsRepository = workoutSettingsRepository,
        exerciseRepository = exerciseRepository,
        context = context,
        now = { System.currentTimeMillis() },
    )

    private val routineId: String = requireNotNull(savedStateHandle[AppRoute.ActiveWorkout.ROUTINE_ID])
    private val weeklyPlanSessionId: String? = savedStateHandle[AppRoute.ActiveWorkout.WEEKLY_PLAN_SESSION_ID]
    private val mutableState = MutableStateFlow(ActiveWorkoutUiState())
    val state: StateFlow<ActiveWorkoutUiState> = mutableState.asStateFlow()
    private var exerciseOrder: List<String> = emptyList()
    // BUG-092: el cronometro visual debe seguir avanzando aunque el WorkoutForegroundService
    // no este vivo (FGS no arranca por SecurityException, lo mata el OS o el permiso
    // ACTIVITY_RECOGNITION es denegado). El job solo se lanza cuando el registry no tiene un
    // timer sano para la sesion actual; cuando el FGS vuelve, se para.
    private var localTimerJob: Job? = null

    init {
        startWorkout()
        loadRestFeedbackSettings()
        loadAvailableExercisesForQuickAdd()
        viewModelScope.launch {
            WorkoutTimerRegistry.state.collect { _ ->
                reconcileTimerState()
            }
        }
    }

    /**
     * Decide que hacer con el cronometro en funcion del estado del FGS y de la sesion
     * cargada en la VM. El collector del registry lo invoca cuando el FGS cambia, y
     * `loadSession` lo invoca tras cargar la sesion para cubrir la carrera en la que
     * el registry ya emitio su valor inicial antes de que la sesion estuviera lista
     * (en ese caso `startLocalTimerIfNeeded` arrancaria y saldria por `session == null`,
     * y sin re-trigger el cronometro quedaria muerto hasta el siguiente cambio del FGS).
     */
    private fun reconcileTimerState() {
        val timer = WorkoutTimerRegistry.state.value
        val sessionId = mutableState.value.session?.id
        when {
            // FGS rechazo el arranque para esta sesion: el registry queda en failed=true.
            // El collector es el unico que conoce el mensaje, pero el tiempo se deriva
            // localmente desde session.startTime (mismo calculo que hace el FGS).
            timer.failed && timer.sessionId == sessionId -> {
                startLocalTimerIfNeeded()
                mutableState.update {
                    it.copy(
                        message = ActiveWorkoutMessage.TimerServiceUnavailable,
                        restTimer = null,
                    )
                }
            }
            // FGS sano para nuestra sesion: espejamos su tiempo y su rest timer.
            timer.sessionId == sessionId && timer.running -> {
                stopLocalTimer()
                mutableState.update {
                    it.copy(
                        elapsedSeconds = timer.elapsedSeconds,
                        restTimer = timer.restTimer?.toUiState(),
                        // Solo retiramos el aviso de fallo del FGS; otros mensajes se conservan.
                        message = it.message.takeUnless { m -> m == ActiveWorkoutMessage.TimerServiceUnavailable },
                    )
                }
            }
            // Registry vacio: el FGS aun no ha arrancado para esta sesion o ya cerro.
            // Mientras tengamos sesion cargada en la VM, el fallback local mantiene
            // el contador en movimiento.
            timer.sessionId == null -> {
                startLocalTimerIfNeeded()
                mutableState.update { it.copy(restTimer = null) }
            }
            else -> { /* registry de otra sesion: no tocamos nada */ }
        }
    }

    private fun startLocalTimerIfNeeded() {
        if (localTimerJob?.isActive == true) return
        localTimerJob = viewModelScope.launch {
            while (isActive) {
                val session = mutableState.value.session ?: break
                val elapsed = ((now() - session.startTime) / 1000L).coerceAtLeast(0L)
                mutableState.update { it.copy(elapsedSeconds = elapsed) }
                delay(1000)
            }
        }
    }

    private fun stopLocalTimer() {
        localTimerJob?.cancel()
        localTimerJob = null
    }

    override fun onCleared() {
        stopLocalTimer()
        super.onCleared()
    }

    /**
     * Continua con la sesion activa en curso ignorando la rutina solicitada.
     * Usado por el boton "Continuar entrenamiento" del dialogo de conflicto.
     */
    fun resumeActiveSession() {
        viewModelScope.launch {
            val sessionId = resumeWorkoutSessionUseCase() ?: run {
                startWorkout()
                return@launch
            }
            loadSession(sessionId)
        }
    }

    /**
     * Borra la sesion activa actual y arranca una nueva con la rutina solicitada.
     */
    fun discardActiveSessionAndStartNew() {
        if (mutableState.value.discardInProgress) return
        mutableState.update { it.copy(discardInProgress = true) }
        viewModelScope.launch {
            val activeId = workoutRepository.findActiveSession()?.id
            if (activeId != null) {
                discardWorkoutSessionUseCase(activeId)
                // El servicio de timer de la sesion anterior esta en primer plano;
                // hay que pararlo para no dejar una notificacion zombi.
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        WorkoutForegroundService.stopIntent(context),
                    )
                }
            }
            // Esperamos a que la nueva sesion se haya cargado para que el boton siga
            // deshabilitado durante todo el ciclo y no se pueda re-disparar en una carrera.
            startWorkout().join()
            mutableState.update { it.copy(discardInProgress = false) }
        }
    }

    /** Cierra el dialogo de conflicto sin tocar nada. */
    fun dismissActiveSessionConflict() {
        mutableState.update { it.copy(conflict = null) }
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
        context.startService(WorkoutForegroundService.clearRestIntent(context))
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

    private fun startWorkout(): Job {
        return viewModelScope.launch {
            when (val result = startWorkoutSessionUseCase(routineId, weeklyPlanSessionId)) {
                is ActiveSessionStartResult.Started, is ActiveSessionStartResult.Resumed -> {
                    loadSession(result.sessionId)
                }
                is ActiveSessionStartResult.Conflict -> {
                    val active = workoutRepository.session(result.sessionId)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            conflict = active?.let { session ->
                                ActiveSessionConflictUi(
                                    activeSessionId = session.id,
                                    activeRoutineName = session.routineName.orEmpty(),
                                )
                            },
                        )
                    }
                }
                ActiveSessionStartResult.NotFound -> {
                    mutableState.update {
                        it.copy(isLoading = false, message = ActiveWorkoutMessage.RoutineMissing)
                    }
                }
            }
        }
    }

    private suspend fun loadSession(sessionId: String) {
        val session = workoutRepository.session(sessionId)
        if (session != null) exerciseOrder = session.exercises.map { it.exerciseId }
        val elapsedSeconds = session?.let { (now() - it.startTime) / 1000L } ?: 0L
        mutableState.update {
            it.copy(
                isLoading = false,
                conflict = null,
                session = session?.withCurrentExerciseOrder(),
                elapsedSeconds = elapsedSeconds.coerceAtLeast(0L),
            )
        }
        // BUG-092: re-evaluamos el cronometro tras cargar la sesion. Si el collector
        // del WorkoutTimerRegistry ya emitio su valor inicial antes de que la sesion
        // estuviera cargada (carrera posible en produccion con el dispatcher Main),
        // `startLocalTimerIfNeeded` habria arrancado el job y este habria salido por
        // `session == null` sin re-arrancar; sin esta llamada el cronometro quedaria
        // muerto hasta el siguiente cambio del FGS.
        reconcileTimerState()
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
        val session = mutableState.value.session ?: return
        val timerId = UUID.randomUUID().toString()
        val endsAt = System.currentTimeMillis() + totalSeconds * 1000L
        val settings = mutableState.value.restFeedbackSettings
        try {
            ContextCompat.startForegroundService(
                context,
                WorkoutForegroundService.startRestIntent(
                    context = context,
                    sessionId = session.id,
                    startedAt = session.startTime,
                    restId = timerId,
                    totalSeconds = totalSeconds,
                    endsAtMillis = endsAt,
                    soundEnabled = settings.soundEnabled,
                    vibrationEnabled = settings.vibrationEnabled,
                ),
            )
        } catch (_: SecurityException) {
            mutableState.update { it.copy(message = ActiveWorkoutMessage.TimerServiceUnavailable) }
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
    val conflict: ActiveSessionConflictUi? = null,
    val discardInProgress: Boolean = false,
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

data class ActiveSessionConflictUi(
    val activeSessionId: String,
    val activeRoutineName: String,
)

data class RestTimerUiState(
    val id: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val alerting: Boolean,
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

private fun WorkoutRestTimerState.toUiState(): RestTimerUiState {
    return RestTimerUiState(
        id = id,
        totalSeconds = totalSeconds,
        remainingSeconds = remainingSeconds,
        alerting = alerting,
    )
}
