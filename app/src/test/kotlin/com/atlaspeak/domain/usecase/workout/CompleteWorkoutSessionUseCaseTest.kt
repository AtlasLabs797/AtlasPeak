package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompleteWorkoutSessionUseCaseTest {
    private val repository = FakeWorkoutRepository()
    private val useCase = CompleteWorkoutSessionUseCase(repository)

    @Test
    fun `complete session calculates duration volume completed sets and personal records`() = runTest {
        repository.sessions = listOf(session())
        repository.previousMax["bench"] = 75.0

        val summary = useCase("session_1", endedAt = 1_700_000_600_000L)

        assertEquals(600, summary?.durationSeconds)
        assertEquals(1280.0, summary?.totalVolumeKg)
        assertEquals(2, summary?.completedSets)
        assertEquals(3, summary?.totalSets)
        assertEquals(1, summary?.personalRecordCount)
        assertTrue(repository.updatedSets.first { it.id == "set_1" }.isPersonalRecord)
        assertEquals(1280.0, repository.completedTotalVolume)
    }

    @Test
    fun `complete session returns null when session is missing`() = runTest {
        assertNull(useCase("missing", endedAt = 1L))
    }

    private fun session() = WorkoutSession(
        id = "session_1",
        routineId = "routine_upper",
        routineName = "Upper",
        startTime = 1_700_000_000_000L,
        endTime = null,
        durationSeconds = null,
        completed = false,
        totalVolumeKg = null,
        exercises = listOf(
            ActiveWorkoutExercise(
                exerciseId = "bench",
                exerciseName = "Bench press",
                orderIndex = 0,
                restSeconds = 90,
                sets = listOf(
                    WorkoutSet("set_1", "session_1", "bench", "Bench press", 1, 8, 8, 80.0, true, 1_700_000_100_000L, false),
                    WorkoutSet("set_2", "session_1", "bench", "Bench press", 2, 8, 8, 80.0, true, 1_700_000_200_000L, false),
                    WorkoutSet("set_3", "session_1", "bench", "Bench press", 3, 8, null, 80.0, false, null, false),
                ),
            ),
        ),
    )

    private class FakeWorkoutRepository : WorkoutRepository {
        var sessions = emptyList<WorkoutSession>()
        val previousMax = mutableMapOf<String, Double>()
        val updatedSets = mutableListOf<WorkoutSet>()
        var completedTotalVolume: Double? = null

        override suspend fun createSession(session: WorkoutSession): WorkoutSession = session
        override suspend fun session(id: String): WorkoutSession? = sessions.firstOrNull { it.id == id }
        override suspend fun sessions(): List<WorkoutSession> = sessions
        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
        }
        override suspend fun upsertSet(set: WorkoutSet) {
            updatedSets += set
        }
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = previousMax[exerciseId]
        override suspend fun updateSessionCompletion(
            sessionId: String,
            endTime: Long,
            durationSeconds: Int,
            totalVolumeKg: Double,
        ) {
            completedTotalVolume = totalVolumeKg
        }
    }
}
