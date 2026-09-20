package com.atlaspeak.presentation.progress

import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.progress.ProgressPeriod
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.domain.usecase.progress.ProgressUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_700_000_000_000L

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid period changes keep latest period results`() = runTest {
        val workoutRepository = FakeWorkoutRepository(
            sessions = listOf(
                workoutSession(id = "recent", startTime = now - DAYS_2),
                workoutSession(id = "old", startTime = now - DAYS_40),
            ),
        )
        val viewModel = viewModel(workoutRepository)
        dispatcher.scheduler.advanceUntilIdle()

        workoutRepository.sessionDelays += 1_000L
        viewModel.selectPeriod(ProgressPeriod.Week)
        dispatcher.scheduler.runCurrent()
        viewModel.selectPeriod(ProgressPeriod.Year)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProgressPeriod.Year, viewModel.state.value.selectedPeriod)
        assertEquals(listOf("recent", "old"), viewModel.state.value.history.map { it.id })
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `load failure becomes ui error instead of crashing`() = runTest {
        val workoutRepository = FakeWorkoutRepository(failSessions = true)
        val viewModel = viewModel(workoutRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.error_generic, viewModel.state.value.errorMessageRes)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `selecting same period does not reload`() = runTest {
        val workoutRepository = FakeWorkoutRepository(
            sessions = listOf(workoutSession(id = "recent", startTime = now - DAYS_2)),
        )
        val viewModel = viewModel(workoutRepository)
        dispatcher.scheduler.advanceUntilIdle()
        val sessionCalls = workoutRepository.sessionCalls

        viewModel.selectPeriod(ProgressPeriod.Month)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(sessionCalls, workoutRepository.sessionCalls)
    }

    private fun viewModel(workoutRepository: FakeWorkoutRepository): ProgressViewModel {
        return ProgressViewModel(
            progressUseCase = ProgressUseCase(
                workoutRepository = workoutRepository,
                cardioRepository = FakeCardioRepository(),
                exerciseRepository = FakeExerciseRepository(),
                now = { now },
            ),
        )
    }

    private fun workoutSession(
        id: String,
        startTime: Long,
    ) = WorkoutSession(
        id = id,
        routineId = "routine-$id",
        routineName = "Routine $id",
        startTime = startTime,
        endTime = startTime + 1_800_000L,
        durationSeconds = 1_800,
        completed = true,
        totalVolumeKg = 500.0,
        exercises = listOf(
            ActiveWorkoutExercise(
                exerciseId = "bench",
                exerciseName = "Bench press",
                orderIndex = 0,
                restSeconds = 90,
                sets = listOf(workoutSet(sessionId = id, startTime = startTime)),
            ),
        ),
    )

    private fun workoutSet(
        sessionId: String,
        startTime: Long,
    ) = WorkoutSet(
        id = "$sessionId-bench-1",
        sessionId = sessionId,
        exerciseId = "bench",
        exerciseName = "Bench press",
        setNumber = 1,
        plannedReps = 5,
        actualReps = 5,
        weightKg = 100.0,
        completed = true,
        completedAt = startTime + 1_000L,
        isPersonalRecord = false,
    )

    private class FakeWorkoutRepository(
        var sessions: List<WorkoutSession> = emptyList(),
        private val failSessions: Boolean = false,
    ) : WorkoutRepository {
        val sessionDelays = ArrayDeque<Long>()
        var sessionCalls = 0

        override suspend fun createSession(session: WorkoutSession): WorkoutSession = session
        override suspend fun session(id: String): WorkoutSession? = sessions.firstOrNull { it.id == id }

        override suspend fun sessions(): List<WorkoutSession> {
            sessionCalls += 1
            sessionDelays.removeFirstOrNull()?.let { delayMillis ->
                delay(delayMillis)
            }
            if (failSessions) error("boom")
            return sessions
        }

        override suspend fun findActiveSession(): WorkoutSession? = sessions.firstOrNull { !it.completed }

        override suspend fun deleteSession(id: String) = Unit
        override suspend fun upsertSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = null
        override suspend fun updateSessionCompletion(
            sessionId: String,
            endTime: Long,
            durationSeconds: Int,
            totalVolumeKg: Double,
        ) = Unit
    }

    private class FakeCardioRepository : CardioRepository {
        override suspend fun cardioTypes(includeArchived: Boolean): List<CardioType> = emptyList()
        override suspend fun upsertCustomType(type: CardioType) = Unit
        override suspend fun archiveType(id: String) = Unit
        override suspend fun createSession(session: CardioSession): CardioSession = session
        override suspend fun session(id: String): CardioSession? = null
        override suspend fun sessions(): List<CardioSession> = emptyList()
        override suspend fun findActiveSession(): CardioSession? = null
        override suspend fun updateSession(session: CardioSession) = Unit
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun addRoutePoint(point: com.atlaspeak.domain.model.cardio.CardioRoutePoint) = Unit
        override suspend fun routePoints(sessionId: String): List<com.atlaspeak.domain.model.cardio.CardioRoutePoint> = emptyList()
        override suspend fun routePointsCount(sessionId: String): Int = 0
        override suspend fun routeDistanceKm(sessionId: String): Double = 0.0
        override suspend fun deleteRoutePoints(sessionId: String) = Unit
        override suspend fun finalizeCardioSessionRoute(session: CardioSession) = Unit
    }

    private class FakeExerciseRepository : ExerciseRepository {
        override suspend fun muscleGroups(): List<MuscleGroup> = emptyList()
        override suspend fun exercises(includeArchived: Boolean): List<Exercise> = emptyList()
        override suspend fun upsertCustomExercise(exercise: Exercise) = Unit
        override suspend fun archiveExercise(id: String) = Unit
    }

    private companion object {
        const val DAYS_2 = 2L * 24L * 60L * 60L * 1_000L
        const val DAYS_40 = 40L * 24L * 60L * 60L * 1_000L
    }
}
