package com.atlaspeak.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.ExerciseEntity
import com.atlaspeak.data.db.entity.MuscleGroupEntity
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.repository.ExerciseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : ExerciseRepository {
    override suspend fun muscleGroups(): List<MuscleGroup> {
        return database.referenceDao().getMuscleGroups().map { it.toDomain() }
    }

    override suspend fun exercises(includeArchived: Boolean): List<Exercise> {
        val groups = database.referenceDao().getMuscleGroups().associate { it.id to it.toDomain() }
        return database.exerciseDao().getExercises(includeArchived).mapNotNull { entity ->
            val group = groups[entity.muscleGroupId] ?: return@mapNotNull null
            entity.toDomain(group, groups[entity.secondaryMuscleGroupId])
        }
    }

    override suspend fun upsertCustomExercise(exercise: Exercise) {
        val existing = database.exerciseDao().getExercise(exercise.id)
        if (existing?.isPreset == true) return
        database.exerciseDao().upsertExercise(
            ExerciseEntity(
                id = exercise.id,
                nameEs = exercise.name,
                nameEn = exercise.name,
                muscleGroupId = exercise.muscleGroup.id,
                secondaryMuscleGroupId = exercise.secondaryMuscleGroup?.id,
                descriptionEs = exercise.description,
                descriptionEn = exercise.description,
                isPreset = false,
                isArchived = exercise.isArchived,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun archiveExercise(id: String) {
        database.exerciseDao().archiveCustomExercise(id)
    }

    private fun ExerciseEntity.toDomain(
        group: MuscleGroup,
        secondaryGroup: MuscleGroup?,
    ) = Exercise(
        id = id,
        name = if (isEnglishLocale()) nameEn else nameEs,
        muscleGroup = group,
        secondaryMuscleGroup = secondaryGroup,
        description = if (isEnglishLocale()) descriptionEn else descriptionEs,
        isPreset = isPreset,
        isArchived = isArchived,
    )

    private fun MuscleGroupEntity.toDomain() = MuscleGroup(
        id = id,
        name = if (isEnglishLocale()) nameEn else nameEs,
    )

    private fun isEnglishLocale(): Boolean {
        return context.resources.configuration.locales[0]?.language == "en"
    }
}
