package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.RoutineExerciseInput
import com.atlaspeak.domain.repository.RoutineRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutineUseCaseTest {
    private val repository = FakeRoutineRepository()
    private val useCase = RoutineUseCase(repository)

    @Test
    fun `estimated duration includes set time and rests`() {
        val duration = RoutineUseCase.estimatedDurationMinutes(
            listOf(
                RoutineExerciseInput("bench", sets = 3, reps = 8, weightKg = 80.0, restSeconds = 90),
                RoutineExerciseInput("row", sets = 4, reps = 10, weightKg = 70.0, restSeconds = 120),
            ),
        )

        assertEquals(19, duration)
    }

    @Test
    fun `create routine rejects blank name and stores ordered exercises`() = runTest {
        assertFalse(useCase.createOrUpdateRoutine(name = "", colorTag = null, exercises = emptyList()))

        assertTrue(
            useCase.createOrUpdateRoutine(
                name = "Upper",
                colorTag = "#E53935",
                exercises = listOf(
                    RoutineExerciseInput("row", 4, 10, 70.0, 120),
                    RoutineExerciseInput("bench", 3, 8, 80.0, 90),
                ),
            ),
        )

        val routine = repository.routines.single()
        assertEquals("Upper", routine.name)
        assertEquals(2, routine.exercises.size)
        assertEquals(listOf(0, 1), routine.exercises.map { it.orderIndex })
        assertEquals(19, routine.estimatedDurationMin)
    }

    @Test
    fun `archive routine uses soft delete`() = runTest {
        useCase.createOrUpdateRoutine("Legs", null, emptyList())
        val id = repository.routines.single().id

        useCase.archiveRoutine(id)

        assertTrue(repository.routines.single().isArchived)
    }

    @Test
    fun `create routine updates existing routine by id`() = runTest {
        assertTrue(useCase.createOrUpdateRoutine("Upper", "#E53935", listOf(RoutineExerciseInput("row", 4, 10, null, 90)), id = "routine_upper"))
        assertTrue(useCase.createOrUpdateRoutine("Upper 2", "#1565C0", listOf(RoutineExerciseInput("bench", 3, 8, 80.0, 120)), id = "routine_upper"))

        val updated = repository.routines.single()
        assertEquals("Upper 2", updated.name)
        assertEquals("#1565C0", updated.colorTag)
        assertEquals(listOf("bench"), updated.exercises.map { it.exerciseId })
        assertEquals(listOf(0), updated.exercises.map { it.orderIndex })
    }

    private class FakeRoutineRepository : RoutineRepository {
        var routines = emptyList<Routine>()

        override suspend fun routines(includeArchived: Boolean): List<Routine> {
            return if (includeArchived) routines else routines.filterNot { it.isArchived }
        }

        override suspend fun routine(id: String): Routine? = routines.firstOrNull { it.id == id }

        override suspend fun upsertRoutine(routine: Routine) {
            routines = routines.filterNot { it.id == routine.id } + routine
        }

        override suspend fun archiveRoutine(id: String) {
            routines = routines.map { if (it.id == id) it.copy(isArchived = true) else it }
        }
    }
}
