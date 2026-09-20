package com.atlaspeak.domain.usecase.cardio

import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.repository.CardioRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResumeCardioSessionUseCaseTest {
    private val cardioRepository = FakeCardioRepository()
    private val useCase = ResumeCardioSessionUseCase(cardioRepository)

    @Test
    fun `resume returns active cardio session id when one exists`() = runTest {
        cardioRepository.sessions = listOf(
            session("active", completed = false),
            session("finished", completed = true),
        )

        assertEquals("active", useCase())
    }

    @Test
    fun `resume returns null when no active cardio session exists`() = runTest {
        cardioRepository.sessions = listOf(session("finished", completed = true))

        assertNull(useCase())
    }

    @Test
    fun `resume is idempotent and does not create a new cardio session`() = runTest {
        cardioRepository.sessions = listOf(session("active", completed = false))

        val first = useCase()
        val second = useCase()

        assertEquals("active", first)
        assertEquals("active", second)
        assertEquals(1, cardioRepository.sessions.size)
    }

    private fun session(id: String, completed: Boolean) = CardioSession(
        id = id,
        cardioTypeId = "run",
        cardioTypeName = "Run",
        mode = CardioMode.Timer,
        startTime = 1L,
        endTime = null,
        durationSeconds = null,
        distanceKm = null,
        avgSpeedKmh = null,
        maxSpeedKmh = null,
        caloriesBurned = null,
        hasGps = true,
        route = emptyList(),
        completed = completed,
    )

    private class FakeCardioRepository : CardioRepository {
        var sessions = emptyList<CardioSession>()
        override suspend fun cardioTypes(includeArchived: Boolean) = emptyList<com.atlaspeak.domain.model.cardio.CardioType>()
        override suspend fun upsertCustomType(type: com.atlaspeak.domain.model.cardio.CardioType) = Unit
        override suspend fun archiveType(id: String) = Unit
        override suspend fun createSession(session: CardioSession): CardioSession = session
        override suspend fun session(id: String): CardioSession? = sessions.firstOrNull { it.id == id }
        override suspend fun sessions(): List<CardioSession> = sessions
        override suspend fun findActiveSession(): CardioSession? = sessions.firstOrNull { !it.completed }
        override suspend fun updateSession(session: CardioSession) = Unit
        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
        }
        override suspend fun addRoutePoint(point: com.atlaspeak.domain.model.cardio.CardioRoutePoint) = Unit
        override suspend fun routePoints(sessionId: String): List<com.atlaspeak.domain.model.cardio.CardioRoutePoint> = emptyList()
        override suspend fun routePointsCount(sessionId: String): Int = 0
        override suspend fun routeDistanceKm(sessionId: String): Double = 0.0
        override suspend fun deleteRoutePoints(sessionId: String) = Unit
        override suspend fun finalizeCardioSessionRoute(session: CardioSession) = Unit
    }
}
