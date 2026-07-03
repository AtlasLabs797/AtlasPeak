package com.atlaspeak.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.data.db.entity.WorkoutSetEntity
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.WorkoutRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkoutRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : WorkoutRepository {
    override suspend fun createSession(session: WorkoutSession): WorkoutSession {
        database.withTransaction {
            database.workoutDao().upsertSession(session.toEntity())
            database.workoutDao().upsertSets(session.exercises.flatMap { exercise ->
                exercise.sets.map { it.toEntity() }
            })
        }
        return session
    }

    override suspend fun session(id: String): WorkoutSession? {
        val entity = database.workoutDao().getStrengthSession(id) ?: return null
        return entity.toDomain(database.workoutDao().getSets(id))
    }

    override suspend fun sessions(): List<WorkoutSession> {
        return database.workoutDao().getStrengthSessions().map { entity ->
            entity.toDomain(database.workoutDao().getSets(entity.id))
        }
    }

    override suspend fun deleteSession(id: String) {
        database.workoutDao().deleteSession(id)
    }

    override suspend fun upsertSet(set: WorkoutSet) {
        database.workoutDao().upsertSet(set.toEntity())
    }

    override suspend fun deleteSet(id: String) {
        database.workoutDao().deleteSet(id)
    }

    override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? {
        return database.workoutDao().maxCompletedWeightBefore(exerciseId, before)
    }

    override suspend fun updateSessionCompletion(
        sessionId: String,
        endTime: Long,
        durationSeconds: Int,
        totalVolumeKg: Double,
    ) {
        database.workoutDao().updateSessionCompletion(sessionId, endTime, durationSeconds, totalVolumeKg)
    }

    private suspend fun WorkoutSessionEntity.toDomain(sets: List<WorkoutSetEntity>): WorkoutSession {
        val exerciseNames = database.exerciseDao().getExercises(includeArchived = true).associate {
            it.id to if (isEnglishLocale()) it.nameEn else it.nameEs
        }
        val routineEntries = routineId?.let { id ->
            database.routineDao().getRoutineExercises(id).associateBy { it.exerciseId }
        }.orEmpty()
        val routineName = routineId?.let { id -> database.routineDao().getRoutine(id)?.name }

        return WorkoutSession(
            id = id,
            routineId = routineId,
            routineName = routineName,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            completed = completed,
            totalVolumeKg = totalVolumeKg,
            exercises = sets.groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
                val routineEntry = routineEntries[exerciseId]
                ActiveWorkoutExercise(
                    exerciseId = exerciseId,
                    exerciseName = exerciseNames[exerciseId] ?: exerciseId,
                    orderIndex = routineEntry?.orderIndex ?: 0,
                    restSeconds = routineEntry?.restSeconds ?: 90,
                    notes = routineEntry?.notes,
                    sets = exerciseSets.sortedBy { it.setNumber }.map { set ->
                        set.toDomain(exerciseNames[exerciseId] ?: exerciseId)
                    },
                )
            }.sortedBy { it.orderIndex },
        )
    }

    private fun WorkoutSession.toEntity() = WorkoutSessionEntity(
        id = id,
        routineId = routineId,
        type = "STRENGTH",
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        notes = null,
        completed = completed,
        caloriesBurned = null,
        totalVolumeKg = totalVolumeKg,
    )

    private fun WorkoutSet.toEntity() = WorkoutSetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        plannedReps = plannedReps,
        actualReps = actualReps,
        weightKg = weightKg,
        completed = completed,
        completedAt = completedAt,
        source = "PHONE",
        isPersonalRecord = isPersonalRecord,
    )

    private fun WorkoutSetEntity.toDomain(exerciseName: String) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        setNumber = setNumber,
        plannedReps = plannedReps,
        actualReps = actualReps,
        weightKg = weightKg,
        completed = completed,
        completedAt = completedAt,
        isPersonalRecord = isPersonalRecord,
    )

    private fun isEnglishLocale(): Boolean {
        return context.resources.configuration.locales[0]?.language == "en"
    }
}
