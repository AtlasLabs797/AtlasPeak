package com.atlaspeak.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun migration4To5AddsOrderIndexWithoutDroppingPlan() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertV4WeeklyPlan()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            AppDatabase.MIGRATION_4_5,
        )

        migrated.query(
            """
            SELECT order_index, type, routine_id, notification_time
            FROM weekly_plan
            WHERE day_of_week = 1
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertEquals("STRENGTH", cursor.getString(1))
            assertEquals("routine-1", cursor.getString(2))
            assertEquals("18:00", cursor.getString(3))
        }
        migrated.close()
    }

    @Test
    fun migration4To5AssignsUniqueOrderIndexesPerDay() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertV4WeeklyPlan(twoSessionsSameDay = true)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            AppDatabase.MIGRATION_4_5,
        )

        migrated.query(
            """
            SELECT id, order_index
            FROM weekly_plan
            WHERE day_of_week = 1
            ORDER BY order_index
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("weekly_plan_1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals("weekly_plan_2", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    @Test
    fun migration5To6NormalizesCollidingOrderIndexes() {
        helper.createDatabase(TEST_DB, 5).apply {
            insertV5WeeklyPlanWithCollidingOrderIndexes()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabase.MIGRATION_5_6,
        )

        migrated.query(
            """
            SELECT id, order_index
            FROM weekly_plan
            WHERE day_of_week = 1
            ORDER BY order_index
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("weekly_plan_1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals("weekly_plan_2", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    @Test
    fun migration6To7CreatesCardioRoutePointsTable() {
        helper.createDatabase(TEST_DB, 6).apply {
            insertV6CardioSession()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabase.MIGRATION_6_7,
        )

        migrated.query(
            "SELECT COUNT(*) FROM cardio_route_points WHERE session_id = 'session-cardio-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query(
            """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type='index' AND name='index_cardio_route_points_session_id'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type='index' AND name='index_cardio_route_points_session_id_timestamp_ms'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.execSQL(
            """
            INSERT INTO cardio_route_points (
                id, session_id, timestamp_ms, latitude, longitude,
                accuracy_m, speed_kmh, distance_from_previous_km
            ) VALUES (
                'route-1', 'session-cardio-1', 1700000000000, 40.0, -3.0,
                5.0, 4.5, 0.0
            )
            """.trimIndent(),
        )
        migrated.query("SELECT latitude FROM cardio_route_points WHERE id = 'route-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(40.0, cursor.getDouble(0), 0.0)
        }
        migrated.close()
    }

    @Test
    fun migration7To8AddsCardioPauseColumns() {
        helper.createDatabase(TEST_DB, 7).apply {
            insertV7CardioSession()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            AppDatabase.MIGRATION_7_8,
        )

        migrated.query(
            "SELECT paused_at_ms, total_paused_duration_ms FROM cardio_sessions WHERE id = 'cardio-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
            assertEquals(0L, cursor.getLong(1))
        }
        migrated.close()
    }

    /**
     * BUG-097 (Fase 8 P1): la migracion v8 -> v9 anade la columna
     * `weekly_plan_session_id` a `workout_sessions` y crea su indice. Las
     * sesiones preexistentes quedan con `weekly_plan_session_id = NULL`,
     * lo cual mantiene la compatibilidad con el fallback de matching por
     * (day, type, targetId) en WeeklyPlanUseCase.
     */
    @Test
    fun migration8To9AddsWeeklyPlanSessionIdColumn() {
        helper.createDatabase(TEST_DB, 8).apply {
            insertV8StrengthSession()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            AppDatabase.MIGRATION_8_9,
        )
        migrated.query(
            "SELECT weekly_plan_session_id FROM workout_sessions WHERE id = 'session-strength-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
        }
        migrated.query(
            """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type='index' AND name='index_workout_sessions_weekly_plan_session_id'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.execSQL(
            "UPDATE workout_sessions SET weekly_plan_session_id = 'plan-1' WHERE id = 'session-strength-1'",
        )
        migrated.query(
            "SELECT weekly_plan_session_id FROM workout_sessions WHERE id = 'session-strength-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("plan-1", cursor.getString(0))
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.insertV7CardioSession() {
        execSQL(
            """
            INSERT INTO cardio_types (
                id, name_es, name_en, has_gps, icon_name, is_preset, is_archived
            ) VALUES (
                'run', 'Correr', 'Run', 1, 'directions_run', 1, 0
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO workout_sessions (
                id, type, start_time, completed
            ) VALUES (
                'cardio-1', 'CARDIO', 1700000000000, 0
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO cardio_sessions (
                id, session_id, cardio_type_id, mode, target_duration_sec,
                has_gps, source
            ) VALUES (
                'cardio-1', 'cardio-1', 'run', 'TIMER', 0, 1, 'GPS'
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertV8StrengthSession() {
        execSQL(
            """
            INSERT INTO workout_sessions (
                id, routine_id, type, start_time, end_time, completed
            ) VALUES (
                'session-strength-1', 'routine-1', 'STRENGTH', 1700000000000, 1700003600000, 1
            )
            """.trimIndent(),
        )
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

    private fun SupportSQLiteDatabase.insertV4WeeklyPlan(twoSessionsSameDay: Boolean = false) {
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
                type,
                routine_id,
                cardio_type_id,
                cardio_target_duration_sec,
                is_rest_day,
                notification_enabled,
                notification_time
            ) VALUES (
                'weekly_plan_1',
                1,
                'STRENGTH',
                'routine-1',
                NULL,
                NULL,
                0,
                1,
                '18:00'
            )
            """.trimIndent(),
        )
        if (twoSessionsSameDay) {
            execSQL(
                """
                INSERT INTO routines (
                    id,
                    name,
                    created_at,
                    updated_at,
                    is_archived
                ) VALUES (
                    'routine-2',
                    'Routine 2',
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
                    type,
                    routine_id,
                    cardio_type_id,
                    cardio_target_duration_sec,
                    is_rest_day,
                    notification_enabled,
                    notification_time
                ) VALUES (
                    'weekly_plan_2',
                    1,
                    'STRENGTH',
                    'routine-2',
                    NULL,
                    NULL,
                    0,
                    1,
                    '19:00'
                )
                """.trimIndent(),
            )
        }
    }

    private fun SupportSQLiteDatabase.insertV5WeeklyPlanWithCollidingOrderIndexes() {
        execSQL(
            """
            INSERT INTO routines (
                id,
                name,
                created_at,
                updated_at,
                is_archived
            ) VALUES
                ('routine-1', 'Routine 1', 1700000000000, 1700000000000, 0),
                ('routine-2', 'Routine 2', 1700000000000, 1700000000000, 0)
            """.trimIndent(),
        )
        execSQL("DROP INDEX IF EXISTS index_weekly_plan_day_of_week_order_index")
        execSQL(
            """
            INSERT INTO weekly_plan (
                id,
                day_of_week,
                order_index,
                type,
                routine_id,
                cardio_type_id,
                cardio_target_duration_sec,
                is_rest_day,
                notification_enabled,
                notification_time
            ) VALUES
                ('weekly_plan_1', 1, 0, 'STRENGTH', 'routine-1', NULL, NULL, 0, 1, '18:00'),
                ('weekly_plan_2', 1, 0, 'STRENGTH', 'routine-2', NULL, NULL, 0, 1, '19:00')
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertV6CardioSession() {
        execSQL(
            """
            INSERT INTO workout_sessions (
                id, type, start_time, completed
            ) VALUES (
                'session-cardio-1', 'CARDIO', 1700000000000, 0
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DB = "atlas-peak-migration-test"
    }
}
