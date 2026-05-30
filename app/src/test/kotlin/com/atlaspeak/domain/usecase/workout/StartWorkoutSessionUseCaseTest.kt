package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StartWorkoutSessionUseCaseTest {
    private val routineRepository = FakeRoutineRepository()
    private val workoutRepository = FakeWorkoutRepository()
    private val useCase = StartWorkoutSessionUseCase(
        routineRepository = routineRepository,
        workoutRepository = workoutRepository,
        now = { 1_700_000_000_000L },
    )

    @Test
    fun `start session creates planned workout sets from routine`() = runTest {
        routineRepository.routines = listOf(routine())

        val sessionId = useCase("routine_upper")

        assertNotNull(sessionId)
        val session = workoutRepository.sessions.single()
        assertEquals("routine_upper", session.routineId)
        assertEquals("Upper", session.routineName)
        assertEquals(2, session.exercises.size)
        assertEquals(listOf(1, 2, 3), session.exercises.first().sets.map { it.setNumber })
        assertEquals(listOf(8, 8, 8), session.exercises.first().sets.map { it.plannedReps })
        assertEquals(listOf(80.0, 80.0, 80.0), session.exercises.first().sets.map { it.weightKg })
    }

    @Test
    fun `start session returns null when routine does not exist`() = runTest {
        assertNull(useCase("missing"))
        assertEquals(emptyList<WorkoutSession>(), workoutRepository.sessions)
    }

    private fun routine() = Routine(
        id = "routine_upper",
        name = "Upper",
        description = null,
        colorTag = "#D32F2F",
        estimatedDurationMin = 19,
        isArchived = false,
        exercises = listOf(
            RoutineExercise("entry_bench", "bench", "Bench press", 3, 8, 80.0, 90, 0),
            RoutineExercise("entry_row", "row", "Barbell row", 4, 10, 70.0, 120, 1),
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
        override suspend fun upsertSet(set: com.atlaspeak.domain.model.workout.WorkoutSet) = Unit
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = null
        override suspend fun updateSessionCompletion(sessionId: String, endTime: Long, durationSeconds: Int, totalVolumeKg: Double) = Unit
    }
}
