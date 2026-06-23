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

    @Test
    fun migration2To3RemovesEntryPasswordWhileKeepingProfile() {
        helper.createDatabase(TEST_DB, 2).apply {
            insertV2UserAndProfile()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        )

        migrated.query("SELECT password_hash, password_salt, last_login_at FROM users WHERE id = 'user-1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
            }
        migrated.query("SELECT display_name FROM user_profile WHERE user_id = 'user-1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals("Atlas User", cursor.getString(0))
            }
        migrated.close()
    }

    @Test
    fun migration3To4AddsCardioPlanningColumnsWithoutDroppingPlan() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertV3WeeklyPlan()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_3_4,
        )

        migrated.query(
            """
            SELECT type, routine_id, cardio_type_id, cardio_target_duration_sec
            FROM weekly_plan
            WHERE day_of_week = 1
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("STRENGTH", cursor.getString(0))
            assertEquals("routine-1", cursor.getString(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
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

    private fun SupportSQLiteDatabase.insertV2UserAndProfile() {
        execSQL(
            """
            INSERT INTO users (
                id,
                google_id,
                email,
                password_hash,
                password_salt,
                created_at,
                last_login_at
            ) VALUES (
                'user-1',
                NULL,
                NULL,
                'hash',
                'salt',
                1700000000000,
                1700000000100
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO user_profile (
                id,
                user_id,
                display_name,
                updated_at
            ) VALUES (
                'profile-1',
                'user-1',
                'Atlas User',
                1700000000200
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertV3WeeklyPlan() {
        execSQL(
            """
            INSERT INTO routines (
                id,
                name,
                created_at,
                updated_at,
                is_archived
            ) VALUES (
                'routine-1',
                'Routine 1',
                1700000000000,
                1700000000000,
                0
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO weekly_plan (
                id,
                day_of_week,
                routine_id,
                is_rest_day,
                notification_enabled,
                notification_time
            ) VALUES (
                'weekly_plan_1',
                1,
                'routine-1',
                0,
                1,
                '18:00'
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DB = "atlas-peak-migration-test"
    }
}
