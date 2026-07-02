package com.atlaspeak.domain.usecase.cardio

import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import java.util.UUID
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class CardioUseCase(
    private val repository: CardioRepository,
    private val bodyCompositionRepository: BodyCompositionRepository,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        repository: CardioRepository,
        bodyCompositionRepository: BodyCompositionRepository,
    ) : this(repository, bodyCompositionRepository, { System.currentTimeMillis() })

    suspend fun cardioTypes(): List<CardioType> = repository.cardioTypes().sortedBy { it.name }

    suspend fun createOrUpdateCustomType(
        name: String,
        hasGps: Boolean,
        id: String = UUID.randomUUID().toString(),
    ): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return false
        val existing = repository.cardioTypes(includeArchived = true).firstOrNull { it.id == id }
        if (existing?.isPreset == true) return false
        repository.upsertCustomType(
            CardioType(
                id = id,
                name = trimmedName,
                hasGps = hasGps,
                isPreset = false,
                isArchived = false,
            ),
        )
        return true
    }

    suspend fun archiveType(id: String) {
        repository.archiveType(id)
    }

    suspend fun startSession(cardioTypeId: String, mode: CardioMode): String? {
        val type = repository.cardioTypes().firstOrNull { it.id == cardioTypeId } ?: return null
        val session = CardioSession(
            id = UUID.randomUUID().toString(),
            cardioTypeId = type.id,
            cardioTypeName = type.name,
            mode = mode,
            startTime = now(),
            endTime = null,
            durationSeconds = null,
            distanceKm = null,
            avgSpeedKmh = null,
            maxSpeedKmh = null,
            caloriesBurned = null,
            hasGps = type.hasGps,
            route = emptyList(),
            completed = false,
        )
        return repository.createSession(session).id
    }

    suspend fun completeSession(
        sessionId: String,
        endedAt: Long = System.currentTimeMillis(),
        manualDistanceKm: Double?,
        manualAvgSpeedKmh: Double? = null,
        route: List<LocationPoint>,
    ): CardioSession? {
        val session = repository.session(sessionId) ?: return null
        val durationSeconds = ((endedAt - session.startTime) / 1000).coerceAtLeast(0).toInt()
        val maxSpeedKmh = session.reasonableMaxSpeedKmh()
        val sanitizedRoute = route.sanitizedRoute(maxSpeedKmh)
        val routeDistance = sanitizedRoute.distanceKm()
        val sanitizedManualDistance = manualDistanceKm?.takeIf { it > 0.0 && it <= MAX_REASONABLE_DISTANCE_KM }
        val sanitizedManualSpeed = manualAvgSpeedKmh?.takeIf { it > 0.0 && it <= maxSpeedKmh }
        val distanceKm = sanitizedManualDistance ?: routeDistance.takeIf { it > 0.0 }
        val avgSpeed = sanitizedManualSpeed ?: if (distanceKm != null && durationSeconds > 0) {
            distanceKm / (durationSeconds / 3600.0)
        } else {
            null
        }
        if (distanceKm == null || avgSpeed == null || avgSpeed > maxSpeedKmh) return null
        val weightKg = latestBodyWeightKg() ?: FALLBACK_WEIGHT_KG
        val completed = session.copy(
            endTime = endedAt,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = sanitizedRoute.maxSegmentSpeedKmh(maxSpeedKmh),
            caloriesBurned = estimateCalories(session.cardioTypeName, durationSeconds, weightKg),
            route = sanitizedRoute,
            completed = true,
        )
        repository.updateSession(completed)
        return completed
    }

    private suspend fun latestBodyWeightKg(): Double? {
        return bodyCompositionRepository.entries()
            .asSequence()
            .filter { it.weightKg != null && it.weightKg > 0.0 && it.weightKg <= MAX_BODY_WEIGHT_KG }
            .maxByOrNull { it.measuredAt }
            ?.weightKg
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val FALLBACK_WEIGHT_KG = 75.0
        private const val MAX_BODY_WEIGHT_KG = 500.0
        private const val MAX_REASONABLE_DISTANCE_KM = 500.0

        fun List<LocationPoint>.distanceKm(): Double {
            return zipWithNext().sumOf { (from, to) -> from.distanceTo(to) }
        }

        fun List<LocationPoint>.maxSegmentSpeedKmh(maxReasonableSpeedKmh: Double = Double.MAX_VALUE): Double? {
            return zipWithNext().mapNotNull { (from, to) ->
                val seconds = ((to.timestamp - from.timestamp) / 1000.0).takeIf { it > 0.0 }
                seconds?.let { from.distanceTo(to) / (it / 3600.0) }
                    ?.takeIf { it <= maxReasonableSpeedKmh }
            }.maxOrNull()
        }

        fun List<LocationPoint>.sanitizedRoute(maxReasonableSpeedKmh: Double): List<LocationPoint> {
            return fold(emptyList()) { accepted, point ->
                if (!point.isValidCoordinate()) return@fold accepted
                val previous = accepted.lastOrNull()
                when {
                    previous == null -> accepted + point
                    point.timestamp <= previous.timestamp -> accepted
                    previous.segmentSpeedKmh(point) > maxReasonableSpeedKmh -> accepted
                    else -> accepted + point
                }
            }
        }

        private fun LocationPoint.distanceTo(other: LocationPoint): Double {
            val dLat = Math.toRadians(other.latitude - latitude)
            val dLon = Math.toRadians(other.longitude - longitude)
            val lat1 = Math.toRadians(latitude)
            val lat2 = Math.toRadians(other.latitude)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_KM * c
        }

        private fun LocationPoint.segmentSpeedKmh(other: LocationPoint): Double {
            val seconds = ((other.timestamp - timestamp) / 1000.0).takeIf { it > 0.0 } ?: return Double.POSITIVE_INFINITY
            return distanceTo(other) / (seconds / 3600.0)
        }

        private fun LocationPoint.isValidCoordinate(): Boolean {
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }

        private fun CardioSession.reasonableMaxSpeedKmh(): Double {
            val name = cardioTypeName
            return when {
                cardioTypeId.contains("swim", ignoreCase = true) ||
                    name.contains("swim", ignoreCase = true) ||
                    name.contains("natacion", ignoreCase = true) -> 15.0
                cardioTypeId.contains("bike", ignoreCase = true) ||
                    cardioTypeId.contains("cycling", ignoreCase = true) ||
                    name.contains("bike", ignoreCase = true) ||
                    name.contains("bici", ignoreCase = true) ||
                    name.contains("cycling", ignoreCase = true) ||
                    name.contains("ciclismo", ignoreCase = true) -> 90.0
                cardioTypeId.contains("run", ignoreCase = true) ||
                    cardioTypeId.contains("treadmill", ignoreCase = true) ||
                    name.contains("run", ignoreCase = true) ||
                    name.contains("carrera", ignoreCase = true) ||
                    name.contains("cinta", ignoreCase = true) -> 45.0
                cardioTypeId.contains("row", ignoreCase = true) ||
                    cardioTypeId.contains("elliptical", ignoreCase = true) ||
                    name.contains("row", ignoreCase = true) ||
                    name.contains("remo", ignoreCase = true) ||
                    name.contains("elliptical", ignoreCase = true) ||
                    name.contains("eliptica", ignoreCase = true) -> 35.0
                else -> 60.0
            }
        }

        private fun estimateCalories(cardioTypeName: String, durationSeconds: Int, weightKg: Double): Int? {
            if (durationSeconds <= 0) return null
            val met = when {
                cardioTypeName.contains("run", ignoreCase = true) ||
                    cardioTypeName.contains("carrera", ignoreCase = true) ||
                    cardioTypeName.contains("cinta", ignoreCase = true) -> 9.8
                cardioTypeName.contains("cycling", ignoreCase = true) ||
                    cardioTypeName.contains("ciclismo", ignoreCase = true) -> 7.5
                cardioTypeName.contains("bike", ignoreCase = true) ||
                    cardioTypeName.contains("bici", ignoreCase = true) -> 6.8
                cardioTypeName.contains("row", ignoreCase = true) ||
                    cardioTypeName.contains("remo", ignoreCase = true) -> 7.0
                cardioTypeName.contains("swim", ignoreCase = true) ||
                    cardioTypeName.contains("natacion", ignoreCase = true) -> 8.0
                cardioTypeName.contains("elliptical", ignoreCase = true) ||
                    cardioTypeName.contains("eliptica", ignoreCase = true) -> 5.0
                else -> 5.0
            }
            val minutes = durationSeconds / 60.0
            return (met * 3.5 * weightKg / 200.0 * minutes).roundToInt().coerceAtLeast(1)
        }
    }
}
