package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import java.util.UUID

class StartWorkoutSessionUseCase(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val now: () -> Long,
) {
    @javax.inject.Inject
    constructor(
        routineRepository: RoutineRepository,
        workoutRepository: WorkoutRepository,
    ) : this(routineRepository, workoutRepository, { System.currentTimeMillis() })

    suspend operator fun invoke(routineId: String): String? {
        val routine = routineRepository.routine(routineId) ?: return null
        val session = routine.toWorkoutSession(startedAt = now())
        return workoutRepository.createSession(session).id
    }

    private fun Routine.toWorkoutSession(startedAt: Long): WorkoutSession {
        val sessionId = UUID.randomUUID().toString()
        return WorkoutSession(
            id = sessionId,
            routineId = id,
            routineName = name,
            startTime = startedAt,
            endTime = null,
            durationSeconds = null,
            completed = false,
            totalVolumeKg = null,
            exercises = exercises.sortedBy { it.orderIndex }.map { routineExercise ->
                ActiveWorkoutExercise(
                    exerciseId = routineExercise.exerciseId,
                    exerciseName = routineExercise.exerciseName,
                    orderIndex = routineExercise.orderIndex,
                    restSeconds = routineExercise.restSeconds,
                    notes = routineExercise.notes,
                    sets = (1..routineExercise.sets.coerceAtLeast(1)).map { setNumber ->
                        WorkoutSet(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            exerciseId = routineExercise.exerciseId,
                            exerciseName = routineExercise.exerciseName,
                            setNumber = setNumber,
                            plannedReps = routineExercise.reps.coerceAtLeast(1),
                            actualReps = null,
                            weightKg = routineExercise.weightKg,
                            completed = false,
                            completedAt = null,
                            isPersonalRecord = false,
                        )
                    },
                )
            },
        )
    }
}
