package com.atlaspeak.domain.model.workout

data class MuscleGroup(
    val id: Int,
    val name: String,
)

data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val secondaryMuscleGroup: MuscleGroup?,
    val description: String?,
    val isPreset: Boolean,
    val isArchived: Boolean,
)

data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val restSeconds: Int,
    val orderIndex: Int,
)

data class RoutineExerciseInput(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val restSeconds: Int,
)

data class Routine(
    val id: String,
    val name: String,
    val description: String?,
    val colorTag: String?,
    val estimatedDurationMin: Int,
    val isArchived: Boolean,
    val exercises: List<RoutineExercise>,
)
