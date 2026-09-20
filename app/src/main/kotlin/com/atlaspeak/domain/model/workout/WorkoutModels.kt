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
    val notes: String? = null,
)

data class RoutineExerciseInput(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val restSeconds: Int,
    val notes: String? = null,
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

data class WorkoutSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val plannedReps: Int,
    val actualReps: Int?,
    val weightKg: Double?,
    val completed: Boolean,
    val completedAt: Long?,
    val isPersonalRecord: Boolean,
)

data class ActiveWorkoutExercise(
    val exerciseId: String,
    val exerciseName: String,
    val orderIndex: Int,
    val restSeconds: Int,
    val sets: List<WorkoutSet>,
    val notes: String? = null,
)

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Int?,
    val completed: Boolean,
    val totalVolumeKg: Double?,
    val exercises: List<ActiveWorkoutExercise>,
    // BUG-097 (Fase 8 P1): si la sesion se origino desde una entrada del
    // plan semanal, este campo guarda el id de esa entrada. Cuando es no-null,
    // la regla "completar" del plan mira especificamente a esta entrada.
    val weeklyPlanSessionId: String? = null,
)

data class WorkoutSummary(
    val sessionId: String,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val completedSets: Int,
    val totalSets: Int,
    val personalRecordCount: Int,
)

data class RestTimerFeedbackSettings(
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
)

fun WorkoutSession.completedVolumeKg(): Double {
    return exercises.sumOf { exercise -> exercise.completedVolumeKg() }
}

fun ActiveWorkoutExercise.completedVolumeKg(): Double {
    return sets.sumOf { set -> set.completedVolumeKg() }
}

fun WorkoutSet.completedVolumeKg(): Double {
    if (!completed) return 0.0
    return (actualReps ?: plannedReps) * (weightKg ?: 0.0)
}
