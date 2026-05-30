package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup

interface ExerciseRepository {
    suspend fun muscleGroups(): List<MuscleGroup>
    suspend fun exercises(includeArchived: Boolean = false): List<Exercise>
    suspend fun upsertCustomExercise(exercise: Exercise)
    suspend fun archiveExercise(id: String)
}
