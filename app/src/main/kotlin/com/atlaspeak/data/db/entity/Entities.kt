package com.atlaspeak.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index(value = ["google_id"]), Index(value = ["email"])])
data class UserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "google_id") val googleId: String? = null,
    val email: String? = null,
    @ColumnInfo(name = "password_hash") val passwordHash: String? = null,
    @ColumnInfo(name = "password_salt") val passwordSalt: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_login_at") val lastLoginAt: Long? = null,
)

@Entity(
    tableName = "user_profile",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["user_id"], unique = true)],
)
data class UserProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    val age: Int? = null,
    @ColumnInfo(name = "height_cm") val heightCm: Double? = null,
    val gender: String? = null,
    @ColumnInfo(name = "goal_type") val goalType: String? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "muscle_groups")
data class MuscleGroupEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "name_es") val nameEs: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "icon_name") val iconName: String,
)

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = MuscleGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscle_group_id"],
        ),
        ForeignKey(
            entity = MuscleGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["secondary_muscle_group_id"],
        ),
    ],
    indices = [
        Index(value = ["muscle_group_id"]),
        Index(value = ["secondary_muscle_group_id"]),
        Index(value = ["is_preset"]),
        Index(value = ["is_archived"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_es") val nameEs: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "muscle_group_id") val muscleGroupId: Int,
    @ColumnInfo(name = "secondary_muscle_group_id") val secondaryMuscleGroupId: Int? = null,
    @ColumnInfo(name = "description_es") val descriptionEs: String? = null,
    @ColumnInfo(name = "description_en") val descriptionEn: String? = null,
    @ColumnInfo(name = "is_preset") val isPreset: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "routines", indices = [Index(value = ["is_archived"]), Index(value = ["updated_at"])])
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "color_tag") val colorTag: String? = null,
    @ColumnInfo(name = "estimated_duration_min") val estimatedDurationMin: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
        ),
    ],
    indices = [
        Index(value = ["routine_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["routine_id", "order_index"], unique = true),
    ],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val sets: Int,
    val reps: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int = 90,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    val notes: String? = null,
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["routine_id"]), Index(value = ["start_time"]), Index(value = ["end_time"])],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String? = null,
    val type: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    val notes: String? = null,
    val completed: Boolean = false,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Int? = null,
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double? = null,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
        ),
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["session_id", "exercise_id", "set_number"], unique = true),
        Index(value = ["completed_at"]),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "planned_reps") val plannedReps: Int,
    @ColumnInfo(name = "actual_reps") val actualReps: Int? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    val completed: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    val source: String = "PHONE",
    @ColumnInfo(name = "is_personal_record") val isPersonalRecord: Boolean = false,
)

@Entity(tableName = "cardio_types", indices = [Index(value = ["is_preset"]), Index(value = ["is_archived"])])
data class CardioTypeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_es") val nameEs: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "has_gps") val hasGps: Boolean,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "is_preset") val isPreset: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(
    tableName = "cardio_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CardioTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardio_type_id"],
        ),
    ],
    indices = [
        Index(value = ["session_id"], unique = true),
        Index(value = ["cardio_type_id"]),
    ],
)
data class CardioSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "cardio_type_id") val cardioTypeId: String,
    val mode: String,
    @ColumnInfo(name = "target_duration_sec") val targetDurationSec: Int,
    @ColumnInfo(name = "actual_duration_sec") val actualDurationSec: Int? = null,
    @ColumnInfo(name = "distance_km") val distanceKm: Double? = null,
    @ColumnInfo(name = "avg_speed_kmh") val avgSpeedKmh: Double? = null,
    @ColumnInfo(name = "max_speed_kmh") val maxSpeedKmh: Double? = null,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Int? = null,
    @ColumnInfo(name = "has_gps") val hasGps: Boolean = false,
    @ColumnInfo(name = "route_polyline_json") val routePolylineJson: String? = null,
    val source: String,
)

@Entity(tableName = "body_composition", indices = [Index(value = ["measured_at"]), Index(value = ["source"])])
data class BodyCompositionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    @ColumnInfo(name = "body_fat_percent") val bodyFatPercent: Double? = null,
    @ColumnInfo(name = "muscle_mass_kg") val muscleMassKg: Double? = null,
    @ColumnInfo(name = "water_percent") val waterPercent: Double? = null,
    @ColumnInfo(name = "body_water_mass_kg") val bodyWaterMassKg: Double? = null,
    @ColumnInfo(name = "visceral_fat_level") val visceralFatLevel: Int? = null,
    @ColumnInfo(name = "protein_percent") val proteinPercent: Double? = null,
    @ColumnInfo(name = "bone_mass_kg") val boneMassKg: Double? = null,
    @ColumnInfo(name = "body_age") val bodyAge: Int? = null,
    val source: String,
    @ColumnInfo(name = "synced_to_hc") val syncedToHc: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "weekly_plan",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["day_of_week", "order_index"], unique = true),
        Index(value = ["routine_id"]),
        Index(value = ["cardio_type_id"]),
    ],
)
data class WeeklyPlanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
    val type: String = "STRENGTH",
    @ColumnInfo(name = "routine_id") val routineId: String? = null,
    @ColumnInfo(name = "cardio_type_id") val cardioTypeId: String? = null,
    @ColumnInfo(name = "cardio_target_duration_sec") val cardioTargetDurationSec: Int? = null,
    @ColumnInfo(name = "is_rest_day") val isRestDay: Boolean = false,
    @ColumnInfo(name = "notification_enabled") val notificationEnabled: Boolean = true,
    @ColumnInfo(name = "notification_time") val notificationTime: String? = null,
)

@Entity(tableName = "hc_sync_log", indices = [Index(value = ["data_type"], unique = true)])
data class HcSyncLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "data_type") val dataType: String,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long? = null,
    @ColumnInfo(name = "last_write_at") val lastWriteAt: Long? = null,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "notifications_enabled") val notificationsEnabled: Boolean = true,
    @ColumnInfo(name = "motivational_messages") val motivationalMessages: Boolean = true,
    @ColumnInfo(name = "daily_summary_enabled") val dailySummaryEnabled: Boolean = true,
    @ColumnInfo(name = "daily_summary_time") val dailySummaryTime: String = "08:30",
    @ColumnInfo(name = "weekly_summary_enabled") val weeklySummaryEnabled: Boolean = true,
    @ColumnInfo(name = "biometrics_enabled") val biometricsEnabled: Boolean = false,
    @ColumnInfo(name = "biometric_timeout_min") val biometricTimeoutMin: Int = 5,
    val theme: String = "SYSTEM",
    val language: String = "SYSTEM",
    @ColumnInfo(name = "rest_sound_enabled") val restSoundEnabled: Boolean = true,
    @ColumnInfo(name = "rest_vibration_enabled") val restVibrationEnabled: Boolean = true,
    @ColumnInfo(name = "last_backup_at") val lastBackupAt: Long? = null,
    @ColumnInfo(name = "backup_auto_enabled") val backupAutoEnabled: Boolean = true,
)

@Entity(tableName = "auth_security")
data class AuthSecurityEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "failed_attempts") val failedAttempts: Int = 0,
    @ColumnInfo(name = "locked_until") val lockedUntil: Long? = null,
)

@Entity(
    tableName = "hc_steps_records",
    indices = [
        Index(value = ["hc_record_id"], unique = true),
        Index(value = ["start_time"]),
        Index(value = ["end_time"]),
        Index(value = ["source_package"]),
        Index(value = ["last_modified_at"]),
    ],
)
data class HcStepsRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "recording_method") val recordingMethod: Int? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "start_zone_offset") val startZoneOffset: String? = null,
    @ColumnInfo(name = "end_zone_offset") val endZoneOffset: String? = null,
    val count: Long,
)

@Entity(
    tableName = "hc_active_calories_records",
    indices = [
        Index(value = ["hc_record_id"], unique = true),
        Index(value = ["start_time"]),
        Index(value = ["end_time"]),
        Index(value = ["source_package"]),
        Index(value = ["last_modified_at"]),
    ],
)
data class HcActiveCaloriesRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "recording_method") val recordingMethod: Int? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "start_zone_offset") val startZoneOffset: String? = null,
    @ColumnInfo(name = "end_zone_offset") val endZoneOffset: String? = null,
    val kilocalories: Double,
)

@Entity(
    tableName = "hc_sleep_sessions",
    indices = [
        Index(value = ["hc_record_id"], unique = true),
        Index(value = ["start_time"]),
        Index(value = ["end_time"]),
        Index(value = ["source_package"]),
        Index(value = ["last_modified_at"]),
    ],
)
data class HcSleepSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "recording_method") val recordingMethod: Int? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "start_zone_offset") val startZoneOffset: String? = null,
    @ColumnInfo(name = "end_zone_offset") val endZoneOffset: String? = null,
    val title: String? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "hc_sleep_stages",
    foreignKeys = [
        ForeignKey(
            entity = HcSleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sleep_session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sleep_session_id"]),
        Index(value = ["hc_record_id"], unique = true),
        Index(value = ["start_time"]),
        Index(value = ["end_time"]),
        Index(value = ["source_package"]),
        Index(value = ["last_modified_at"]),
    ],
)
data class HcSleepStageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sleep_session_id") val sleepSessionId: String,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "recording_method") val recordingMethod: Int? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "stage_type") val stageType: Int,
)

@Entity(
    tableName = "hc_heart_rate_samples",
    indices = [
        Index(value = ["hc_record_id", "sampled_at"], unique = true),
        Index(value = ["sampled_at"]),
        Index(value = ["source_package"]),
        Index(value = ["last_modified_at"]),
    ],
)
data class HcHeartRateSampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long,
    @ColumnInfo(name = "recording_method") val recordingMethod: Int? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "sampled_at") val sampledAt: Long,
    val bpm: Long,
)
