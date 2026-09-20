package com.atlaspeak.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.atlaspeak.data.db.dao.AuthSecurityDao
import com.atlaspeak.data.db.dao.BodyCompositionDao
import com.atlaspeak.data.db.dao.CardioDao
import com.atlaspeak.data.db.dao.CardioRoutePointDao
import com.atlaspeak.data.db.dao.DashboardDao
import com.atlaspeak.data.db.dao.ExerciseDao
import com.atlaspeak.data.db.dao.HealthConnectDao
import com.atlaspeak.data.db.dao.ReferenceDao
import com.atlaspeak.data.db.dao.RoutineDao
import com.atlaspeak.data.db.dao.SettingsDao
import com.atlaspeak.data.db.dao.UserDao
import com.atlaspeak.data.db.dao.UserProfileDao
import com.atlaspeak.data.db.dao.WeeklyPlanDao
import com.atlaspeak.data.db.dao.WorkoutDao
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.AuthSecurityEntity
import com.atlaspeak.data.db.entity.BodyCompositionEntity
import com.atlaspeak.data.db.entity.CardioRoutePointEntity
import com.atlaspeak.data.db.entity.CardioSessionEntity
import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.data.db.entity.ExerciseEntity
import com.atlaspeak.data.db.entity.HcActiveCaloriesRecordEntity
import com.atlaspeak.data.db.entity.HcHeartRateSampleEntity
import com.atlaspeak.data.db.entity.HcSleepSessionEntity
import com.atlaspeak.data.db.entity.HcSleepStageEntity
import com.atlaspeak.data.db.entity.HcStepsRecordEntity
import com.atlaspeak.data.db.entity.HcSyncLogEntity
import com.atlaspeak.data.db.entity.MuscleGroupEntity
import com.atlaspeak.data.db.entity.RoutineEntity
import com.atlaspeak.data.db.entity.RoutineExerciseEntity
import com.atlaspeak.data.db.entity.UserEntity
import com.atlaspeak.data.db.entity.UserProfileEntity
import com.atlaspeak.data.db.entity.WeeklyPlanEntity
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.data.db.entity.WorkoutSetEntity

@Database(
    entities = [
        UserEntity::class,
        UserProfileEntity::class,
        MuscleGroupEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        CardioTypeEntity::class,
        CardioSessionEntity::class,
        CardioRoutePointEntity::class,
        BodyCompositionEntity::class,
        WeeklyPlanEntity::class,
        HcSyncLogEntity::class,
        AppSettingsEntity::class,
        AuthSecurityEntity::class,
        HcStepsRecordEntity::class,
        HcActiveCaloriesRecordEntity::class,
        HcSleepSessionEntity::class,
        HcSleepStageEntity::class,
        HcHeartRateSampleEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun cardioDao(): CardioDao
    abstract fun cardioRoutePointDao(): CardioRoutePointDao
    abstract fun settingsDao(): SettingsDao
    abstract fun authSecurityDao(): AuthSecurityDao
    abstract fun bodyCompositionDao(): BodyCompositionDao
    abstract fun healthConnectDao(): HealthConnectDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun weeklyPlanDao(): WeeklyPlanDao

    companion object {
        const val DATABASE_NAME = "atlas_peak.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE body_composition ADD COLUMN body_water_mass_kg REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users_new (
                        id TEXT NOT NULL,
                        google_id TEXT,
                        email TEXT,
                        password_hash TEXT,
                        password_salt TEXT,
                        created_at INTEGER NOT NULL,
                        last_login_at INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO users_new (
                        id,
                        google_id,
                        email,
                        password_hash,
                        password_salt,
                        created_at,
                        last_login_at
                    )
                    SELECT
                        id,
                        google_id,
                        email,
                        NULL,
                        NULL,
                        created_at,
                        NULL
                    FROM users
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile_backup (
                        id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        display_name TEXT,
                        age INTEGER,
                        height_cm REAL,
                        gender TEXT,
                        goal_type TEXT,
                        photo_uri TEXT,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO user_profile_backup (
                        id,
                        user_id,
                        display_name,
                        age,
                        height_cm,
                        gender,
                        goal_type,
                        photo_uri,
                        updated_at
                    )
                    SELECT
                        id,
                        user_id,
                        display_name,
                        age,
                        height_cm,
                        gender,
                        goal_type,
                        photo_uri,
                        updated_at
                    FROM user_profile
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE user_profile")
                db.execSQL("DROP TABLE users")
                db.execSQL("ALTER TABLE users_new RENAME TO users")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_google_id ON users(google_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_email ON users(email)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        display_name TEXT,
                        age INTEGER,
                        height_cm REAL,
                        gender TEXT,
                        goal_type TEXT,
                        photo_uri TEXT,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(user_id) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO user_profile (
                        id,
                        user_id,
                        display_name,
                        age,
                        height_cm,
                        gender,
                        goal_type,
                        photo_uri,
                        updated_at
                    )
                    SELECT
                        id,
                        user_id,
                        display_name,
                        age,
                        height_cm,
                        gender,
                        goal_type,
                        photo_uri,
                        updated_at
                    FROM user_profile_backup
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE user_profile_backup")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_profile_user_id ON user_profile(user_id)")
                db.execSQL("UPDATE app_settings SET biometrics_enabled = 0")
                db.execSQL("UPDATE auth_security SET failed_attempts = 0, locked_until = NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekly_plan ADD COLUMN type TEXT NOT NULL DEFAULT 'STRENGTH'")
                db.execSQL("ALTER TABLE weekly_plan ADD COLUMN cardio_type_id TEXT")
                db.execSQL("ALTER TABLE weekly_plan ADD COLUMN cardio_target_duration_sec INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekly_plan_new (
                        id TEXT NOT NULL,
                        day_of_week INTEGER NOT NULL,
                        order_index INTEGER NOT NULL DEFAULT 0,
                        type TEXT NOT NULL DEFAULT 'STRENGTH',
                        routine_id TEXT,
                        cardio_type_id TEXT,
                        cardio_target_duration_sec INTEGER,
                        is_rest_day INTEGER NOT NULL DEFAULT 0,
                        notification_enabled INTEGER NOT NULL DEFAULT 1,
                        notification_time TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(routine_id) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO weekly_plan_new (
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
                    )
                    SELECT
                        id,
                        day_of_week,
                        ROW_NUMBER() OVER (PARTITION BY day_of_week ORDER BY id) - 1,
                        type,
                        routine_id,
                        cardio_type_id,
                        cardio_target_duration_sec,
                        is_rest_day,
                        notification_enabled,
                        notification_time
                    FROM weekly_plan
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE weekly_plan")
                db.execSQL("ALTER TABLE weekly_plan_new RENAME TO weekly_plan")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_weekly_plan_day_of_week_order_index
                    ON weekly_plan(day_of_week, order_index)
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekly_plan_routine_id ON weekly_plan(routine_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekly_plan_cardio_type_id ON weekly_plan(cardio_type_id)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekly_plan_reindexed (
                        id TEXT NOT NULL,
                        day_of_week INTEGER NOT NULL,
                        order_index INTEGER NOT NULL DEFAULT 0,
                        type TEXT NOT NULL DEFAULT 'STRENGTH',
                        routine_id TEXT,
                        cardio_type_id TEXT,
                        cardio_target_duration_sec INTEGER,
                        is_rest_day INTEGER NOT NULL DEFAULT 0,
                        notification_enabled INTEGER NOT NULL DEFAULT 1,
                        notification_time TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(routine_id) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO weekly_plan_reindexed (
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
                    )
                    SELECT
                        id,
                        day_of_week,
                        ROW_NUMBER() OVER (PARTITION BY day_of_week ORDER BY order_index, id) - 1,
                        type,
                        routine_id,
                        cardio_type_id,
                        cardio_target_duration_sec,
                        is_rest_day,
                        notification_enabled,
                        notification_time
                    FROM weekly_plan
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE weekly_plan")
                db.execSQL("ALTER TABLE weekly_plan_reindexed RENAME TO weekly_plan")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_weekly_plan_day_of_week_order_index
                    ON weekly_plan(day_of_week, order_index)
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekly_plan_routine_id ON weekly_plan(routine_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekly_plan_cardio_type_id ON weekly_plan(cardio_type_id)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cardio_route_points (
                        id TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        timestamp_ms INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy_m REAL,
                        speed_kmh REAL,
                        distance_from_previous_km REAL NOT NULL DEFAULT 0.0,
                        PRIMARY KEY(id),
                        FOREIGN KEY(session_id) REFERENCES workout_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cardio_route_points_session_id " +
                        "ON cardio_route_points(session_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cardio_route_points_session_id_timestamp_ms " +
                        "ON cardio_route_points(session_id, timestamp_ms)",
                )
            }
        }

        // BUG-094 (Fase 5 P1): soporte de pausa/reanudacion para sesiones de
        // cardio activas. Anadimos `paused_at_ms` (INTEGER nullable, ausente =>
        // sesion no pausada) y `total_paused_duration_ms` (INTEGER NOT NULL
        // con DEFAULT 0 para que las filas existentes la rellenen sin rewrite
        // de la tabla). No alteramos `start_time`: la pausa se descuenta en
        // lectura (`effectiveElapsedSeconds`) sin falsificar el origen.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cardio_sessions ADD COLUMN paused_at_ms INTEGER")
                db.execSQL(
                    "ALTER TABLE cardio_sessions ADD COLUMN total_paused_duration_ms " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        // BUG-097 (Fase 8 P1): anadimos `weekly_plan_session_id` a
        // `workout_sessions` para que las sesiones iniciadas desde el plan
        // semanal apunten al row concreto de `weekly_plan` que las origino.
        // Asi, la regla "completar" se aplica por entrada individual y no
        // por (day, type, targetId), que es lo que producia el bug de las dos
        // sesiones del mismo dia marcadas a la vez.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_sessions ADD COLUMN weekly_plan_session_id TEXT",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_sessions_weekly_plan_session_id " +
                        "ON workout_sessions(weekly_plan_session_id)",
                )
            }
        }

        val TABLE_ORDER = listOf(
            "users",
            "user_profile",
            "muscle_groups",
            "exercises",
            "routines",
            "routine_exercises",
            "workout_sessions",
            "workout_sets",
            "cardio_types",
            "cardio_sessions",
            "cardio_route_points",
            "body_composition",
            "weekly_plan",
            "hc_sync_log",
            "app_settings",
            "auth_security",
            "hc_steps_records",
            "hc_active_calories_records",
            "hc_sleep_sessions",
            "hc_sleep_stages",
            "hc_heart_rate_samples",
        )

        val TABLES = TABLE_ORDER.toSet()

        val V1_TABLES = TABLES
    }
}
