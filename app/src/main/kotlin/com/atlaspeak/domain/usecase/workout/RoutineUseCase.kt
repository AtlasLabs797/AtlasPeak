package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExercise
import com.atlaspeak.domain.model.workout.RoutineExerciseInput
import com.atlaspeak.domain.repository.RoutineRepository
import java.util.UUID
import javax.inject.Inject

class RoutineUseCase @Inject constructor(
    private val repository: RoutineRepository,
) {
    suspend fun routines(): List<Routine> = repository.routines().sortedByDescending { it.estimatedDurationMin }

    suspend fun routine(id: String): Routine? = repository.routine(id)

    suspend fun createOrUpdateRoutine(
        name: String,
        colorTag: String?,
        exercises: List<RoutineExerciseInput>,
        id: String = UUID.randomUUID().toString(),
    ): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return false
        val duration = estimatedDurationMinutes(exercises)
        repository.upsertRoutine(
            Routine(
                id = id,
                name = trimmedName,
                description = null,
                colorTag = colorTag,
                estimatedDurationMin = duration,
                isArchived = false,
                exercises = exercises.mapIndexed { index, input ->
                    RoutineExercise(
                        id = UUID.randomUUID().toString(),
                        exerciseId = input.exerciseId,
                        exerciseName = input.exerciseId,
                        sets = input.sets,
                        reps = input.reps,
                        weightKg = input.weightKg,
                        restSeconds = input.restSeconds,
                        orderIndex = index,
                        notes = input.notes?.trim()?.takeIf { it.isNotBlank() },
                    )
                },
            ),
        )
        return true
    }

    suspend fun archiveRoutine(id: String) {
        repository.archiveRoutine(id)
    }

    companion object {
        private const val SET_SECONDS = 60
        fun estimatedDurationMinutes(exercises: List<RoutineExerciseInput>): Int {
            val totalSeconds = exercises.sumOf { input ->
                val sets = input.sets.coerceAtLeast(0)
                sets * SET_SECONDS + sets * input.restSeconds.coerceAtLeast(0)
            }
            return totalSeconds / 60
        }
    }
}
