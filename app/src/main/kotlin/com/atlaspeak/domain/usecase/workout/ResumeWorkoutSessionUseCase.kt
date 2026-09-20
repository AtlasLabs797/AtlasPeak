package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * Devuelve el id de la sesion de fuerza activa persistida en Room, o null si no hay.
 *
 * Es idempotente: si no existe una sesion activa no crea nada; la UI usa este caso
 * para volver a abrir la pantalla despues de un kill del proceso sin generar
 * duplicados.
 */
class ResumeWorkoutSessionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {
    suspend operator fun invoke(): String? = workoutRepository.findActiveSession()?.id
}
