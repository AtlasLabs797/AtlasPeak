package com.atlaspeak.domain.model.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkoutVolumeTest {
    @Test
    fun `completed volume counts only completed sets with actual reps taking precedence`() {
        val session = WorkoutSession(
            id = "session_1",
            routineId = "routine_1",
            routineName = "Upper",
            startTime = 1L,
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
                        WorkoutSet("set_1", "session_1", "bench", "Bench press", 1, 10, 8, 10.0, true, 10L, false),
                        WorkoutSet("set_2", "session_1", "bench", "Bench press", 2, 5, null, 12.0, true, 20L, false),
                        WorkoutSet("set_3", "session_1", "bench", "Bench press", 3, 20, 20, 100.0, false, null, false),
                        WorkoutSet("set_4", "session_1", "bench", "Bench press", 4, 10, 10, null, true, 30L, false),
                    ),
                ),
            ),
        )

        assertEquals(140.0, session.completedVolumeKg())
    }
}
