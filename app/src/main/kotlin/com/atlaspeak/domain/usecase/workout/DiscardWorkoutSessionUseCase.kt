package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * Borra una sesión activa que el usuario abandona. Sin esto, salir con back dejaba
 * sesiones incompletas huérfanas en la base de datos y duplicados al volver a entrar.
 */
class DiscardWorkoutSessionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {
    suspend operator fun invoke(sessionId: String) {
        workoutRepository.deleteSession(sessionId)
    }
}
