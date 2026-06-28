package com.atlaspeak.domain.usecase.cardio

import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.body.BodyCompositionSource
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

        val timerSessionId = useCase.startSession("run", CardioMode.Timer)
        val countdownSessionId = useCase.startSession("run", CardioMode.Countdown(targetDurationSeconds = 1800))

        assertNotNull(timerSessionId)
        assertNotNull(countdownSessionId)
        assertEquals(listOf(CardioMode.Timer, CardioMode.Countdown(1800)), repository.sessions.map { it.mode })
    }

    @Test
    fun `start session returns null for missing type`() = runTest {
        assertNull(useCase.startSession("missing", CardioMode.Timer))
    }

    @Test
    fun `complete session calculates distance speed and stores route`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        val sessionId = useCase.startSession("run", CardioMode.Timer)!!
        val route = listOf(
            LocationPoint(40.0, -3.0, 1_700_000_000_000L),
            LocationPoint(40.0, -2.991, 1_700_000_600_000L),
        )

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_600_000L,
            manualDistanceKm = null,
            route = route,
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
        val sessionId = useCase.startSession("row", CardioMode.Timer)!!

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_900_000L,
            manualDistanceKm = 5.0,
            manualAvgSpeedKmh = 20.0,
            route = emptyList(),
        )

        assertEquals(5.0, completed?.distanceKm)
        assertEquals(20.0, completed?.avgSpeedKmh)
        assertTrue(completed?.route?.isEmpty() == true)
    }

    @Test
    fun `complete session uses latest body weight for calorie estimate`() = runTest {
        repository.types = listOf(CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false))
        bodyRepository.entries = listOf(bodyEntry(weightKg = 100.0, measuredAt = 1_700_000_000_000L))
        val sessionId = useCase.startSession("run", CardioMode.Timer)!!

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_600_000L,
            manualDistanceKm = 1.0,
            manualAvgSpeedKmh = 6.0,
            route = emptyList(),
        )

        assertEquals(172, completed?.caloriesBurned)
    }

    @Test
    fun `complete session rejects manual cardio without distance and speed`() = runTest {
        repository.types = listOf(CardioType("bike", "Bike", hasGps = false, isPreset = true, isArchived = false))
        val sessionId = useCase.startSession("bike", CardioMode.Timer)!!

        val completed = useCase.completeSession(
            sessionId = sessionId,
            endedAt = 1_700_000_900_000L,
            manualDistanceKm = null,
            route = emptyList(),
        )

        assertNull(completed)
        assertFalse(repository.sessions.single().completed)
    }

    private class FakeCardioRepository : CardioRepository {
        var types = emptyList<CardioType>()
        var sessions = emptyList<CardioSession>()

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

        override suspend fun updateSession(session: CardioSession) {
            sessions = sessions.map { if (it.id == session.id) session else it }
        }

        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
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
}
