package com.atlaspeak.domain.model.progress

import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.WorkoutSession

enum class ProgressPeriod {
    Week,
    Month,
    ThreeMonths,
    Year,
    YearToDate,
}

enum class ProgressHistoryType {
    All,
    Strength,
    Cardio,
}

data class ProgressHistoryFilter(
    val type: ProgressHistoryType = ProgressHistoryType.All,
    val period: ProgressPeriod = ProgressPeriod.Month,
)

sealed class ProgressHistoryItem {
    abstract val id: String
    abstract val startedAt: Long
    abstract val durationSeconds: Int?
    abstract val title: String?
    abstract val searchText: String

    data class Strength(val session: WorkoutSession) : ProgressHistoryItem() {
        override val id: String = session.id
        override val startedAt: Long = session.startTime
        override val durationSeconds: Int? = session.durationSeconds
        override val title: String? = session.routineName
            ?: session.exercises.firstOrNull()?.exerciseName
        override val searchText: String = buildString {
            append(session.routineName.orEmpty())
            append(' ')
            append(session.exercises.joinToString(" ") { exercise -> exercise.exerciseName })
        }
    }

    data class Cardio(val session: CardioSession) : ProgressHistoryItem() {
        override val id: String = session.id
        override val startedAt: Long = session.startTime
        override val durationSeconds: Int? = session.durationSeconds
        override val title: String? = session.cardioTypeName
        override val searchText: String = session.cardioTypeName
    }
}

data class ExerciseProgressPoint(
    val sessionId: String,
    val startedAt: Long,
    val maxWeightKg: Double?,
    val volumeKg: Double,
    val reps: Int,
)

data class ExerciseProgress(
    val exercise: Exercise,
    val points: List<ExerciseProgressPoint>,
) {
    val latestMaxWeightKg: Double? = points.lastOrNull()?.maxWeightKg
    val latestVolumeKg: Double = points.lastOrNull()?.volumeKg ?: 0.0
    val totalVolumeKg: Double = points.sumOf { it.volumeKg }
}

data class MuscleGroupProgress(
    val muscleGroup: MuscleGroup,
    val exercises: List<ExerciseProgress>,
) {
    val totalVolumeKg: Double = exercises.sumOf { it.totalVolumeKg }
}
