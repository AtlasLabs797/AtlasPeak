package com.atlaspeak.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.AuthSecurityEntity
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

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getLocalUser(): UserEntity?

    @Query("UPDATE users SET last_login_at = :lastLoginAt WHERE id = :id")
    suspend fun updateLastLoginAt(id: String, lastLoginAt: Long)
}

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?
}

@Dao
interface ReferenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroups(groups: List<MuscleGroupEntity>)

    @Query("SELECT COUNT(*) FROM muscle_groups")
    suspend fun countMuscleGroups(): Int

    @Query("SELECT * FROM muscle_groups ORDER BY id")
    suspend fun getMuscleGroups(): List<MuscleGroupEntity>
}

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Query("SELECT COUNT(*) FROM exercises WHERE is_preset = 1")
    suspend fun countPresetExercises(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE (:includeArchived = 1 OR is_archived = 0)")
    suspend fun getExercises(includeArchived: Boolean = false): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: String): ExerciseEntity?

    @Query("UPDATE exercises SET is_archived = 1 WHERE id = :id AND is_preset = 0")
    suspend fun archiveCustomExercise(id: String)
}

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routine_id = :routineId")
    suspend fun deleteRoutineExercises(routineId: String)

    @Query("SELECT * FROM routines WHERE (:includeArchived = 1 OR is_archived = 0)")
    suspend fun getRoutines(includeArchived: Boolean = false): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routine_exercises WHERE routine_id = :routineId ORDER BY order_index")
    suspend fun getRoutineExercises(routineId: String): List<RoutineExerciseEntity>

    @Query("UPDATE routines SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archiveRoutine(id: String, updatedAt: Long)
}

@Dao
interface CardioDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardioTypes(types: List<CardioTypeEntity>)

    @Query("SELECT COUNT(*) FROM cardio_types WHERE is_preset = 1")
    suspend fun countPresetCardioTypes(): Int
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Query("UPDATE app_settings SET biometrics_enabled = :enabled WHERE id = 1")
    suspend fun updateBiometricsEnabled(enabled: Boolean)
}

@Dao
interface AuthSecurityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuthSecurity(authSecurity: AuthSecurityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthSecurity(authSecurity: AuthSecurityEntity)

    @Query("SELECT * FROM auth_security WHERE id = 1")
    suspend fun getAuthSecurity(): AuthSecurityEntity?
}

@Dao
interface HealthConnectDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncLogs(syncLogs: List<HcSyncLogEntity>)

    @Query("SELECT COUNT(*) FROM hc_sync_log")
    suspend fun countSyncLogs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(record: HcStepsRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveCalories(record: HcActiveCaloriesRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepSession(session: HcSleepSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepStage(stage: HcSleepStageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHeartRateSample(sample: HcHeartRateSampleEntity)

    @Query("SELECT COUNT(*) FROM hc_steps_records")
    suspend fun countStepRecords(): Int

    @Query("DELETE FROM hc_sleep_sessions WHERE id = :id")
    suspend fun deleteSleepSession(id: String)

    @Query("SELECT COUNT(*) FROM hc_sleep_stages")
    suspend fun countSleepStages(): Int
}
