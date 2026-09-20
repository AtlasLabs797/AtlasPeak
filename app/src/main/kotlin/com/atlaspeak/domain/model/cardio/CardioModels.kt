package com.atlaspeak.domain.model.cardio

data class CardioType(
    val id: String,
    val name: String,
    val hasGps: Boolean,
    val isPreset: Boolean,
    val isArchived: Boolean,
    val iconName: String = "directions_run",
)

sealed class CardioMode {
    data object Timer : CardioMode()
    data class Countdown(val targetDurationSeconds: Int) : CardioMode()
}

/**
 * Tipo de foreground service que cabe en el escenario actual de cardio. BUG-093
 * (Fase 4 P0): Android 14+ aplica una politica estricta de tipos de FGS y
 * reclamar uno sin los permisos/uso real que lo justifican dispara
 * `SecurityException`. La UI expone este enum para que la pantalla pueda
 * mostrar el mensaje adecuado ("modo local, sin notificacion persistente")
 * cuando no hay un FGS legal para la sesion.
 *
 * - [Location]: tracking GPS activo, requiere `ACCESS_FINE_LOCATION` en
 *   runtime. Reclama `FOREGROUND_SERVICE_TYPE_LOCATION`.
 * - [Health]: cardio sin GPS con `ACTIVITY_RECOGNITION` concedida (u otro uso
 *   legitimo de datos de salud). Reclama `FOREGROUND_SERVICE_TYPE_HEALTH`.
 * - [None]: ningun FGS legal para este momento (permiso denegado o no
 *   solicitado, escenario de cardio manual sin `ACTIVITY_RECOGNITION`). El
 *   cronometro local sigue, pero el sistema puede parar el proceso en
 *   background: documentado en la UI.
 */
enum class CardioFgsMode { Location, Health, None }

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
)

/**
 * Punto GPS persistido de una sesion de cardio activa. Mantiene la distancia
 * incremental desde el punto anterior para que la distancia total de la sesion
 * sea SUM(distance_from_previous_km). BUG-091 / Fase 2 P0.
 */
data class CardioRoutePoint(
    val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val speedKmh: Double? = null,
    val distanceFromPreviousKm: Double = 0.0,
) {
    fun toLocationPoint(): LocationPoint = LocationPoint(latitude, longitude, timestampMs)
}

data class CardioSession(
    val id: String,
    val cardioTypeId: String,
    val cardioTypeName: String,
    val mode: CardioMode,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Int?,
    val distanceKm: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val caloriesBurned: Int?,
    val hasGps: Boolean,
    val route: List<LocationPoint>,
    val completed: Boolean,
    // BUG-094 (Fase 5 P1): tiempo en pausa excluido del cronometro efectivo sin
    // falsificar `startTime`. Mientras la sesion esta corriendo ambos campos valen
    // `pausedAtMillis = null` y `totalPausedDurationMillis = 0`. Al pausar, el VM
    // fija `pausedAtMillis` con la hora actual. Al reanudar, acumula el delta en
    // `totalPausedDurationMillis` y vuelve a dejar `pausedAtMillis = null`.
    val pausedAtMillis: Long? = null,
    val totalPausedDurationMillis: Long = 0L,
)

/**
 * Calcula los segundos efectivos transcurridos de una sesion de cardio, excluyendo
 * el tiempo en pausa. BUG-094 (Fase 5 P1). Mientras la sesion esta pausada
 * ([CardioSession.pausedAtMillis] != null) el delta pendiente
 * `(now - pausedAtMillis)` se resta ademas del acumulado previo; con la sesion
 * corriendo solo se resta `totalPausedDurationMillis`. El resultado nunca es
 * negativo (se hace coerce a 0 para tolerar pequenas carreras de reloj o una
 * ordenacion invertida entre start/pause).
 */
fun effectiveElapsedSeconds(session: CardioSession, now: Long): Long {
    val pendingPauseMs = if (session.pausedAtMillis != null) {
        (now - session.pausedAtMillis).coerceAtLeast(0L)
    } else {
        0L
    }
    val totalPausedMs = session.totalPausedDurationMillis + pendingPauseMs
    return ((now - session.startTime - totalPausedMs) / 1000L).coerceAtLeast(0L)
}
