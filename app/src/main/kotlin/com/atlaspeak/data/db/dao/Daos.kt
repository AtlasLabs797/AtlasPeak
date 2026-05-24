package com.atlaspeak.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.AuthSecurityEntity
import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.data.db.entity.CardioSessionEntity
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
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.data.db.entity.WorkoutSetEntity

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
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSets(sets: List<WorkoutSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND type = 'STRENGTH'")
    suspend fun getStrengthSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions ORDER BY start_time DESC")
    suspend fun getSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE type = 'STRENGTH' ORDER BY start_time DESC")
    suspend fun getStrengthSessions(): List<WorkoutSessionEntity>

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM workout_sets WHERE session_id = :sessionId ORDER BY exercise_id, set_number")
    suspend fun getSets(sessionId: String): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query(
        """
        SELECT MAX(workout_sets.weight_kg)
        FROM workout_sets
        INNER JOIN workout_sessions ON workout_sets.session_id = workout_sessions.id
        WHERE workout_sets.exercise_id = :exerciseId
          AND workout_sets.completed = 1
          AND workout_sessions.start_time < :before
          AND workout_sets.weight_kg IS NOT NULL
        """,
    )
    suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double?

    @Query(
        """
        UPDATE workout_sessions
        SET end_time = :endTime,
            duration_seconds = :durationSeconds,
            total_volume_kg = :totalVolumeKg,
            completed = 1
        WHERE id = :sessionId
        """,
    )
    suspend fun updateSessionCompletion(
        sessionId: String,
        endTime: Long,
        durationSeconds: Int,
        totalVolumeKg: Double,
    )
}

@Dao
interface CardioDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardioTypes(types: List<CardioTypeEntity>)

    @Query("SELECT COUNT(*) FROM cardio_types WHERE is_preset = 1")
    suspend fun countPresetCardioTypes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardioType(type: CardioTypeEntity)

    @Query("SELECT * FROM cardio_types WHERE (:includeArchived = 1 OR is_archived = 0)")
    suspend fun getCardioTypes(includeArchived: Boolean = false): List<CardioTypeEntity>

    @Query("SELECT * FROM cardio_types WHERE id = :id")
    suspend fun getCardioType(id: String): CardioTypeEntity?

    @Query("UPDATE cardio_types SET is_archived = 1 WHERE id = :id AND is_preset = 0")
    suspend fun archiveCustomType(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardioSession(session: CardioSessionEntity)

    @Query("SELECT * FROM cardio_sessions WHERE id = :id")
    suspend fun getCardioSession(id: String): CardioSessionEntity?

    @Query("DELETE FROM cardio_sessions WHERE id = :id")
    suspend fun deleteCardioSession(id: String)

    @Query(
        """
        SELECT cardio_sessions.*
        FROM cardio_sessions
        INNER JOIN workout_sessions ON cardio_sessions.session_id = workout_sessions.id
        ORDER BY workout_sessions.start_time DESC
        """,
    )
    suspend fun getCardioSessions(): List<CardioSessionEntity>
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

data class DashboardPointRow(
    val timestamp: Long,
    val value: Double,
)

data class DashboardIntervalRow(
    val startTime: Long,
    val endTime: Long,
    val value: Double,
)

data class DashboardSessionRow(
    val startTime: Long,
    val durationSeconds: Int?,
    val totalVolumeKg: Double?,
)

@Dao
interface DashboardDao {
    @Query(
        """
        SELECT start_time AS startTime, duration_seconds AS durationSeconds, total_volume_kg AS totalVolumeKg
        FROM workout_sessions
        WHERE type = 'STRENGTH'
          AND completed = 1
          AND start_time >= :startInclusive
          AND start_time < :endExclusive
        ORDER BY start_time
        """,
    )
    suspend fun getCompletedStrengthSessions(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardSessionRow>

    @Query(
        """
        SELECT start_time AS startTime, duration_seconds AS durationSeconds, total_volume_kg AS totalVolumeKg
        FROM workout_sessions
        WHERE type = 'CARDIO'
          AND completed = 1
          AND start_time >= :startInclusive
          AND start_time < :endExclusive
        ORDER BY start_time
        """,
    )
    suspend fun getCompletedCardioSessions(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardSessionRow>

    @Query(
        """
        SELECT measured_at AS timestamp, weight_kg AS value
        FROM body_composition
        WHERE measured_at >= :startInclusive
          AND measured_at < :endExclusive
          AND weight_kg IS NOT NULL
        ORDER BY measured_at
        """,
    )
    suspend fun getBodyWeightPoints(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardPointRow>

    @Query(
        """
        SELECT start_time AS startTime, end_time AS endTime, count AS value
        FROM hc_steps_records
        WHERE start_time < :endExclusive
          AND end_time > :startInclusive
        ORDER BY start_time
        """,
    )
    suspend fun getStepIntervals(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardIntervalRow>

    @Query(
        """
        SELECT sampled_at AS timestamp, bpm AS value
        FROM hc_heart_rate_samples
        WHERE sampled_at >= :startInclusive
          AND sampled_at < :endExclusive
        ORDER BY sampled_at
        """,
    )
    suspend fun getHeartRateSamples(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardPointRow>

    @Query(
        """
        SELECT start_time AS startTime, end_time AS endTime, 0.0 AS value
        FROM hc_sleep_sessions
        WHERE start_time < :endExclusive
          AND end_time > :startInclusive
        ORDER BY start_time
        """,
    )
    suspend fun getSleepIntervals(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardIntervalRow>

    @Query(
        """
        SELECT day_of_week
        FROM weekly_plan
        WHERE is_rest_day = 0 AND routine_id IS NOT NULL
        ORDER BY day_of_week
        """,
    )
    suspend fun getPlannedTrainingDays(): List<Int>
}
