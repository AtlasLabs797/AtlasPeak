package com.atlaspeak.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.RoutineEntity
import com.atlaspeak.data.db.entity.RoutineExerciseEntity
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExercise
import com.atlaspeak.domain.repository.RoutineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRoutineRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : RoutineRepository {
    override suspend fun routines(includeArchived: Boolean): List<Routine> {
        return database.routineDao().getRoutines(includeArchived).map { entity ->
            entity.toDomain(database.routineDao().getRoutineExercises(entity.id))
        }
    }

    override suspend fun routine(id: String): Routine? {
        val entity = database.routineDao().getRoutine(id) ?: return null
        return entity.toDomain(database.routineDao().getRoutineExercises(entity.id))
    }

    override suspend fun upsertRoutine(routine: Routine) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = database.routineDao().getRoutine(routine.id)
            database.routineDao().upsertRoutine(
                RoutineEntity(
                    id = routine.id,
                    name = routine.name,
                    description = routine.description,
                    colorTag = routine.colorTag,
                    estimatedDurationMin = routine.estimatedDurationMin,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    isArchived = routine.isArchived,
                ),
            )
            database.routineDao().deleteRoutineExercises(routine.id)
            database.routineDao().upsertRoutineExercises(
                routine.exercises.map {
                    RoutineExerciseEntity(
                        id = it.id,
                        routineId = routine.id,
                        exerciseId = it.exerciseId,
                        sets = it.sets,
                        reps = it.reps,
                        weightKg = it.weightKg,
                        restSeconds = it.restSeconds,
                        orderIndex = it.orderIndex,
                        notes = it.notes,
                    )
                },
            )
        }
    }

    override suspend fun archiveRoutine(id: String) {
        database.routineDao().archiveRoutine(id, System.currentTimeMillis())
    }

    private suspend fun RoutineEntity.toDomain(entries: List<RoutineExerciseEntity>): Routine {
        val exerciseNames = database.exerciseDao().getExercises(includeArchived = true).associate {
            it.id to if (isEnglishLocale()) it.nameEn else it.nameEs
        }
        return Routine(
            id = id,
            name = name,
            description = description,
            colorTag = colorTag,
            estimatedDurationMin = estimatedDurationMin ?: 0,
            isArchived = isArchived,
            exercises = entries.map { entry ->
                RoutineExercise(
                    id = entry.id,
                    exerciseId = entry.exerciseId,
                    exerciseName = exerciseNames[entry.exerciseId] ?: entry.exerciseId,
                    sets = entry.sets,
                    reps = entry.reps,
                    weightKg = entry.weightKg,
                    restSeconds = entry.restSeconds,
                    orderIndex = entry.orderIndex,
                    notes = entry.notes,
                )
            },
        )
    }

    private fun isEnglishLocale(): Boolean {
        return context.resources.configuration.locales[0]?.language == "en"
    }
}
