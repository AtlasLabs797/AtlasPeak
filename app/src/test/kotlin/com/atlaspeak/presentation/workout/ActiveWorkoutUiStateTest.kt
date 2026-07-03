package com.atlaspeak.presentation.workout

import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActiveWorkoutUiStateTest {
    @Test
    fun `state reports exercise completion progress and live volume`() {
        val state = ActiveWorkoutUiState(
            session = WorkoutSession(
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
                            WorkoutSet("set_1", "session_1", "bench", "Bench press", 1, 10, 10, 15.0, true, 10L, false),
                        ),
                    ),
                    ActiveWorkoutExercise(
                        exerciseId = "row",
                        exerciseName = "Row",
                        orderIndex = 1,
                        restSeconds = 90,
                        sets = listOf(
                            WorkoutSet("set_2", "session_1", "row", "Row", 1, 10, 10, 20.0, true, 20L, false),
                            WorkoutSet("set_3", "session_1", "row", "Row", 2, 10, null, 20.0, false, null, false),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, state.completedExerciseCount)
        assertEquals(2, state.totalExerciseCount)
        assertEquals(0.5f, state.exerciseCompletionProgress)
        assertEquals(350.0, state.liveTotalVolumeKg)
    }
}
