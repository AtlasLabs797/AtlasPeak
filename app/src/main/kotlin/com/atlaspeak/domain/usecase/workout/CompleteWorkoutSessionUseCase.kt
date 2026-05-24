package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.WorkoutSummary
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.WorkoutRepository
import javax.inject.Inject

class CompleteWorkoutSessionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        endedAt: Long = System.currentTimeMillis(),
    ): WorkoutSummary? {
        val session = workoutRepository.session(sessionId) ?: return null
        val sets = session.exercises.flatMap { it.sets }
        val completedSets = sets.filter { it.completed }
        var personalRecords = 0
        val maxSeenByExercise = mutableMapOf<String, Double?>()

        completedSets.sortedWith(compareBy<WorkoutSet> { it.exerciseId }.thenBy { it.setNumber }).forEach { set ->
            val currentMax = maxSeenByExercise.getOrPut(set.exerciseId) {
                workoutRepository.maxCompletedWeightBefore(set.exerciseId, session.startTime)
            }
            val isPr = set.isNewPersonalRecord(currentMax)
            if (isPr) personalRecords += 1
            if (set.isPersonalRecord != isPr) {
                workoutRepository.upsertSet(set.copy(isPersonalRecord = isPr))
            }
            val weight = set.weightKg
            if (weight != null && (currentMax == null || weight > currentMax)) {
                maxSeenByExercise[set.exerciseId] = weight
            }
        }

        val totalVolumeKg = completedSets.sumOf { set ->
            (set.actualReps ?: set.plannedReps).coerceAtLeast(0) * (set.weightKg ?: 0.0)
        }
        val durationSeconds = ((endedAt - session.startTime) / 1000).coerceAtLeast(0).toInt()
        workoutRepository.updateSessionCompletion(
            sessionId = sessionId,
            endTime = endedAt,
            durationSeconds = durationSeconds,
            totalVolumeKg = totalVolumeKg,
        )
        return WorkoutSummary(
            sessionId = sessionId,
            durationSeconds = durationSeconds,
            totalVolumeKg = totalVolumeKg,
            completedSets = completedSets.size,
            totalSets = sets.size,
            personalRecordCount = personalRecords,
        )
    }

    private fun WorkoutSet.isNewPersonalRecord(previousMax: Double?): Boolean {
        val weight = weightKg ?: return false
        if (!completed) return false
        return previousMax == null || weight > previousMax
    }
}
