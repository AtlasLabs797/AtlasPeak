package com.atlaspeak.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.atlaspeak.data.db.dao.AuthSecurityDao
import com.atlaspeak.data.db.dao.CardioDao
import com.atlaspeak.data.db.dao.ExerciseDao
import com.atlaspeak.data.db.dao.HealthConnectDao
import com.atlaspeak.data.db.dao.ReferenceDao
import com.atlaspeak.data.db.dao.RoutineDao
import com.atlaspeak.data.db.dao.SettingsDao
import com.atlaspeak.data.db.dao.UserDao
import com.atlaspeak.data.db.dao.UserProfileDao
import com.atlaspeak.data.db.dao.WorkoutDao
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.AuthSecurityEntity
import com.atlaspeak.data.db.entity.BodyCompositionEntity
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
    version = 1,
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
    abstract fun settingsDao(): SettingsDao
    abstract fun authSecurityDao(): AuthSecurityDao
    abstract fun healthConnectDao(): HealthConnectDao

    companion object {
        const val DATABASE_NAME = "atlas_peak.db"

        val V1_TABLES = setOf(
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
    }
}
