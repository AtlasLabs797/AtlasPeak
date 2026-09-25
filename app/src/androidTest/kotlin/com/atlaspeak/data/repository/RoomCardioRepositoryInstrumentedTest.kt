package com.atlaspeak.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Perf N+1 (auditoria): `sessions()` batea la workout_session y el cardio_type de cada sesion
 * de cardio en dos queries totales (en vez de una `getSession()`/`getCardioType()` por sesion).
 * Este test cubre que el resultado combinado en memoria sigue siendo correcto por sesion.
 */
@RunWith(AndroidJUnit4::class)
class RoomCardioRepositoryInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomCardioRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomCardioRepository(database, ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sessionsResolveTheirOwnWorkoutTimingAndCardioTypeName() = runTest {
        database.cardioDao().upsertCardioType(cardioType("run", "Correr"))
        database.cardioDao().upsertCardioType(cardioType("bike", "Bici"))

        repository.createSession(cardioSession("cardio-1", "run", startTime = 1_700_000_000_000))
        repository.createSession(cardioSession("cardio-2", "bike", startTime = 1_700_000_100_000))

        val sessions = repository.sessions()

        assertEquals(2, sessions.size)
        // Orden: start_time DESC (igual que getCardioSessions()).
        assertEquals("cardio-2", sessions[0].id)
        assertEquals("Bici", sessions[0].cardioTypeName)
        assertEquals(1_700_000_100_000, sessions[0].startTime)
        assertEquals("cardio-1", sessions[1].id)
        assertEquals("Correr", sessions[1].cardioTypeName)
        assertEquals(1_700_000_000_000, sessions[1].startTime)
    }

    private fun cardioType(id: String, nameEs: String) = CardioTypeEntity(
        id = id,
        nameEs = nameEs,
        nameEn = nameEs,
        hasGps = false,
        iconName = "directions_run",
        isPreset = true,
        isArchived = false,
    )

    private fun cardioSession(id: String, cardioTypeId: String, startTime: Long) = CardioSession(
        id = id,
        cardioTypeId = cardioTypeId,
        cardioTypeName = "",
        mode = CardioMode.Timer,
        startTime = startTime,
        endTime = null,
        durationSeconds = null,
        distanceKm = null,
        avgSpeedKmh = null,
        maxSpeedKmh = null,
        caloriesBurned = null,
        hasGps = false,
        route = emptyList(),
        completed = true,
    )
}
