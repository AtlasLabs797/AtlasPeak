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
)
