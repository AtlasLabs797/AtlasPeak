package com.atlaspeak.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaspeak.data.db.entity.CardioRoutePointEntity
import com.atlaspeak.data.db.entity.CardioSessionEntity
import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.data.repository.RoomCardioRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentados de la tabla cardio_route_points contra un AppDatabase
 * real (no in-memory) para poder cerrar y reabrir la conexion simulando la
 * muerte del proceso. BUG-091 / Fase 2 P0.
 */
@RunWith(AndroidJUnit4::class)
class CardioRoutePointsInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomCardioRepository
    private lateinit var sessionId: String

    @Before
    fun setUp() {
        // Cada test parte de un archivo limpio; lo borramos en tearDown.
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase(DB_NAME)
        database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            DB_NAME,
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomCardioRepository(database, ApplicationProvider.getApplicationContext())
        sessionId = "cardio-1"
        seedSession(database, sessionId)
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase(DB_NAME)
    }

    @Test
    fun addingPointsAndRecreatingDatabasePreservesDistanceAndCount() = runTest {
        val points = listOf(
            point("rp-1", 1_700_000_000_000L, 40.0, -3.0, 0.0),
            point("rp-2", 1_700_000_060_000L, 40.0, -2.991, 0.768),
            point("rp-3", 1_700_000_120_000L, 40.0, -2.982, 0.768),
        )
        points.forEach { repository.addRoutePoint(it) }
        assertEquals(3, repository.routePointsCount(sessionId))
        assertEquals(0.768 + 0.768, repository.routeDistanceKm(sessionId), 1e-9)

        // Cerramos y reabrimos la DB para simular muerte de proceso. El archivo
        // SQLite persiste; los route points tambien.
        database.close()
        database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            DB_NAME,
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomCardioRepository(database, ApplicationProvider.getApplicationContext())

        val restored = repository.routePoints(sessionId)
        val distance = repository.routeDistanceKm(sessionId)
        assertEquals(3, restored.size)
        assertEquals(points.map { it.id }, restored.map { it.id })
        assertEquals(points.sumOf { it.distanceFromPreviousKm }, distance, 1e-9)
    }

    @Test
    fun deletingSessionCascadesRoutePoints() = runTest {
        repository.addRoutePoint(point("rp-1", 1_700_000_000_000L, 40.0, -3.0, 0.0))
        assertEquals(1, repository.routePointsCount(sessionId))

        repository.deleteSession(sessionId)

        assertEquals(0, repository.routePointsCount(sessionId))
        assertTrue(database.cardioDao().getCardioSession(sessionId) == null)
        assertTrue(database.workoutDao().getSession(sessionId) == null)
    }

    @Test
    fun deletingRoutePointsKeepsSessionRow() = runTest {
        repository.addRoutePoint(point("rp-1", 1_700_000_000_000L, 40.0, -3.0, 0.0))

        repository.deleteRoutePoints(sessionId)

        assertEquals(0, repository.routePointsCount(sessionId))
        assertTrue(database.cardioDao().getCardioSession(sessionId) != null)
        assertTrue(database.workoutDao().getSession(sessionId) != null)
    }

    @Test
    fun foreignKeyRejectsRoutePointForMissingWorkoutSession() = runTest {
        val orphan = CardioRoutePointEntity(
            id = "rp-orphan",
            sessionId = "missing-session",
            timestampMs = 1_700_000_000_000L,
            latitude = 40.0,
            longitude = -3.0,
            accuracyM = 5f,
            speedKmh = null,
            distanceFromPreviousKm = 0.0,
        )
        var thrown: Throwable? = null
        try {
            database.cardioRoutePointDao().upsert(orphan)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("FK constraint should reject orphan route point", thrown != null)
    }

    private fun point(id: String, timestampMs: Long, lat: Double, lon: Double, distKm: Double) =
        CardioRoutePointEntity(
            id = id,
            sessionId = sessionId,
            timestampMs = timestampMs,
            latitude = lat,
            longitude = lon,
            accuracyM = 5f,
            speedKmh = null,
            distanceFromPreviousKm = distKm,
        )

    private fun seedSession(database: AppDatabase, sessionId: String) {
        database.cardioDao().upsertCardioType(
            CardioTypeEntity(
                id = "run",
                nameEs = "Run",
                nameEn = "Run",
                hasGps = true,
                iconName = "directions_run",
                isPreset = true,
                isArchived = false,
            ),
        )
        runBlocking {
            database.withTransaction {
                database.workoutDao().upsertSession(
                    WorkoutSessionEntity(
                        id = sessionId,
                        routineId = null,
                        type = "CARDIO",
                        startTime = 1_700_000_000_000L,
                        endTime = null,
                        durationSeconds = null,
                        notes = null,
                        completed = false,
                        caloriesBurned = null,
                        totalVolumeKg = null,
                    ),
                )
                database.cardioDao().upsertCardioSession(
                    CardioSessionEntity(
                        id = sessionId,
                        sessionId = sessionId,
                        cardioTypeId = "run",
                        mode = "TIMER",
                        targetDurationSec = 0,
                        actualDurationSec = null,
                        distanceKm = null,
                        avgSpeedKmh = null,
                        maxSpeedKmh = null,
                        caloriesBurned = null,
                        hasGps = true,
                        routePolylineJson = null,
                        source = "GPS",
                    ),
                )
            }
        }
    }

    private companion object {
        const val DB_NAME = "atlas-peak-cardio-route-test.db"
    }
}