package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResumeWorkoutSessionUseCaseTest {
    private val workoutRepository = FakeWorkoutRepository()
    private val useCase = ResumeWorkoutSessionUseCase(workoutRepository)

    @Test
    fun `resume returns active session id when one exists`() = runTest {
        workoutRepository.sessions = listOf(
            workout("active", completed = false),
            workout("finished", completed = true),
        )

        assertEquals("active", useCase())
    }

    @Test
    fun `resume returns null when no active session exists`() = runTest {
        workoutRepository.sessions = listOf(workout("finished", completed = true))

        assertNull(useCase())
    }

    @Test
    fun `resume is idempotent and does not create a new session`() = runTest {
        workoutRepository.sessions = listOf(workout("active", completed = false))

        val first = useCase()
        val second = useCase()

        assertEquals("active", first)
        assertEquals("active", second)
        assertEquals(1, workoutRepository.sessions.size)
    }

    private fun workout(id: String, completed: Boolean) = WorkoutSession(
        id = id,
        routineId = "routine",
        routineName = "Routine",
        startTime = 1L,
        endTime = null,
        durationSeconds = null,
        completed = completed,
        totalVolumeKg = null,
        exercises = emptyList(),
    )

    private class FakeWorkoutRepository : WorkoutRepository {
        var sessions = emptyList<WorkoutSession>()
        override suspend fun createSession(session: WorkoutSession): WorkoutSession = session
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
    }
}
