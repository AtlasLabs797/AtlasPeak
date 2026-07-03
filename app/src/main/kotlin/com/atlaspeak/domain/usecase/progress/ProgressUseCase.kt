package com.atlaspeak.domain.usecase.progress

import com.atlaspeak.domain.model.progress.ExerciseProgress
import com.atlaspeak.domain.model.progress.ExerciseProgressPoint
import com.atlaspeak.domain.model.progress.MuscleGroupProgress
import com.atlaspeak.domain.model.progress.ProgressHistoryFilter
import com.atlaspeak.domain.model.progress.ProgressHistoryItem
import com.atlaspeak.domain.model.progress.ProgressHistoryType
import com.atlaspeak.domain.model.progress.ProgressPeriod
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.completedVolumeKg
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

class ProgressUseCase(
    private val workoutRepository: WorkoutRepository,
    private val cardioRepository: CardioRepository,
    private val exerciseRepository: ExerciseRepository,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        workoutRepository: WorkoutRepository,
        cardioRepository: CardioRepository,
        exerciseRepository: ExerciseRepository,
    ) : this(
        workoutRepository = workoutRepository,
        cardioRepository = cardioRepository,
        exerciseRepository = exerciseRepository,
        now = { System.currentTimeMillis() },
    )

    suspend fun history(
        query: String,
        filter: ProgressHistoryFilter,
    ): List<ProgressHistoryItem> {
        val cutoff = filter.period.cutoffMillis(now())
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val strength = if (filter.type == ProgressHistoryType.All || filter.type == ProgressHistoryType.Strength) {
            workoutRepository.sessions()
                .filter { it.completed && it.startTime >= cutoff && it.hasStrengthWork() }
                .map { ProgressHistoryItem.Strength(it) }
        } else {
            emptyList()
        }
        val cardio = if (filter.type == ProgressHistoryType.All || filter.type == ProgressHistoryType.Cardio) {
            cardioRepository.sessions()
                .filter { it.completed && it.startTime >= cutoff }
                .map { ProgressHistoryItem.Cardio(it) }
        } else {
            emptyList()
        }
        return (strength + cardio)
            .filter { item ->
                normalizedQuery.isBlank() ||
                    item.searchText.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .sortedByDescending { it.startedAt }
    }

    suspend fun exerciseProgress(period: ProgressPeriod): List<ExerciseProgress> {
        val cutoff = period.cutoffMillis(now())
        val exerciseById = exerciseRepository.exercises(includeArchived = true).associateBy { it.id }
        return workoutRepository.sessions()
            .filter { it.completed && it.startTime >= cutoff && it.hasStrengthWork() }
            .sortedBy { it.startTime }
            .flatMap { session -> session.toExercisePoints(exerciseById) }
            .groupBy { it.first.id }
            .map { (_, entries) ->
                ExerciseProgress(
                    exercise = entries.first().first,
                    points = entries.map { it.second }.sortedBy { it.startedAt },
                )
            }
            .filter { it.points.isNotEmpty() }
            .sortedBy { it.exercise.name.lowercase(Locale.ROOT) }
    }

    suspend fun muscleGroupProgress(period: ProgressPeriod): List<MuscleGroupProgress> {
        val exercises = exerciseProgress(period)
        val groups = (exerciseRepository.muscleGroups() + exercises.flatMap {
            listOfNotNull(it.exercise.muscleGroup, it.exercise.secondaryMuscleGroup)
        }).distinctBy { it.id }

        return groups.mapNotNull { group ->
            val groupExercises = exercises
                .filter { progress ->
                    progress.exercise.muscleGroup.id == group.id ||
                        progress.exercise.secondaryMuscleGroup?.id == group.id
                }
                .sortedBy { it.exercise.name.lowercase(Locale.ROOT) }
            if (groupExercises.isEmpty()) {
                null
            } else {
                MuscleGroupProgress(group, groupExercises)
            }
        }.sortedBy { it.muscleGroup.name.lowercase(Locale.ROOT) }
    }

    private fun WorkoutSession.toExercisePoints(
        exerciseById: Map<String, Exercise>,
    ): List<Pair<Exercise, ExerciseProgressPoint>> {
        return exercises
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sessionExercises) ->
                val completedSets = sessionExercises.flatMap(ActiveWorkoutExercise::sets)
                    .filter { it.completed }
                if (completedSets.isEmpty()) return@mapNotNull null
                val exercise = exerciseById[exerciseId] ?: sessionExercises.first().toFallbackExercise()
                exercise to ExerciseProgressPoint(
                    sessionId = id,
                    startedAt = startTime,
                    maxWeightKg = completedSets.mapNotNull { it.weightKg }.maxOrNull(),
                    volumeKg = completedSets.sumOf { it.completedVolumeKg() },
                    reps = completedSets.sumOf { it.actualReps ?: it.plannedReps },
                )
            }
    }

    private fun WorkoutSession.hasStrengthWork(): Boolean {
        return exercises.any { exercise -> exercise.sets.any { it.completed } }
    }

    private fun ActiveWorkoutExercise.toFallbackExercise() = Exercise(
        id = exerciseId,
        name = exerciseName,
        muscleGroup = MuscleGroup(id = UNKNOWN_GROUP_ID, name = ""),
        secondaryMuscleGroup = null,
        description = null,
        isPreset = false,
        isArchived = false,
    )

    private fun ProgressPeriod.cutoffMillis(nowMillis: Long): Long {
        return when (this) {
            ProgressPeriod.Week -> nowMillis - WEEK_MILLIS
            ProgressPeriod.Month -> nowMillis - MONTH_MILLIS
            ProgressPeriod.ThreeMonths -> nowMillis - THREE_MONTHS_MILLIS
            ProgressPeriod.Year -> nowMillis - YEAR_MILLIS
            ProgressPeriod.YearToDate -> {
                val zone = ZoneId.systemDefault()
                val startOfYear = Instant.ofEpochMilli(nowMillis)
                    .atZone(zone)
                    .toLocalDate()
                    .withDayOfYear(1)
                    .atStartOfDay(zone)
                startOfYear.toInstant().toEpochMilli()
            }
        }
    }

    private companion object {
        const val UNKNOWN_GROUP_ID = -1
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        const val WEEK_MILLIS = 7L * DAY_MILLIS
        const val MONTH_MILLIS = 30L * DAY_MILLIS
        const val THREE_MONTHS_MILLIS = 90L * DAY_MILLIS
        const val YEAR_MILLIS = 365L * DAY_MILLIS
    }
}
