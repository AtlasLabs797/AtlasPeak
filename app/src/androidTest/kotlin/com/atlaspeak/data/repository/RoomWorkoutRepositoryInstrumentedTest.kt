package com.atlaspeak.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.domain.model.workout.ActiveWorkoutExercise
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Perf N+1 (auditoria): `sessions()` batea los sets de todas las sesiones de fuerza en una
 * sola query (`getSetsForStrengthSessions`) en vez de una por sesion. Este test cubre que el
 * agrupado en memoria sigue devolviendo, por sesion, exactamente sus propios sets.
 */
@RunWith(AndroidJUnit4::class)
class RoomWorkoutRepositoryInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomWorkoutRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sessionsGroupsSetsByTheirOwnSessionOnly() = runTest {
        repository.createSession(session("session-1", startTime = 1_700_000_000_000, sets = listOf("s1a", "s1b")))
        repository.createSession(session("session-2", startTime = 1_700_000_100_000, sets = listOf("s2a")))
        // Una sesion de cardio no debe aparecer ni aportar sets a sessions() (solo STRENGTH).
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "cardio-session",
                type = "CARDIO",
                startTime = 1_700_000_200_000,
                completed = true,
            ),
        )

        val sessions = repository.sessions()

        assertEquals(2, sessions.size)
        // Orden: start_time DESC (igual que getStrengthSessions()).
        assertEquals("session-2", sessions[0].id)
        assertEquals("session-1", sessions[1].id)

        val session1Sets = sessions.single { it.id == "session-1" }.exercises.flatMap { it.sets }.map { it.id }
        val session2Sets = sessions.single { it.id == "session-2" }.exercises.flatMap { it.sets }.map { it.id }
        assertEquals(setOf("s1a", "s1b"), session1Sets.toSet())
        assertEquals(setOf("s2a"), session2Sets.toSet())
    }

    private fun session(id: String, startTime: Long, sets: List<String>): WorkoutSession {
        val workoutSets = sets.mapIndexed { index, setId ->
            WorkoutSet(
                id = setId,
                sessionId = id,
                exerciseId = "exercise-1",
                exerciseName = "Bench press",
                setNumber = index + 1,
                plannedReps = 5,
                actualReps = 5,
                weightKg = 60.0,
                completed = true,
                completedAt = startTime + index,
                isPersonalRecord = false,
            )
        }
        return WorkoutSession(
            id = id,
            routineId = null,
            routineName = null,
            startTime = startTime,
            endTime = null,
            durationSeconds = null,
            completed = true,
            totalVolumeKg = 0.0,
            exercises = listOf(
                ActiveWorkoutExercise(
                    exerciseId = "exercise-1",
                    exerciseName = "Bench press",
                    orderIndex = 0,
                    restSeconds = 90,
                    sets = workoutSets,
                ),
            ),
        )
    }
}
