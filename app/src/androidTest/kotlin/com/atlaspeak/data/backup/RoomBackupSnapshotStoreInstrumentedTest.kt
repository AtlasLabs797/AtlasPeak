package com.atlaspeak.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.Fixtures
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.ExerciseEntity
import com.atlaspeak.data.db.entity.MuscleGroupEntity
import com.atlaspeak.data.db.entity.RoutineEntity
import com.atlaspeak.data.db.entity.RoutineExerciseEntity
import com.atlaspeak.data.db.entity.UserEntity
import com.atlaspeak.data.db.entity.UserProfileEntity
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.data.db.entity.WorkoutSetEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBackupSnapshotStoreInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RoomBackupSnapshotStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        store = RoomBackupSnapshotStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun snapshotRestoresForeignKeyHeavyWorkoutAndHealthConnectData() = runTest {
        insertConnectedRows()
        val snapshot = store.snapshot()

        database.workoutDao().deleteSession("session-1")
        database.healthConnectDao().deleteSleepSession("sleep-1")
        assertEquals(0, database.workoutDao().getSets("session-1").size)
        assertEquals(0, database.healthConnectDao().countSleepStages())

        store.restore(snapshot)

        assertEquals("Athlete", database.userProfileDao().getProfile()?.displayName)
        assertNotNull(database.routineDao().getRoutine("routine-1"))
        assertEquals(1, database.routineDao().getRoutineExercises("routine-1").size)
        assertEquals(1, database.workoutDao().getSets("session-1").size)
        assertEquals(1, database.healthConnectDao().countSleepStages())
        assertEquals(1_700_000_000_000, database.settingsDao().getSettings()?.lastBackupAt)
    }

    @Test
    fun restoreRejectsUnknownColumnsBeforeWriting() = runTest {
        insertConnectedRows()
        val snapshot = store.snapshot()
        val poisonedUsers = snapshot.tables.getValue("users").map { row ->
            row + ("unexpected_column" to JsonPrimitive("boom"))
        }
        val poisoned = snapshot.copy(tables = snapshot.tables + ("users" to poisonedUsers))

        val result = runCatching { store.restore(poisoned) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Athlete", database.userProfileDao().getProfile()?.displayName)
    }

    @Test
    fun restoreRejectsMissingColumnsBeforeWriting() = runTest {
        insertConnectedRows()
        val snapshot = store.snapshot()
        val poisonedUsers = snapshot.tables.getValue("users").map { row -> row - "id" }
        val poisoned = snapshot.copy(tables = snapshot.tables + ("users" to poisonedUsers))

        val result = runCatching { store.restore(poisoned) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Athlete", database.userProfileDao().getProfile()?.displayName)
    }

    @Test
    fun restoreRejectsNonPrimitiveJsonValuesBeforeWriting() = runTest {
        insertConnectedRows()
        val snapshot = store.snapshot()
        val poisonedUsers = snapshot.tables.getValue("users").map { row ->
            row + ("id" to buildJsonObject { put("nested", JsonPrimitive("user-1")) })
        }
        val poisoned = snapshot.copy(tables = snapshot.tables + ("users" to poisonedUsers))

        val result = runCatching { store.restore(poisoned) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Athlete", database.userProfileDao().getProfile()?.displayName)
    }

    @Test
    fun restoreRejectsValuesWithWrongSqliteAffinityBeforeWriting() = runTest {
        insertConnectedRows()
        val snapshot = store.snapshot()
        val poisonedUsers = snapshot.tables.getValue("users").map { row ->
            row + ("created_at" to JsonPrimitive("not-a-number"))
        }
        val poisoned = snapshot.copy(tables = snapshot.tables + ("users" to poisonedUsers))

        val result = runCatching { store.restore(poisoned) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Athlete", database.userProfileDao().getProfile()?.displayName)
    }

    private suspend fun insertConnectedRows() {
        database.userDao().upsertUser(
            UserEntity(
                id = "user-1",
                passwordHash = "hash",
                passwordSalt = "salt",
                createdAt = 1_700_000_000_000,
            ),
        )
        database.userProfileDao().upsertProfile(
            UserProfileEntity(
                id = "profile-1",
                userId = "user-1",
                displayName = "Athlete",
                updatedAt = 1_700_000_000_000,
            ),
        )
        database.referenceDao().insertMuscleGroups(
            listOf(
                MuscleGroupEntity(
                    id = 1,
                    nameEs = "Pecho",
                    nameEn = "Chest",
                    iconName = "fitness_center",
                ),
            ),
        )
        database.exerciseDao().upsertExercise(
            ExerciseEntity(
                id = "exercise-1",
                nameEs = "Press banca",
                nameEn = "Bench press",
                muscleGroupId = 1,
                isPreset = false,
                createdAt = 1_700_000_000_000,
            ),
        )
        database.routineDao().upsertRoutine(
            RoutineEntity(
                id = "routine-1",
                name = "Upper",
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_000_000,
            ),
        )
        database.routineDao().upsertRoutineExercises(
            listOf(
                RoutineExerciseEntity(
                    id = "routine-exercise-1",
                    routineId = "routine-1",
                    exerciseId = "exercise-1",
                    sets = 3,
                    reps = 5,
                    orderIndex = 0,
                ),
            ),
        )
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "session-1",
                routineId = "routine-1",
                type = "STRENGTH",
                startTime = 1_700_000_000_000,
                completed = true,
            ),
        )
        database.workoutDao().upsertSets(
            listOf(
                WorkoutSetEntity(
                    id = "set-1",
                    sessionId = "session-1",
                    exerciseId = "exercise-1",
                    setNumber = 1,
                    plannedReps = 5,
                    actualReps = 5,
                    weightKg = 100.0,
                    completed = true,
                    completedAt = 1_700_000_010_000,
                ),
            ),
        )
        database.healthConnectDao().upsertSleepSession(Fixtures.sleepSession())
        database.healthConnectDao().upsertSleepStage(Fixtures.sleepStage())
        database.settingsDao().insertSettings(
            AppSettingsEntity(lastBackupAt = 1_700_000_000_000),
        )
    }
}
