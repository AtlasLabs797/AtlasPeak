package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        routineRepository.routines = listOf(routine("routine_upper", "Upper"))

        val result = useCase("routine_upper")

        val sessionId = assertInstanceOf(ActiveSessionStartResult.Started::class.java, result).sessionId
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
    fun `start session keeps weekly plan row id`() = runTest {
        routineRepository.routines = listOf(routine("routine_upper", "Upper"))

        useCase("routine_upper", weeklyPlanSessionId = "weekly_plan_1")

        assertEquals("weekly_plan_1", workoutRepository.sessions.single().weeklyPlanSessionId)
    }

    @Test
    fun `start session returns NotFound when routine does not exist`() = runTest {
        assertInstanceOf(ActiveSessionStartResult.NotFound::class.java, useCase("missing"))
        assertEquals(emptyList<WorkoutSession>(), workoutRepository.sessions)
    }

    @Test
    fun `start session returns Resumed when active session exists for same routine`() = runTest {
        routineRepository.routines = listOf(routine("routine_upper", "Upper"))
        workoutRepository.sessions = listOf(
            FakeWorkoutRepository.session(id = "existing", routineId = "routine_upper", completed = false),
        )

        val result = useCase("routine_upper")

        val resumed = assertInstanceOf(ActiveSessionStartResult.Resumed::class.java, result)
        assertEquals("existing", resumed.sessionId)
        // No new session should be created on resume.
        assertEquals(1, workoutRepository.sessions.size)
    }

    @Test
    fun `start session returns Conflict when active session exists for different routine`() = runTest {
        routineRepository.routines = listOf(
            routine("routine_upper", "Upper"),
            routine("routine_lower", "Lower"),
        )
        workoutRepository.sessions = listOf(
            FakeWorkoutRepository.session(id = "existing", routineId = "routine_lower", completed = false),
        )

        val result = useCase("routine_upper")

        val conflict = assertInstanceOf(ActiveSessionStartResult.Conflict::class.java, result)
        assertEquals("existing", conflict.sessionId)
        // Active session must NOT be silently overwritten.
        assertEquals(1, workoutRepository.sessions.size)
        assertEquals("routine_lower", workoutRepository.sessions.single().routineId)
    }

    @Test
    fun `start session does not treat completed sessions as active`() = runTest {
        routineRepository.routines = listOf(routine("routine_upper", "Upper"))
        workoutRepository.sessions = listOf(
            FakeWorkoutRepository.session(id = "finished", routineId = "routine_upper", completed = true),
        )

        val result = useCase("routine_upper")

        val started = assertInstanceOf(ActiveSessionStartResult.Started::class.java, result)
        assertNotEquals("finished", started.sessionId)
        assertEquals(2, workoutRepository.sessions.size)
    }

    @Test
    fun `process recreation preserves active session and does not duplicate it`() = runTest {
        routineRepository.routines = listOf(routine("routine_upper", "Upper"))

        val first = useCase("routine_upper") as ActiveSessionStartResult.Started
        // The user completes two sets, then the process is killed and recreated.
        val session = workoutRepository.sessions.single()
        workoutRepository.sessions = listOf(
            session.copy(
                exercises = session.exercises.map { ex ->
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
            ),
        )

        val second = useCase("routine_upper")

        val resumed = assertInstanceOf(ActiveSessionStartResult.Resumed::class.java, second)
        assertEquals(first.sessionId, resumed.sessionId)
        // Exactly one session, not two.
        assertEquals(1, workoutRepository.sessions.size)
        // The completed sets are preserved.
        val reloaded = workoutRepository.session(first.sessionId)!!
        assertTrue(reloaded.exercises.first().sets[0].completed)
        assertTrue(reloaded.exercises.first().sets[1].completed)
        assertFalse(reloaded.exercises.first().sets[2].completed)
    }

    private fun routine(id: String, name: String) = Routine(
        id = id,
        name = name,
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
        override suspend fun findActiveSession(): WorkoutSession? = sessions.firstOrNull { !it.completed }
        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
        }
        override suspend fun upsertSet(set: com.atlaspeak.domain.model.workout.WorkoutSet) = Unit
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = null
        override suspend fun updateSessionCompletion(sessionId: String, endTime: Long, durationSeconds: Int, totalVolumeKg: Double) = Unit

        companion object {
            fun session(id: String, routineId: String, completed: Boolean): WorkoutSession = WorkoutSession(
                id = id,
                routineId = routineId,
                routineName = "Routine $routineId",
                startTime = 1_700_000_000_000L,
                endTime = null,
                durationSeconds = null,
                completed = completed,
                totalVolumeKg = null,
                exercises = emptyList(),
            )
        }
    }

    @Test
    fun `start session returns null-like result on missing routine without touching repo`() = runTest {
        // Garantiza que un NotFound no deja sesion huerfana.
        val result = useCase("never_existed")
        assertInstanceOf(ActiveSessionStartResult.NotFound::class.java, result)
        assertNull(workoutRepository.sessions.firstOrNull { !it.completed })
    }
}
