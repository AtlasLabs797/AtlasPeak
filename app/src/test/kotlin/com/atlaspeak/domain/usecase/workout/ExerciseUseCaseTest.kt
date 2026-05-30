package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.repository.ExerciseRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExerciseUseCaseTest {
    private val repository = FakeExerciseRepository()
    private val useCase = ExerciseUseCase(repository)

    @Test
    fun `library filters by search query and muscle group`() = runTest {
        repository.exercises = listOf(
            exercise(id = "bench", name = "Bench press", muscleGroupId = 1),
            exercise(id = "row", name = "Barbell row", muscleGroupId = 2),
            exercise(id = "pushup", name = "Push-up", muscleGroupId = 1),
        )

        val result = useCase.library(query = "press", muscleGroupId = 1)

        assertEquals(listOf("bench"), result.map { it.id })
    }

    @Test
    fun `library muscle group filter includes secondary muscle group`() = runTest {
        repository.exercises = listOf(
            exercise(id = "bench", name = "Bench press", muscleGroupId = 1, secondaryMuscleGroupId = 6),
            exercise(id = "row", name = "Barbell row", muscleGroupId = 2),
        )

        val result = useCase.library(query = "", muscleGroupId = 6)

        assertEquals(listOf("bench"), result.map { it.id })
    }

    @Test
    fun `create custom exercise rejects blank names and archives by soft delete`() = runTest {
        assertFalse(useCase.createOrUpdateCustomExercise(name = "", muscleGroupId = 1))

        assertTrue(useCase.createOrUpdateCustomExercise(name = "Seal row", muscleGroupId = 2))
        val created = repository.exercises.single()
        assertFalse(created.isPreset)
        assertFalse(created.isArchived)

        useCase.archiveExercise(created.id)

        assertTrue(repository.exercises.single().isArchived)
    }

    @Test
    fun `create custom exercise updates existing custom exercise by id`() = runTest {
        assertTrue(useCase.createOrUpdateCustomExercise(name = "Seal row", muscleGroupId = 2, id = "custom_seal_row"))
        assertTrue(useCase.createOrUpdateCustomExercise(name = "Chest row", muscleGroupId = 1, id = "custom_seal_row"))

        val updated = repository.exercises.single()
        assertEquals("Chest row", updated.name)
        assertEquals(1, updated.muscleGroup.id)
    }

    @Test
    fun `create custom exercise refuses to overwrite presets`() = runTest {
        repository.exercises = listOf(exercise(id = "preset_bench", name = "Bench press", muscleGroupId = 1))

        assertFalse(useCase.createOrUpdateCustomExercise(name = "Hijacked", muscleGroupId = 2, id = "preset_bench"))

        val preset = repository.exercises.single()
        assertTrue(preset.isPreset)
        assertEquals("Bench press", preset.name)
        assertEquals(1, preset.muscleGroup.id)
    }

    private fun exercise(
        id: String,
        name: String,
        muscleGroupId: Int,
        secondaryMuscleGroupId: Int? = null,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = MuscleGroup(muscleGroupId, "Group $muscleGroupId"),
        secondaryMuscleGroup = secondaryMuscleGroupId?.let { MuscleGroup(it, "Group $it") },
        description = null,
        isPreset = true,
        isArchived = false,
    )

    private class FakeExerciseRepository : ExerciseRepository {
        var exercises = emptyList<Exercise>()
        override suspend fun muscleGroups(): List<MuscleGroup> = listOf(
            MuscleGroup(1, "Chest"),
            MuscleGroup(2, "Back"),
            MuscleGroup(6, "Triceps"),
        )
        override suspend fun exercises(includeArchived: Boolean): List<Exercise> {
            return if (includeArchived) exercises else exercises.filterNot { it.isArchived }
        }

        override suspend fun upsertCustomExercise(exercise: Exercise) {
            exercises = exercises.filterNot { it.id == exercise.id } + exercise
        }

        override suspend fun archiveExercise(id: String) {
            exercises = exercises.map { if (it.id == id) it.copy(isArchived = true) else it }
        }
    }
}
