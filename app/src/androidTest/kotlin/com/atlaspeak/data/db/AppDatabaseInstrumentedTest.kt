package com.atlaspeak.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaspeak.data.db.seed.DatabaseSeeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        seeder = DatabaseSeeder(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseCreatesAllV1Tables() {
        val tableNames = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

        assertTrue(tableNames.containsAll(AppDatabase.V1_TABLES))
        assertEquals(AppDatabase.V1_TABLES.size, tableNames.intersect(AppDatabase.V1_TABLES).size)
    }

    @Test
    fun seedIsIdempotentAndCreatesSingletonRows() = runTest {
        seeder.seed()
        seeder.seed()

        assertEquals(10, database.referenceDao().countMuscleGroups())
        assertTrue(database.exerciseDao().countPresetExercises() >= 20)
        assertEquals(7, database.cardioDao().countPresetCardioTypes())
        assertNotNull(database.settingsDao().getSettings())
        assertNotNull(database.authSecurityDao().getAuthSecurity())
        assertEquals(6, database.healthConnectDao().countSyncLogs())
    }

    @Test
    fun healthConnectSleepStagesCascadeWithSession() = runTest {
        database.healthConnectDao().upsertSleepSession(Fixtures.sleepSession())
        database.healthConnectDao().upsertSleepStage(Fixtures.sleepStage())

        assertEquals(1, database.healthConnectDao().countSleepStages())
        database.healthConnectDao().deleteSleepSession("sleep-1")
        assertEquals(0, database.healthConnectDao().countSleepStages())
    }

    @Test
    fun healthConnectRecordsReplaceByProviderRecordId() = runTest {
        database.healthConnectDao().upsertSteps(Fixtures.stepsRecord(id = "local-1", count = 100))
        database.healthConnectDao().upsertSteps(Fixtures.stepsRecord(id = "local-2", count = 200))

        assertEquals(1, database.healthConnectDao().countStepRecords())
    }
}
