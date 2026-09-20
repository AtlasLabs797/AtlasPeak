package com.atlaspeak.domain.usecase.workout

/**
 * Resultado de intentar iniciar (o reanudar) una sesion activa de fuerza/cardio.
 *
 * Una sola llamada cubre los tres caminos para que la UI no tenga que distinguir
 * entre "arranque nuevo" y "reanudar": la propia capa de dominio decide.
 *
 * - [Started] - no habia sesion activa; se creo una nueva con [sessionId].
 * - [Resumed] - ya existia una sesion activa para el mismo recurso (misma rutina o
 *   mismo tipo de cardio); se devuelve su id y la UI la hidrata desde Room.
 * - [Conflict] - existia una sesion activa pero de un recurso distinto (otra rutina u
 *   otro tipo de cardio). La UI debe mostrar un dialogo de decision; NO se sustituye
 *   automaticamente para no perder los sets/ruta registrados.
 * - [NotFound] - la rutina o el tipo de cardio solicitado no existe.
 */
sealed class ActiveSessionStartResult {
    abstract val sessionId: String

    data class Started(override val sessionId: String) : ActiveSessionStartResult()
    data class Resumed(override val sessionId: String) : ActiveSessionStartResult()
    data class Conflict(override val sessionId: String) : ActiveSessionStartResult()
    data object NotFound : ActiveSessionStartResult() {
        override val sessionId: String = ""
    }
}
