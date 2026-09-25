package com.atlaspeak.presentation.workout

import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import com.atlaspeak.domain.usecase.workout.CompleteWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.DiscardWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.ResumeWorkoutSessionUseCase
import com.atlaspeak.domain.usecase.workout.StartWorkoutSessionUseCase
import com.atlaspeak.presentation.navigation.AppRoute
import com.atlaspeak.service.WorkoutTimerRegistry
import com.atlaspeak.service.WorkoutTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val routineRepository = FakeRoutineRepository()
    private val workoutRepository = FakeWorkoutRepository()
    private val workoutSettingsRepository = FakeWorkoutSettingsRepository()
    private val exerciseRepository = FakeExerciseRepository()
    private val completeWorkoutSessionUseCase = CompleteWorkoutSessionUseCase(workoutRepository)
    private val discardWorkoutSessionUseCase = DiscardWorkoutSessionUseCase(workoutRepository)
    private val resumeWorkoutSessionUseCase = ResumeWorkoutSessionUseCase(workoutRepository)
    private val startWorkoutSessionUseCase = StartWorkoutSessionUseCase(
        routineRepository = routineRepository,
        workoutRepository = workoutRepository,
        now = { 1_700_000_000_000L },
    )
    // BUG-092: reloj inyectable para poder avanzar el tiempo de forma determinista
    // en los tests del fallback local del cronometro.
    private val fixedClock = TestClock(initialMillis = 1_700_000_000_000L)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        routineRepository.routines = listOf(upperRoutine(), lowerRoutine())
        WorkoutTimerRegistry.update(WorkoutTimerState())
        fixedClock.reset()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `process recreation preserves active session and does not duplicate it`() = runTest(dispatcher) {
        val firstSessionId = newViewModelAndStart().session?.id
        assertNotNull(firstSessionId)

        // Usuario registra dos sets completados antes de que Android mate el proceso.
        val firstSession = workoutRepository.sessions.single()
        val updated = firstSession.copy(
            exercises = firstSession.exercises.map { ex ->
                ex.copy(
                    sets = ex.sets.mapIndexed { idx, set ->
                        set.copy(
                            completed = idx < 2,
                            actualReps = if (idx < 2) set.plannedReps else null,
                            completedAt = if (idx < 2) 1_700_000_100_000L else null,
                        )
                    },
                )
            },
        )
        workoutRepository.sessions = listOf(updated)

        // Simulamos un kill: se crea un ViewModel nuevo (mismo repository).
        val secondSessionId = newViewModelAndStart().session?.id
        assertEquals(firstSessionId, secondSessionId)
        assertEquals(1, workoutRepository.sessions.size)
        val reloaded = workoutRepository.session(firstSessionId!!)!!
        assertTrue(reloaded.exercises.first().sets[0].completed)
        assertTrue(reloaded.exercises.first().sets[1].completed)
        assertFalse(reloaded.exercises.first().sets[2].completed)
    }

    @Test
    fun `elapsed seconds derives from startTime after recreation`() = runTest(dispatcher) {
        newViewModelAndStart()
        val startTime = workoutRepository.sessions.single().startTime

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(workoutRepository.sessions.single().id, state.session?.id)
        // ElapsedSeconds >= 0: el reloj real de la JVM avanza durante el test,
        // asi que nunca deberia ser negativo.
        assertTrue(state.elapsedSeconds >= 0L)
        // La sesion se hidrata desde Room y la fecha de inicio coincide.
        assertEquals(startTime, state.session?.startTime)
        // durationSeconds sigue null hasta que se complete la sesion.
        assertNull(state.session?.durationSeconds)
    }

    @Test
    fun `conflict state is exposed when active session belongs to a different routine`() = runTest(dispatcher) {
        val first = newViewModelAndStart("routine_lower").session
        assertNotNull(first)

        val viewModel = newViewModel(routineId = "routine_upper")
        dispatcher.scheduler.advanceUntilIdle()

        val conflict = viewModel.state.value.conflict
        assertNotNull(conflict)
        assertEquals(first!!.id, conflict!!.activeSessionId)
        assertEquals(1, workoutRepository.sessions.size)
        assertEquals("routine_lower", workoutRepository.sessions.single().routineId)
    }

    @Test
    fun `discard active and start new replaces active session`() = runTest(dispatcher) {
        newViewModelAndStart("routine_lower")

        val viewModel = newViewModel(routineId = "routine_upper")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.discardActiveSessionAndStartNew()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.conflict)
        assertNotNull(state.session)
        assertEquals("routine_upper", state.session!!.routineId)
        assertEquals(1, workoutRepository.sessions.size)
    }

    @Test
    fun `resume active keeps the existing session`() = runTest(dispatcher) {
        val first = newViewModelAndStart("routine_lower").session

        val viewModel = newViewModel(routineId = "routine_upper")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.resumeActiveSession()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.conflict)
        assertEquals(first!!.id, state.session?.id)
        assertEquals(1, workoutRepository.sessions.size)
    }

    @Test
    fun `dismiss conflict does not touch active session`() = runTest(dispatcher) {
        val first = newViewModelAndStart("routine_lower").session
        val viewModel = newViewModel(routineId = "routine_upper")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.dismissActiveSessionConflict()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.conflict)
        assertEquals(first!!.id, workoutRepository.sessions.single().id)
    }

    @Test
    fun `routine missing produces RoutineMissing message without active session`() = runTest(dispatcher) {
        val viewModel = newViewModel(routineId = "missing")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ActiveWorkoutMessage.RoutineMissing, state.message)
        assertNull(state.session)
        assertEquals(0, workoutRepository.sessions.size)
    }

    @Test
    fun `elapsed_seconds keeps increasing when foreground service fails to start`() = runTest(dispatcher) {
        val viewModel = newStartedViewModel()
        val sessionId = viewModel.session?.id
        assertNotNull(sessionId)

        // BUG-092: WorkoutForegroundService rechazo startForeground (SecurityException,
        // OS kill, permiso ACTIVITY_RECOGNITION denegado...). El registry queda en
        // failed=true para nuestra sesion; la VM debe seguir derivando elapsedSeconds
        // desde session.startTime sin depender del FGS.
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = sessionId,
                startedAt = fixedClock.currentMillis(),
                running = false,
                failed = true,
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ActiveWorkoutMessage.TimerServiceUnavailable, viewModel.state.value.message)

        fixedClock.advanceBy(5_000L)
        advanceLocalTimer(5_000L)

        val state = viewModel.state.value
        assertTrue(
            state.elapsedSeconds >= 5L,
            "expected elapsedSeconds >= 5, was ${state.elapsedSeconds}",
        )
    }

    @Test
    fun `elapsed_seconds keeps increasing when foreground service never started`() = runTest(dispatcher) {
        val viewModel = newStartedViewModel()
        assertNotNull(viewModel.session?.id)
        // Registry queda como en el setUp (estado inicial vacio): el FGS no ha arrancado
        // o su startForegroundService lanzo SecurityException antes de tocar el registry.
        assertEquals(WorkoutTimerState(), WorkoutTimerRegistry.state.value)

        fixedClock.advanceBy(5_000L)
        advanceLocalTimer(5_000L)

        val state = viewModel.state.value
        assertTrue(
            state.elapsedSeconds >= 5L,
            "expected elapsedSeconds >= 5, was ${state.elapsedSeconds}",
        )
        // Sin FGS nunca arrancado no se debe estar mostrando el mensaje de fallback.
        assertNull(state.message)
    }

    @Test
    fun `elapsed_seconds keeps increasing across recreation when foreground service is unavailable`() = runTest(dispatcher) {
        val firstVm = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val firstSessionId = firstVm.state.value.session?.id
        assertNotNull(firstSessionId)
        val startTime = firstVm.state.value.session!!.startTime

        // Simulamos 3 segundos avanzando el reloj y el job local antes de recrear la VM.
        fixedClock.advanceBy(3_000L)
        advanceLocalTimer(3_000L)
        val firstElapsed = firstVm.state.value.elapsedSeconds
        assertTrue(firstElapsed >= 3L, "first VM elapsed was $firstElapsed")

        // Recreamos la VM (mismo repository, misma sesion persistida, sin FGS).
        val secondVm = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        fixedClock.advanceBy(3_000L)
        advanceLocalTimer(3_000L)

        val secondState = secondVm.state.value
        assertEquals(firstSessionId, secondState.session?.id)
        assertEquals(startTime, secondState.session?.startTime)
        // El total debe sumar al menos 6s derivados del startTime.
        assertTrue(
            secondState.elapsedSeconds >= 6L,
            "expected second VM elapsedSeconds >= 6, was ${secondState.elapsedSeconds}",
        )
    }

    @Test
    fun `local fallback timer stops when foreground service becomes healthy`() = runTest(dispatcher) {
        val viewModel = newStartedViewModel()
        val sessionId = viewModel.session?.id
        assertNotNull(sessionId)
        val startTime = viewModel.session!!.startTime

        // FGS muerto: el collector arranca el job local.
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = sessionId,
                startedAt = startTime,
                running = false,
                failed = true,
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ActiveWorkoutMessage.TimerServiceUnavailable, viewModel.state.value.message)

        // Damos tiempo al job local para que haga un par de ticks.
        fixedClock.advanceBy(2_000L)
        advanceLocalTimer(2_000L)
        val elapsedWhileFallback = viewModel.state.value.elapsedSeconds
        assertTrue(elapsedWhileFallback >= 2L, "fallback elapsed was $elapsedWhileFallback")

        // El FGS se recupera: el registry pasa a running=true publicando un elapsedSeconds
        // arbitrario que NO coincide con lo que diria la formula local tras mas ticks.
        // Asi, si el job local siguiera vivo, sobreescribiria este valor al avanzar el reloj.
        val frozenRegistryElapsed = 99L
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = sessionId,
                startedAt = startTime,
                elapsedSeconds = frozenRegistryElapsed,
                running = true,
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        dispatcher.scheduler.runCurrent()

        // Tras la transicion a healthy: el mensaje desaparece y elapsedSeconds refleja
        // el valor del registry (espejado por el collector), no la formula local.
        val recoveredState = viewModel.state.value
        assertNull(recoveredState.message)
        assertEquals(frozenRegistryElapsed, recoveredState.elapsedSeconds)

        // Si el job local sigue vivo, al avanzar reloj + scheduler recalcularia elapsed
        // y pisaria el valor del registry. Verificamos que NO ocurre: solo el collector
        // es escritor activo.
        fixedClock.advanceBy(10_000L)
        advanceLocalTimer(10_000L)
        assertEquals(
            frozenRegistryElapsed,
            viewModel.state.value.elapsedSeconds,
            "local fallback must not overwrite elapsedSeconds from registry",
        )
    }

    @Test
    fun `elapsed_seconds matches math from startTime`() = runTest(dispatcher) {
        val viewModel = newStartedViewModel()
        val session = viewModel.session
        assertNotNull(session)
        val startTime = session!!.startTime

        // Forzamos una relacion conocida: reloj = startTime + 3000ms.
        fixedClock.reset(startTime + 3_000L)
        advanceLocalTimer(2_500L)

        val state = viewModel.state.value
        val expected = (fixedClock.currentMillis() - startTime) / 1000L
        assertEquals(expected, state.elapsedSeconds)
        // El matematico nunca es negativo ni absurdo.
        assertTrue(state.elapsedSeconds >= 0L)
    }

    /**
     * Avanza el scheduler lo justo para que el job local del cronometro haga un tick
     * completo, leyendo la nueva hora del reloj inyectable. Se usa en los tests del
     * BUG-092 para validar que `elapsedSeconds` se deriva de `now()`.
     */
    private fun advanceLocalTimer(virtualMillis: Long) {
        // Cada tick del job local consume 1000ms virtuales de `delay`. Sumamos uno extra
        // para absorber el tick inmediato que ocurre al despertarse.
        dispatcher.scheduler.advanceTimeBy(virtualMillis + 1_100L)
        dispatcher.scheduler.runCurrent()
    }

    /**
     * Reloj mutable para los tests del BUG-092. Permite avanzar el tiempo de forma
     * determinista mientras el job local del cronometro hace sus ticks.
     */
    private class TestClock(private val initialMillis: Long) {
        private var current: Long = initialMillis

        fun reset(newMillis: Long = initialMillis) {
            current = newMillis
        }

        fun advanceBy(deltaMillis: Long) {
            current += deltaMillis
        }

        fun currentMillis(): Long = current

        fun asNow(): () -> Long = { current }
    }

    private fun newViewModelAndStart(routineId: String = "routine_upper"): ActiveWorkoutUiState {
        val viewModel = newViewModel(routineId)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.state.value
    }

    private fun newStartedViewModel(routineId: String = "routine_upper"): ActiveWorkoutViewModel {
        val viewModel = newViewModel(routineId)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun newViewModel(
        routineId: String = "routine_upper",
        now: () -> Long = fixedClock.asNow(),
    ): ActiveWorkoutViewModel {
        return ActiveWorkoutViewModel(
            savedStateHandle = handleFor(routineId),
            startWorkoutSessionUseCase = startWorkoutSessionUseCase,
            resumeWorkoutSessionUseCase = resumeWorkoutSessionUseCase,
            completeWorkoutSessionUseCase = completeWorkoutSessionUseCase,
            discardWorkoutSessionUseCase = discardWorkoutSessionUseCase,
            workoutRepository = workoutRepository,
            workoutSettingsRepository = workoutSettingsRepository,
            exerciseRepository = exerciseRepository,
            context = NoopContext,
            now = now,
        )
    }

    private fun handleFor(routineId: String): androidx.lifecycle.SavedStateHandle {
        return androidx.lifecycle.SavedStateHandle(mapOf(AppRoute.ActiveWorkout.ROUTINE_ID to routineId))
    }

    private fun upperRoutine() = Routine(
        id = "routine_upper",
        name = "Upper",
        description = null,
        colorTag = "#D32F2F",
        estimatedDurationMin = 19,
        isArchived = false,
        exercises = listOf(
            RoutineExercise("entry_bench", "bench", "Bench press", 3, 8, 80.0, 90, 0),
        ),
    )

    private fun lowerRoutine() = Routine(
        id = "routine_lower",
        name = "Lower",
        description = null,
        colorTag = "#1976D2",
        estimatedDurationMin = 19,
        isArchived = false,
        exercises = listOf(
            RoutineExercise("entry_squat", "squat", "Squat", 3, 8, 100.0, 120, 0),
        ),
    )

    private class FakeRoutineRepository : RoutineRepository {
        var routines = emptyList<Routine>()
        override suspend fun routines(includeArchived: Boolean): List<Routine> = routines
        override suspend fun routine(id: String): Routine? = routines.firstOrNull { it.id == id }
        override suspend fun upsertRoutine(routine: Routine) = Unit
        override suspend fun archiveRoutine(id: String) = Unit
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        var sessions = emptyList<WorkoutSession>()

        override suspend fun createSession(session: WorkoutSession): WorkoutSession {
            sessions = sessions + session
            return session
        }

        override suspend fun session(id: String): WorkoutSession? = sessions.firstOrNull { it.id == id }
        override suspend fun sessions(): List<WorkoutSession> = sessions
        override suspend fun findActiveSession(): WorkoutSession? = sessions.firstOrNull { !it.completed }
        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
        }
        override suspend fun upsertSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = null
        override suspend fun updateSessionCompletion(sessionId: String, endTime: Long, durationSeconds: Int, totalVolumeKg: Double) = Unit
    }

    private class FakeWorkoutSettingsRepository : WorkoutSettingsRepository {
        override suspend fun restTimerFeedbackSettings(): RestTimerFeedbackSettings = RestTimerFeedbackSettings(
            soundEnabled = true,
            vibrationEnabled = true,
        )

        override suspend fun updateRestTimerFeedbackSettings(settings: RestTimerFeedbackSettings) = Unit
    }

    private class FakeExerciseRepository : ExerciseRepository {
        override suspend fun muscleGroups(): List<MuscleGroup> = emptyList()
        override suspend fun exercises(includeArchived: Boolean): List<Exercise> = emptyList()
        override suspend fun upsertCustomExercise(exercise: Exercise) = Unit
        override suspend fun archiveExercise(id: String) = Unit
    }
}

private object NoopContext : android.content.ContextWrapper(null) {
    override fun startService(intent: android.content.Intent): android.content.ComponentName? =
        throw UnsupportedOperationException("NoopContext does not support startService in unit tests")
    override fun stopService(intent: android.content.Intent): Boolean =
        throw UnsupportedOperationException("NoopContext does not support stopService in unit tests")
    override fun checkSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED
}
