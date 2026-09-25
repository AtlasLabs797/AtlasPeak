package com.atlaspeak.domain.usecase.cardio

import com.atlaspeak.domain.repository.CardioRepository
import javax.inject.Inject

/**
 * Devuelve el id de la sesion de cardio activa persistida en Room, o null si no hay.
 *
 * Es idempotente: no crea nada. La UI lo usa para reabrir la pantalla tras un
 * kill del proceso sin generar duplicados de la sesion ni perder la ruta GPS.
 */
class ResumeCardioSessionUseCase @Inject constructor(
    private val cardioRepository: CardioRepository,
) {
    suspend operator fun invoke(): String? = cardioRepository.findActiveSession()?.id
}
