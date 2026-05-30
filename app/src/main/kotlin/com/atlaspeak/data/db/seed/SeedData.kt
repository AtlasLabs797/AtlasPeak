package com.atlaspeak.data.db.seed

import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.data.db.entity.ExerciseEntity
import com.atlaspeak.data.db.entity.HcSyncLogEntity
import com.atlaspeak.data.db.entity.MuscleGroupEntity

object SeedData {
    private const val CREATED_AT = 1_700_000_000_000L

    val muscleGroups = listOf(
        MuscleGroupEntity(1, "Pecho", "Chest", "fitness_center"),
        MuscleGroupEntity(2, "Espalda", "Back", "accessibility_new"),
        MuscleGroupEntity(3, "Piernas", "Legs", "directions_run"),
        MuscleGroupEntity(4, "Hombros", "Shoulders", "sports_gymnastics"),
        MuscleGroupEntity(5, "Biceps", "Biceps", "fitness_center"),
        MuscleGroupEntity(6, "Triceps", "Triceps", "fitness_center"),
        MuscleGroupEntity(7, "Core", "Core", "self_improvement"),
        MuscleGroupEntity(8, "Gluteos", "Glutes", "directions_run"),
        MuscleGroupEntity(9, "Gemelos", "Calves", "directions_walk"),
        MuscleGroupEntity(10, "Cuerpo completo", "Full body", "sports_martial_arts"),
    )

    val exercises = listOf(
        ExerciseEntity("preset_bench_press", "Press banca", "Bench press", 1, 6, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_incline_press", "Press inclinado", "Incline press", 1, 4, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_push_up", "Flexiones", "Push-up", 1, 6, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_pull_up", "Dominadas", "Pull-up", 2, 5, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_barbell_row", "Remo con barra", "Barbell row", 2, 5, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_lat_pulldown", "Jalon al pecho", "Lat pulldown", 2, 5, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_squat", "Sentadilla", "Squat", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_deadlift", "Peso muerto", "Deadlift", 3, 2, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_lunge", "Zancadas", "Lunges", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_leg_press", "Prensa", "Leg press", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_overhead_press", "Press militar", "Overhead press", 4, 6, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_lateral_raise", "Elevaciones laterales", "Lateral raise", 4, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_face_pull", "Face pull", "Face pull", 4, 2, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_barbell_curl", "Curl con barra", "Barbell curl", 5, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_hammer_curl", "Curl martillo", "Hammer curl", 5, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_triceps_pushdown", "Extension de triceps", "Triceps pushdown", 6, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_dips", "Fondos", "Dips", 6, 1, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_plank", "Plancha", "Plank", 7, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_hanging_leg_raise", "Elevacion de piernas", "Hanging leg raise", 7, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_hip_thrust", "Hip thrust", "Hip thrust", 8, 3, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_calf_raise", "Elevacion de gemelos", "Calf raise", 9, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_burpee", "Burpee", "Burpee", 10, null, isPreset = true, createdAt = CREATED_AT),
    )

    val cardioTypes = listOf(
        CardioTypeEntity("cardio_running_outdoor", "Carrera exterior", "Outdoor running", true, "directions_run", true),
        CardioTypeEntity("cardio_cycling_outdoor", "Ciclismo exterior", "Outdoor cycling", true, "directions_bike", true),
        CardioTypeEntity("cardio_treadmill", "Cinta", "Treadmill", false, "directions_run", true),
        CardioTypeEntity("cardio_static_bike", "Bici estatica", "Stationary bike", false, "pedal_bike", true),
        CardioTypeEntity("cardio_elliptical", "Eliptica", "Elliptical", false, "fitness_center", true),
        CardioTypeEntity("cardio_rowing", "Remo", "Rowing", false, "rowing", true),
        CardioTypeEntity("cardio_swimming", "Natacion", "Swimming", false, "pool", true),
    )

    val hcSyncLogs = listOf(
        HcSyncLogEntity("hc_sync_steps", "STEPS"),
        HcSyncLogEntity("hc_sync_active_calories", "ACTIVE_CALORIES"),
        HcSyncLogEntity("hc_sync_sleep", "SLEEP"),
        HcSyncLogEntity("hc_sync_heart_rate", "HEART_RATE"),
        HcSyncLogEntity("hc_sync_workouts", "WORKOUTS"),
        HcSyncLogEntity("hc_sync_body_comp", "BODY_COMP"),
    )
}
