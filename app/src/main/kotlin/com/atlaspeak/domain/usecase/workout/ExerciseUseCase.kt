package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.repository.ExerciseRepository
import java.util.UUID
import javax.inject.Inject

class ExerciseUseCase @Inject constructor(
    private val repository: ExerciseRepository,
) {
    suspend fun muscleGroups(): List<MuscleGroup> = repository.muscleGroups()

    suspend fun library(query: String = "", muscleGroupId: Int? = null): List<Exercise> {
        val normalizedQuery = query.trim()
        return repository.exercises()
            .filter { exercise -> exercise.matchesMuscleGroup(muscleGroupId) }
            .filter { exercise -> normalizedQuery.isBlank() || exercise.name.contains(normalizedQuery, ignoreCase = true) }
            .sortedWith(compareBy<Exercise> { it.muscleGroup.id }.thenBy { it.name })
    }

    suspend fun createOrUpdateCustomExercise(
        name: String,
        muscleGroupId: Int,
        secondaryMuscleGroupId: Int? = null,
        description: String? = null,
        id: String = UUID.randomUUID().toString(),
    ): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return false
        val groups = repository.muscleGroups()
        val group = groups.firstOrNull { it.id == muscleGroupId } ?: return false
        val secondary = groups.firstOrNull { it.id == secondaryMuscleGroupId }
        val existing = repository.exercises(includeArchived = true).firstOrNull { it.id == id }
        if (existing?.isPreset == true) return false
        repository.upsertCustomExercise(
            Exercise(
                id = id,
                name = trimmedName,
                muscleGroup = group,
                secondaryMuscleGroup = secondary,
                description = description?.trim()?.ifBlank { null },
                isPreset = false,
                isArchived = false,
            ),
        )
        return true
    }

    suspend fun archiveExercise(id: String) {
        repository.archiveExercise(id)
    }

    private fun Exercise.matchesMuscleGroup(muscleGroupId: Int?): Boolean {
        return muscleGroupId == null ||
            muscleGroup.id == muscleGroupId ||
            secondaryMuscleGroup?.id == muscleGroupId
    }
}
