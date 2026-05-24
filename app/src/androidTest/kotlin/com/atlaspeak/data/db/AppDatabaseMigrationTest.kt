package com.atlaspeak.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration1To2AddsBodyWaterMassWithoutDroppingBodyData() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1BodyComposition()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT body_water_mass_kg, weight_kg FROM body_composition WHERE id = 'body-1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
                assertEquals(82.5, cursor.getDouble(1), 0.0)
            }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.insertV1BodyComposition() {
        execSQL(
            """
            INSERT INTO body_composition (
                id,
                measured_at,
                weight_kg,
                source,
                synced_to_hc,
                created_at
            ) VALUES (
                'body-1',
                1700000000000,
                82.5,
                'MANUAL',
                0,
                1700000000000
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DB = "atlas-peak-migration-test"
    }
}
