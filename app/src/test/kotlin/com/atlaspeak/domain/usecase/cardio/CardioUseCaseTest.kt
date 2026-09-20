package com.atlaspeak.domain.usecase.cardio

import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.body.BodyCompositionSource
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioRoutePoint
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.usecase.workout.ActiveSessionStartResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardioUseCaseTest {
    private val repository = FakeCardioRepository()
    private val bodyRepository = FakeBodyCompositionRepository()
    private val useCase = CardioUseCase(
        repository = repository,
        bodyCompositionRepository = bodyRepository,
        now = { 1_700_000_000_000L },
    )

    @Test
    fun `create custom type rejects blank name and stores gps capability`() = runTest {
        assertFalse(useCase.createOrUpdateCustomType(name = "", hasGps = true))

        assertTrue(useCase.createOrUpdateCustomType(name = "Trail run", hasGps = true))

        val created = repository.types.single()
        assertEquals("Trail run", created.name)
        assertTrue(created.hasGps)
        assertFalse(created.isPreset)
        assertFalse(created.isArchived)
    }

    @Test
    fun `start session creates timer or countdown session from cardio type`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))

        val timerResult = useCase.startSession("run", CardioMode.Timer)
        // Re-create state to simulate opening the screen a second time without an existing session.
        repository.sessions = emptyList()
        val countdownResult = useCase.startSession("run", CardioMode.Countdown(targetDurationSeconds = 1800))

        val timerSessionId = assertInstanceOf(ActiveSessionStartResult.Started::class.java, timerResult).sessionId
        val countdownSessionId = assertInstanceOf(ActiveSessionStartResult.Started::class.java, countdownResult).sessionId
        assertNotNull(timerSessionId)
        assertNotNull(countdownSessionId)
        assertEquals(listOf(CardioMode.Timer, CardioMode.Countdown(1800)), repository.sessions.map { it.mode })
    }

    @Test
    fun `start session returns NotFound for missing type`() = runTest {
        assertInstanceOf(ActiveSessionStartResult.NotFound::class.java, useCase.startSession("missing", CardioMode.Timer))
    }

    @Test
    fun `start session returns Resumed for same cardio type without creating duplicate`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val first = useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started
        repository.sessions = repository.sessions.map { it.copy(route = listOf(LocationPoint(40.0, -3.0, 1_700_000_500_000L))) }

        val second = useCase.startSession("run", CardioMode.Timer)

        val resumed = assertInstanceOf(ActiveSessionStartResult.Resumed::class.java, second)
        assertEquals(first.sessionId, resumed.sessionId)
        // Route is preserved across recreation.
        assertEquals(1, repository.sessions.size)
        assertEquals(1, repository.sessions.single().route.size)
    }

    @Test
    fun `start session returns Conflict for different cardio type and does not overwrite active session`() = runTest {
        repository.types = listOf(
            CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false),
            CardioType("bike", "Bike", hasGps = false, isPreset = true, isArchived = false),
        )
        val existingId = "session-bike"
        repository.sessions = listOf(
            CardioSession(
                id = existingId,
                cardioTypeId = "bike",
                cardioTypeName = "Bike",
                mode = CardioMode.Timer,
                startTime = 1_700_000_000_000L,
                endTime = null,
                durationSeconds = null,
                distanceKm = null,
                avgSpeedKmh = null,
                maxSpeedKmh = null,
                caloriesBurned = null,
                hasGps = false,
                route = listOf(LocationPoint(40.0, -3.0, 1_700_000_500_000L)),
                completed = false,
            ),
        )

        val result = useCase.startSession("run", CardioMode.Timer)

        val conflict = assertInstanceOf(ActiveSessionStartResult.Conflict::class.java, result)
        assertEquals(existingId, conflict.sessionId)
        // No debe sustituir la sesion activa.
        assertEquals(1, repository.sessions.size)
        assertEquals("bike", repository.sessions.single().cardioTypeId)
        assertEquals(1, repository.sessions.single().route.size)
    }

    @Test
    fun `complete session calculates distance speed and stores route`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val route = listOf(
            LocationPoint(40.0, -3.0, 1_700_000_000_000L),
            LocationPoint(40.0, -2.991, 1_700_000_600_000L),
        )
        route.forEach { useCase.appendRoutePoint(sessionId, it, accuracyMeters = 5f, previousAcceptedPoint = null) }

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_600_000L,
            manualDistanceKm = null,
        )

        assertEquals(600, completed?.durationSeconds)
        assertEquals(0.767, completed?.distanceKm!!, 0.02)
        assertEquals(4.60, completed.avgSpeedKmh!!, 0.1)
        assertNotNull(completed.caloriesBurned)
        assertEquals(route, completed.route)
    }

    @Test
    fun `complete session accepts manual distance and speed for non gps cardio`() = runTest {
        repository.types = listOf(CardioType("row", "Rowing", hasGps = false, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("row", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_900_000L,
            manualDistanceKm = 5.0,
            manualAvgSpeedKmh = 20.0,
        )

        assertEquals(5.0, completed?.distanceKm)
        assertEquals(20.0, completed?.avgSpeedKmh)
        assertTrue(completed?.route?.isEmpty() == true)
    }

    @Test
    fun `complete session uses latest body weight for calorie estimate`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        bodyRepository.entries = listOf(bodyEntry(weightKg = 100.0, measuredAt = 1_700_000_000_000L))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_600_000L,
            manualDistanceKm = 1.0,
            manualAvgSpeedKmh = 6.0,
        )

        assertEquals(172, completed?.caloriesBurned)
    }

    @Test
    fun `complete session rejects manual cardio without distance and speed`() = runTest {
        repository.types = listOf(CardioType("bike", "Bike", hasGps = false, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("bike", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_900_000L,
            manualDistanceKm = null,
        )

        assertNull(completed)
        assertFalse(repository.sessions.single().completed)
    }

    @Test
    fun `appendRoutePoint persists each point incrementally and rebuilds same distance on restore`() = runTest {
        // bike: cap 90 km/h. El segmento de 60s con ~0.77 km recorre 46 km/h,
        // valido para ciclismo pero no para correr (cap 45).
        repository.types = listOf(CardioType("bike", "Bike", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("bike", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val route = listOf(
            LocationPoint(40.0, -3.0, 1_700_000_000_000L),
            LocationPoint(40.0, -2.991, 1_700_000_060_000L),
            LocationPoint(40.0, -2.982, 1_700_000_120_000L),
            LocationPoint(40.0, -2.973, 1_700_000_180_000L),
            LocationPoint(40.0, -2.964, 1_700_000_240_000L),
        )

        route.forEach { point ->
            val persisted = useCase.appendRoutePoint(
                sessionId = sessionId,
                point = point,
                accuracyMeters = 5f,
                previousAcceptedPoint = null,
            )
            assertNotNull(persisted, "Point at ${point.timestamp} should be persisted")
        }

        // Re-create the use case to simulate process recreation: nuevo caso de uso
        // contra el mismo repositorio. La distancia reconstruida debe coincidir
        // con la suma incremental registrada.
        val rebuilt = CardioUseCase(
            repository = repository,
            bodyCompositionRepository = bodyRepository,
            now = { 1_700_000_300_000L },
        )
        val restored = rebuilt.restoreRoute(sessionId)

        assertEquals(route.size, restored.points.size)
        assertEquals(route, restored.points)
        val expectedDistance = route.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }
        assertEquals(expectedDistance, restored.distanceKm, 1e-6)
        assertEquals(expectedDistance, repository.routeDistanceKm(sessionId), 1e-6)
        assertEquals(route.size, repository.routePointsCount(sessionId))
    }

    @Test
    fun `appendRoutePoint rejects invalid coordinates without modifying distance`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val valid = LocationPoint(40.0, -3.0, 1_700_000_000_000L)
        useCase.appendRoutePoint(sessionId, valid, accuracyMeters = 5f, previousAcceptedPoint = null)

        val invalidLat = useCase.appendRoutePoint(
            sessionId = sessionId,
            point = LocationPoint(200.0, -3.0, 1_700_000_060_000L),
            accuracyMeters = 5f,
            previousAcceptedPoint = null,
        )
        val invalidLon = useCase.appendRoutePoint(
            sessionId = sessionId,
            point = LocationPoint(40.0, -200.0, 1_700_000_120_000L),
            accuracyMeters = 5f,
            previousAcceptedPoint = null,
        )

        assertNull(invalidLat)
        assertNull(invalidLon)
        assertEquals(0.0, repository.routeDistanceKm(sessionId), 1e-9)
        assertEquals(1, repository.routePointsCount(sessionId))
    }

    @Test
    fun `appendRoutePoint rejects impossibly fast segment and does not persist it`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val p1 = LocationPoint(40.0, -3.0, 1_700_000_000_000L)
        // ~111 km en 1s = 400000 km/h, claramente imposible para correr (cap 45 km/h).
        val p2 = LocationPoint(41.0, -3.0, 1_700_000_001_000L)
        useCase.appendRoutePoint(sessionId, p1, accuracyMeters = 5f, previousAcceptedPoint = null)
        val rejected = useCase.appendRoutePoint(sessionId, p2, accuracyMeters = 5f, previousAcceptedPoint = null)

        assertNull(rejected)
        assertEquals(1, repository.routePointsCount(sessionId))
        assertEquals(0.0, repository.routeDistanceKm(sessionId), 1e-9)
    }

    @Test
    fun `appendRoutePoint rejects zero or negative accuracy and keeps the previous accepted point`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val valid = LocationPoint(40.0, -3.0, 1_700_000_000_000L)
        useCase.appendRoutePoint(sessionId, valid, accuracyMeters = 5f, previousAcceptedPoint = null)

        val zeroRejected = useCase.appendRoutePoint(
            sessionId = sessionId,
            point = LocationPoint(40.0, -2.99, 1_700_000_060_000L),
            accuracyMeters = 0f,
            previousAcceptedPoint = null,
        )
        val negativeRejected = useCase.appendRoutePoint(
            sessionId = sessionId,
            point = LocationPoint(40.0, -2.99, 1_700_000_120_000L),
            accuracyMeters = -3f,
            previousAcceptedPoint = null,
        )

        assertNull(zeroRejected)
        assertNull(negativeRejected)
        // Solo el primer fix valido queda persistido; distancia 0 porque es un unico punto.
        assertEquals(1, repository.routePointsCount(sessionId))
        assertEquals(0.0, repository.routeDistanceKm(sessionId), 1e-9)
    }

    @Test
    fun `appendRoutePoint accepts unknown accuracy (null) and rejects only non positive values`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId

        // El FGS pasa null (no expone accuracy); la regla solo bloquea valores <= 0.
        val persisted = useCase.appendRoutePoint(
            sessionId = sessionId,
            point = LocationPoint(40.0, -3.0, 1_700_000_000_000L),
            accuracyMeters = null,
            previousAcceptedPoint = null,
        )

        assertNotNull(persisted)
        assertEquals(1, repository.routePointsCount(sessionId))
    }

    @Test
    fun `appendRoutePoint continues from previous accepted point after recreation`() = runTest {
        // bike: cap 90 km/h. El segmento de 60s con ~0.85 km recorre 51 km/h,
        // valido para ciclismo pero no para correr (cap 45).
        repository.types = listOf(CardioType("bike", "Bike", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("bike", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val p1 = LocationPoint(40.0, -3.0, 1_700_000_000_000L)
        val p2 = LocationPoint(40.0, -2.99, 1_700_000_060_000L)
        useCase.appendRoutePoint(sessionId, p1, accuracyMeters = 5f, previousAcceptedPoint = null)

        // Simulamos muerte del proceso: nuevo caso de uso, sin estado en memoria.
        val rebuilt = CardioUseCase(
            repository = repository,
            bodyCompositionRepository = bodyRepository,
            now = { 1_700_000_300_000L },
        )
        val persisted = rebuilt.appendRoutePoint(
            sessionId = sessionId,
            point = p2,
            accuracyMeters = 5f,
            // Le pedimos al caso de uso que localice el previous por su cuenta.
            previousAcceptedPoint = null,
        )

        assertNotNull(persisted)
        assertEquals(2, repository.routePointsCount(sessionId))
        val restored = rebuilt.restoreRoute(sessionId)
        assertEquals(listOf(p1, p2), restored.points)
    }

    @Test
    fun `complete session deletes persisted route points after snapshotting route into json`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = (useCase.startSession("run", CardioMode.Timer) as ActiveSessionStartResult.Started).sessionId
        val p1 = LocationPoint(40.0, -3.0, 1_700_000_000_000L)
        val p2 = LocationPoint(40.0, -2.991, 1_700_000_600_000L)
        useCase.appendRoutePoint(sessionId, p1, accuracyMeters = 5f, previousAcceptedPoint = null)
        useCase.appendRoutePoint(sessionId, p2, accuracyMeters = 5f, previousAcceptedPoint = null)

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_600_000L,
            manualDistanceKm = null,
        )

        assertNotNull(completed)
        // La sesion completada lleva la ruta en su `route` (snapshot) y los puntos
        // incrementales ya no ocupan sitio.
        assertEquals(listOf(p1, p2), completed?.route)
        assertEquals(0, repository.routePointsCount(sessionId))
    }

    private class FakeCardioRepository : CardioRepository {
        var types = emptyList<CardioType>()
        var sessions = emptyList<CardioSession>()
        private val points = mutableMapOf<String, MutableList<CardioRoutePoint>>()

        override suspend fun cardioTypes(includeArchived: Boolean): List<CardioType> {
            return if (includeArchived) types else types.filterNot { it.isArchived }
        }

        override suspend fun upsertCustomType(type: CardioType) {
            types = types.filterNot { it.id == type.id } + type
        }

        override suspend fun archiveType(id: String) {
            types = types.map { if (it.id == id) it.copy(isArchived = true) else it }
        }

        override suspend fun createSession(session: CardioSession): CardioSession {
            sessions = sessions + session
            return session
        }

        override suspend fun session(id: String): CardioSession? = sessions.firstOrNull { it.id == id }

        override suspend fun sessions(): List<CardioSession> = sessions

        override suspend fun findActiveSession(): CardioSession? = sessions.firstOrNull { !it.completed }

        override suspend fun updateSession(session: CardioSession) {
            sessions = sessions.map { if (it.id == session.id) session else it }
        }

        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
            points.remove(id)
        }

        override suspend fun addRoutePoint(point: CardioRoutePoint) {
            val list = points.getOrPut(point.sessionId) { mutableListOf() }
            list.removeAll { it.id == point.id }
            list.add(point)
        }

        override suspend fun routePoints(sessionId: String): List<CardioRoutePoint> {
            return points[sessionId].orEmpty().sortedBy { it.timestampMs }
        }

        override suspend fun routePointsCount(sessionId: String): Int {
            return points[sessionId]?.size ?: 0
        }

        override suspend fun routeDistanceKm(sessionId: String): Double {
            return points[sessionId].orEmpty().sumOf { it.distanceFromPreviousKm }
        }

        override suspend fun deleteRoutePoints(sessionId: String) {
            points.remove(sessionId)
        }

        override suspend fun finalizeCardioSessionRoute(session: CardioSession) {
            sessions = sessions.map { if (it.id == session.id) session else it }
            points.remove(session.id)
        }
    }

    private class FakeBodyCompositionRepository : BodyCompositionRepository {
        var entries = emptyList<BodyCompositionEntry>()

        override suspend fun entries(): List<BodyCompositionEntry> = entries

        override suspend fun upsert(entry: BodyCompositionEntry) {
            entries = entries.filterNot { it.id == entry.id } + entry
        }
    }

    private fun bodyEntry(weightKg: Double, measuredAt: Long) = BodyCompositionEntry(
        id = "body-$measuredAt",
        measuredAt = measuredAt,
        weightKg = weightKg,
        bodyFatPercent = null,
        muscleMassKg = null,
        waterPercent = null,
        bodyWaterMassKg = null,
        visceralFatLevel = null,
        proteinPercent = null,
        boneMassKg = null,
        bodyAge = null,
        source = BodyCompositionSource.Manual,
        syncedToHealthConnect = false,
        createdAt = measuredAt,
    )

    private fun haversineKm(a: LocationPoint, b: LocationPoint): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinDLat = kotlin.math.sin(dLat / 2.0)
        val sinDLon = kotlin.math.sin(dLon / 2.0)
        val h = sinDLat * sinDLat + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinDLon * sinDLon
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
        return earthRadiusKm * c
    }
}
