package com.atlaspeak.domain.usecase.progress

import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.progress.ProgressHistoryFilter
import com.atlaspeak.domain.model.progress.ProgressHistoryItem
import com.atlaspeak.domain.model.progress.ProgressHistoryType
import com.atlaspeak.domain.model.progress.ProgressPeriod
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.Exercise
import com.atlaspeak.domain.model.workout.MuscleGroup
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressUseCaseTest {
    private val workoutRepository = FakeWorkoutRepository()
    private val cardioRepository = FakeCardioRepository()
    private val exerciseRepository = FakeExerciseRepository()
    private val now = 1_700_000_000_000L
    private val useCase = ProgressUseCase(
        workoutRepository = workoutRepository,
        cardioRepository = cardioRepository,
        exerciseRepository = exerciseRepository,
        now = { now },
    )

    @Test
    fun `history combines completed strength and cardio sessions sorted newest first`() = runTest {
        workoutRepository.sessions = listOf(
            workoutSession(id = "old-strength", routineName = "Old Upper", startTime = now - DAYS_40),
            workoutSession(id = "cardio-header", routineName = null, startTime = now - DAYS_2 + 2_000L, sets = emptyList()),
            workoutSession(id = "strength", routineName = "Upper Power", startTime = now - DAYS_2),
        )
        cardioRepository.sessions = listOf(
            cardioSession(id = "draft-cardio", typeName = "Run", startTime = now - DAYS_2, completed = false),
            cardioSession(id = "cardio", typeName = "Run", startTime = now - DAYS_2 + 1_000L),
        )

        val all = useCase.history(
            query = "",
            filter = ProgressHistoryFilter(type = ProgressHistoryType.All, period = ProgressPeriod.Month),
        )

        assertEquals(listOf("cardio", "strength"), all.map { it.id })
        assertTrue(all.first() is ProgressHistoryItem.Cardio)
        assertTrue(all.last() is ProgressHistoryItem.Strength)
    }

    @Test
    fun `history filters by type and searches routine cardio and exercise names`() = runTest {
        workoutRepository.sessions = listOf(
            workoutSession(id = "bench-session", routineName = "Upper Power", exerciseName = "Bench press"),
        )
        cardioRepository.sessions = listOf(
            cardioSession(id = "run-session", typeName = "Trail Run"),
        )

        val strength = useCase.history(
            query = "bench",
            filter = ProgressHistoryFilter(type = ProgressHistoryType.Strength, period = ProgressPeriod.Year),
        )
        val cardio = useCase.history(
            query = "trail",
            filter = ProgressHistoryFilter(type = ProgressHistoryType.Cardio, period = ProgressPeriod.Year),
        )

        assertEquals(listOf("bench-session"), strength.map { it.id })
        assertEquals(listOf("run-session"), cardio.map { it.id })
    }

    @Test
    fun `exercise progress aggregates completed sets by session`() = runTest {
        exerciseRepository.exercises = listOf(exercise(id = "bench", name = "Bench press", primaryGroupId = 1))
        workoutRepository.sessions = listOf(
            workoutSession(
                id = "s1",
                exerciseId = "bench",
                exerciseName = "Bench press",
                startTime = now - DAYS_40,
                sets = listOf(
                    workoutSet(sessionId = "s1", exerciseId = "bench", setNumber = 1, reps = 8, weightKg = 80.0),
                    workoutSet(sessionId = "s1", exerciseId = "bench", setNumber = 2, reps = 6, weightKg = 85.0),
                    workoutSet(sessionId = "s1", exerciseId = "bench", setNumber = 3, reps = 3, weightKg = 90.0, completed = false),
                ),
            ),
            workoutSession(
                id = "s2",
                exerciseId = "bench",
                exerciseName = "Bench press",
                startTime = now - DAYS_2,
                sets = listOf(workoutSet(sessionId = "s2", exerciseId = "bench", setNumber = 1, reps = 5, weightKg = 90.0)),
            ),
        )

        val progress = useCase.exerciseProgress(ProgressPeriod.Year).single()

        assertEquals("bench", progress.exercise.id)
        assertEquals(listOf("s1", "s2"), progress.points.map { it.sessionId })
        assertEquals(listOf(85.0, 90.0), progress.points.map { it.maxWeightKg })
        assertEquals(listOf(1_150.0, 450.0), progress.points.map { it.volumeKg })
        assertEquals(1_600.0, progress.totalVolumeKg)
    }

    @Test
    fun `muscle group progress includes primary and secondary group exercises`() = runTest {
        exerciseRepository.exercises = listOf(
            exercise(id = "bench", name = "Bench press", primaryGroupId = 1, secondaryGroupId = 3),
            exercise(id = "pushdown", name = "Pushdown", primaryGroupId = 3),
        )
        workoutRepository.sessions = listOf(
            workoutSession(
                id = "s1",
                exerciseId = "bench",
                exerciseName = "Bench press",
                sets = listOf(workoutSet(sessionId = "s1", exerciseId = "bench", reps = 8, weightKg = 80.0)),
            ),
            workoutSession(
                id = "s2",
                exerciseId = "pushdown",
                exerciseName = "Pushdown",
                sets = listOf(workoutSet(sessionId = "s2", exerciseId = "pushdown", reps = 12, weightKg = 30.0)),
            ),
        )

        val triceps = useCase.muscleGroupProgress(ProgressPeriod.Year)
            .single { it.muscleGroup.id == 3 }

        assertEquals(listOf("bench", "pushdown"), triceps.exercises.map { it.exercise.id })
        assertEquals(1_000.0, triceps.totalVolumeKg)
    }

    private fun workoutSession(
        id: String,
        routineName: String? = "Upper",
        exerciseId: String = "bench",
        exerciseName: String = "Bench press",
        startTime: Long = now - DAYS_2,
        completed: Boolean = true,
        sets: List<WorkoutSet> = listOf(workoutSet(sessionId = id, exerciseId = exerciseId)),
    ) = WorkoutSession(
        id = id,
        routineId = "routine-$id",
        routineName = routineName,
        startTime = startTime,
        endTime = startTime + 1_800_000L,
        durationSeconds = 1_800,
        completed = completed,
        totalVolumeKg = sets.filter { it.completed }.sumOf { (it.actualReps ?: it.plannedReps) * (it.weightKg ?: 0.0) },
        exercises = listOf(
            ActiveWorkoutExercise(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                orderIndex = 0,
                restSeconds = 90,
                sets = sets,
            ),
        ),
    )

    private fun workoutSet(
        sessionId: String,
        exerciseId: String,
        setNumber: Int = 1,
        reps: Int = 5,
        weightKg: Double = 100.0,
        completed: Boolean = true,
    ) = WorkoutSet(
        id = "$sessionId-$exerciseId-$setNumber",
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = setNumber,
        plannedReps = reps,
        actualReps = reps,
        weightKg = weightKg,
        completed = completed,
        completedAt = if (completed) now else null,
        isPersonalRecord = false,
    )

    private fun cardioSession(
        id: String,
        typeName: String,
        startTime: Long = now - DAYS_2,
        completed: Boolean = true,
    ) = CardioSession(
        id = id,
        cardioTypeId = "type-$id",
        cardioTypeName = typeName,
        mode = CardioMode.Timer,
        startTime = startTime,
        endTime = startTime + 1_200_000L,
        durationSeconds = 1_200,
        distanceKm = 4.2,
        avgSpeedKmh = 12.6,
        maxSpeedKmh = 14.0,
        caloriesBurned = 300,
        hasGps = true,
        route = emptyList(),
        completed = completed,
    )

    private fun exercise(
        id: String,
        name: String,
        primaryGroupId: Int,
        secondaryGroupId: Int? = null,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = MuscleGroup(primaryGroupId, "Group $primaryGroupId"),
        secondaryMuscleGroup = secondaryGroupId?.let { MuscleGroup(it, "Group $it") },
        description = null,
        isPreset = true,
        isArchived = false,
    )

    private class FakeWorkoutRepository : WorkoutRepository {
        var sessions = emptyList<WorkoutSession>()

        override suspend fun createSession(session: WorkoutSession): WorkoutSession = session
        override suspend fun session(id: String): WorkoutSession? = sessions.firstOrNull { it.id == id }
        override suspend fun sessions(): List<WorkoutSession> = sessions
        override suspend fun findActiveSession(): WorkoutSession? = sessions.firstOrNull { !it.completed }
        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
        }
        override suspend fun upsertSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = null
        override suspend fun updateSessionCompletion(
            sessionId: String,
            endTime: Long,
            durationSeconds: Int,
            totalVolumeKg: Double,
        ) = Unit
    }

    private class FakeCardioRepository : CardioRepository {
        var sessions = emptyList<CardioSession>()

        override suspend fun cardioTypes(includeArchived: Boolean) = emptyList<com.atlaspeak.domain.model.cardio.CardioType>()
        override suspend fun upsertCustomType(type: com.atlaspeak.domain.model.cardio.CardioType) = Unit
        override suspend fun archiveType(id: String) = Unit
        override suspend fun createSession(session: CardioSession): CardioSession = session
        override suspend fun session(id: String): CardioSession? = sessions.firstOrNull { it.id == id }
        override suspend fun sessions(): List<CardioSession> = sessions
        override suspend fun findActiveSession(): CardioSession? = sessions.firstOrNull { !it.completed }
        override suspend fun updateSession(session: CardioSession) = Unit
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun addRoutePoint(point: com.atlaspeak.domain.model.cardio.CardioRoutePoint) = Unit
        override suspend fun routePoints(sessionId: String): List<com.atlaspeak.domain.model.cardio.CardioRoutePoint> = emptyList()
        override suspend fun routePointsCount(sessionId: String): Int = 0
        override suspend fun routeDistanceKm(sessionId: String): Double = 0.0
        override suspend fun deleteRoutePoints(sessionId: String) = Unit
        override suspend fun finalizeCardioSessionRoute(session: CardioSession) = Unit
    }

    private class FakeExerciseRepository : ExerciseRepository {
        var exercises = emptyList<Exercise>()

        override suspend fun muscleGroups(): List<MuscleGroup> = exercises
            .flatMap { listOfNotNull(it.muscleGroup, it.secondaryMuscleGroup) }
            .distinctBy { it.id }

        override suspend fun exercises(includeArchived: Boolean): List<Exercise> {
            return if (includeArchived) exercises else exercises.filterNot { it.isArchived }
        }

        override suspend fun upsertCustomExercise(exercise: Exercise) = Unit
        override suspend fun archiveExercise(id: String) = Unit
    }

    private companion object {
        const val DAYS_2 = 2L * 24L * 60L * 60L * 1_000L
        const val DAYS_40 = 40L * 24L * 60L * 60L * 1_000L
    }
}
