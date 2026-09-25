package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.cardio.CardioRoutePoint
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType

interface CardioRepository {
    suspend fun cardioTypes(includeArchived: Boolean = false): List<CardioType>
    suspend fun upsertCustomType(type: CardioType)
    suspend fun archiveType(id: String)
    suspend fun createSession(session: CardioSession): CardioSession
    suspend fun session(id: String): CardioSession?
    suspend fun sessions(): List<CardioSession>
    suspend fun findActiveSession(): CardioSession?
    suspend fun findActiveOrCreateSession(session: CardioSession): CardioSession {
        return findActiveSession() ?: createSession(session)
    }
    suspend fun updateSession(session: CardioSession)
    suspend fun deleteSession(id: String)

    /**
     * Persiste un punto GPS aceptado para la sesion. El caller (FGS) ya valido
     * el punto por accuracy/antiguedad/coordenadas invalidas. La sesion aplica
     * el filtro de velocidades imposibles antes de llamar.
     */
    suspend fun addRoutePoint(point: CardioRoutePoint)

    suspend fun addRoutePointIfSessionActive(point: CardioRoutePoint): Boolean {
        val active = session(point.sessionId)?.completed == false
        if (active) addRoutePoint(point)
        return active
    }

    /** Puntos GPS de una sesion ordenados por timestamp ascendente. */
    suspend fun routePoints(sessionId: String): List<CardioRoutePoint>

    suspend fun routePointsCount(sessionId: String): Int

    /** Suma de [CardioRoutePoint.distanceFromPreviousKm] para la sesion. */
    suspend fun routeDistanceKm(sessionId: String): Double

    /** Borra todos los puntos GPS en vuelo de la sesion. */
    suspend fun deleteRoutePoints(sessionId: String)

    /**
     * Persiste el snapshot final de la sesion de cardio (incluyendo la polilinea
     * serializada) y borra los puntos GPS en vuelo, todo dentro de una sola
     * transaccion de base de datos. BUG-091 / Fase 2 P0.
     */
    suspend fun finalizeCardioSessionRoute(session: CardioSession)
}
