package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import java.util.UUID

/**
 * Inicia (o reanuda) una sesion activa de fuerza.
 *
 * Una sesion ya creada en Room se considera activa mientras `completed = false`.
 * El arranque y la reanudacion se colapsan en una sola llamada para que la UI
 * no tenga que distinguir entre ambos casos:
 *
 * - Sin sesion activa: se crea una nueva ([ActiveSessionStartResult.Started]).
 * - Ya existe una sesion activa para la misma rutina: se devuelve su id
 *   ([ActiveSessionStartResult.Resumed]). Asi, si Android mata el proceso y el
 *   usuario vuelve a abrir la pantalla, no se duplica la sesion ni se pierden
 *   los sets registrados.
 * - Existe una sesion activa pero de OTRA rutina: [ActiveSessionStartResult.Conflict].
 *   La UI debe mostrar el dialogo de decision (continuar/descartar/cancelar);
 *   no se sustituye en silencio porque seria destruir datos del usuario.
 * - La rutina no existe: [ActiveSessionStartResult.NotFound].
 */
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

    suspend operator fun invoke(routineId: String, weeklyPlanSessionId: String? = null): ActiveSessionStartResult {
        val routine = routineRepository.routine(routineId) ?: return ActiveSessionStartResult.NotFound
        val active = workoutRepository.findActiveSession()
        when {
            active == null -> {
                val session = routine.toWorkoutSession(startedAt = now(), weeklyPlanSessionId = weeklyPlanSessionId)
                val created = workoutRepository.createSession(session)
                return ActiveSessionStartResult.Started(created.id)
            }
            active.routineId == routineId -> {
                return ActiveSessionStartResult.Resumed(active.id)
            }
            else -> {
                return ActiveSessionStartResult.Conflict(active.id)
            }
        }
    }

    private fun Routine.toWorkoutSession(startedAt: Long, weeklyPlanSessionId: String? = null): WorkoutSession {
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
            weeklyPlanSessionId = weeklyPlanSessionId,
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
