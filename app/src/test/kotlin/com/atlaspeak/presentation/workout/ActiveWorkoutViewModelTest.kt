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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        routineRepository.routines = listOf(upperRoutine(), lowerRoutine())
        WorkoutTimerRegistry.update(com.atlaspeak.service.WorkoutTimerState())
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

    private fun newViewModelAndStart(routineId: String = "routine_upper"): ActiveWorkoutUiState {
        val viewModel = newViewModel(routineId)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.state.value
    }

    private fun newViewModel(routineId: String = "routine_upper"): ActiveWorkoutViewModel {
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
