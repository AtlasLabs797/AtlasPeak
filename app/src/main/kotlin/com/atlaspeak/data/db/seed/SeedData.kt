package com.atlaspeak.data.db.seed

import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.data.db.entity.ExerciseEntity
import com.atlaspeak.data.db.entity.HcSyncLogEntity
import com.atlaspeak.data.db.entity.MuscleGroupEntity
import com.atlaspeak.data.db.entity.RoutineEntity
import com.atlaspeak.data.db.entity.RoutineExerciseEntity
import com.atlaspeak.data.db.entity.WeeklyPlanEntity

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
        ExerciseEntity("preset_back_squat", "Sentadilla con barra", "Back squat", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_romanian_deadlift", "Peso muerto rumano", "Romanian deadlift", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_dumbbell_lunge", "Zancadas con mancuernas", "Dumbbell lunge", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_seated_db_press", "Press militar con mancuernas sentado", "Seated dumbbell shoulder press", 4, 6, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_cable_triceps_extension", "Extension triceps polea", "Cable triceps extension", 6, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_goblet_squat", "Sentadilla goblet", "Goblet squat", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_hack_squat", "Hack squat", "Hack squat", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_leg_curl", "Curl femoral maquina", "Machine leg curl", 3, 8, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_ab_wheel", "Rueda abdominal", "Ab wheel", 7, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_side_plank", "Plancha lateral", "Side plank", 7, null, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_incline_db_press", "Press inclinado con mancuernas", "Incline dumbbell press", 1, 4, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_neutral_pulldown", "Jalon agarre neutro", "Neutral-grip pulldown", 2, 5, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_cable_row", "Remo en polea", "Cable row", 2, 5, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_one_arm_db_row", "Remo con mancuerna", "One-arm dumbbell row", 2, 5, isPreset = true, createdAt = CREATED_AT),
        ExerciseEntity("preset_triceps_dips", "Fondos triceps", "Triceps dips", 6, 1, isPreset = true, createdAt = CREATED_AT),
    )

    val defaultRoutines = listOf(
        RoutineEntity("default_5day_lower_a", "Tren inferior A (sentadilla)", null, "#FFFFFF", 50, CREATED_AT, CREATED_AT),
        RoutineEntity("default_5day_upper_a", "Tren superior A", null, "#FFFFFF", 50, CREATED_AT, CREATED_AT),
        RoutineEntity("default_5day_lower_b", "Tren inferior B (peso muerto)", null, "#FFFFFF", 49, CREATED_AT, CREATED_AT),
        RoutineEntity("default_5day_upper_b", "Tren superior B", null, "#FFFFFF", 49, CREATED_AT, CREATED_AT),
    )

    val defaultRoutineExercises = listOf(
        routineExercise("default_5day_lower_a", 0, "preset_back_squat", 4, 5, 180, null),
        routineExercise("default_5day_lower_a", 1, "preset_romanian_deadlift", 3, 10, 90, "8-10 reps"),
        routineExercise("default_5day_lower_a", 2, "preset_leg_press", 3, 12, 90, "10-12 reps"),
        routineExercise("default_5day_lower_a", 3, "preset_dumbbell_lunge", 3, 10, 90, "10 por pierna"),
        routineExercise("default_5day_lower_a", 4, "preset_plank", 3, 45, 60, "30-45 s"),
        routineExercise("default_5day_upper_a", 0, "preset_bench_press", 4, 6, 180, "5-6 reps"),
        routineExercise("default_5day_upper_a", 1, "preset_barbell_row", 4, 8, 90, "6-8 reps; Pendlay o inclinado"),
        routineExercise("default_5day_upper_a", 2, "preset_seated_db_press", 3, 10, 90, "8-10 reps"),
        routineExercise("default_5day_upper_a", 3, "preset_lat_pulldown", 3, 12, 90, "10-12 reps"),
        routineExercise("default_5day_upper_a", 4, "preset_barbell_curl", 3, 12, 60, "Superserie con extension triceps polea"),
        routineExercise("default_5day_upper_a", 5, "preset_cable_triceps_extension", 3, 12, 60, "Superserie con curl biceps"),
        routineExercise("default_5day_lower_b", 0, "preset_deadlift", 4, 5, 180, null),
        routineExercise("default_5day_lower_b", 1, "preset_goblet_squat", 3, 10, 90, "8-10 reps; alternativa: hack squat"),
        routineExercise("default_5day_lower_b", 2, "preset_hip_thrust", 3, 12, 90, "10-12 reps"),
        routineExercise("default_5day_lower_b", 3, "preset_leg_curl", 3, 12, 90, null),
        routineExercise("default_5day_lower_b", 4, "preset_ab_wheel", 3, 12, 60, "10-12 reps; alternativa: plancha lateral 30 s/lado"),
        routineExercise("default_5day_upper_b", 0, "preset_incline_db_press", 4, 10, 90, "8-10 reps"),
        routineExercise("default_5day_upper_b", 1, "preset_neutral_pulldown", 4, 10, 90, "8-10 reps; alternativa: dominadas asistidas"),
        routineExercise("default_5day_upper_b", 2, "preset_overhead_press", 3, 8, 120, "6-8 reps"),
        routineExercise("default_5day_upper_b", 3, "preset_cable_row", 3, 12, 90, "10-12 reps; alternativa: remo mancuerna"),
        routineExercise("default_5day_upper_b", 4, "preset_hammer_curl", 3, 12, 60, "Superserie con fondos triceps"),
        routineExercise("default_5day_upper_b", 5, "preset_triceps_dips", 3, 12, 60, "Superserie con curl martillo"),
    )

    val defaultWeeklyPlan = listOf(
        strengthDay(1, "default_5day_lower_a"),
        strengthDay(2, "default_5day_upper_a"),
        WeeklyPlanEntity(
            id = "weekly_plan_3",
            dayOfWeek = 3,
            type = "CARDIO",
            routineId = null,
            cardioTypeId = "cardio_static_bike",
            cardioTargetDurationSec = 45 * 60,
            isRestDay = false,
            notificationEnabled = true,
            notificationTime = "18:00",
        ),
        strengthDay(4, "default_5day_lower_b"),
        strengthDay(5, "default_5day_upper_b"),
        restDay(6),
        restDay(7),
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

    private fun routineExercise(
        routineId: String,
        orderIndex: Int,
        exerciseId: String,
        sets: Int,
        reps: Int,
        restSeconds: Int,
        notes: String?,
    ) = RoutineExerciseEntity(
        id = "${routineId}_$orderIndex",
        routineId = routineId,
        exerciseId = exerciseId,
        sets = sets,
        reps = reps,
        weightKg = null,
        restSeconds = restSeconds,
        orderIndex = orderIndex,
        notes = notes,
    )

    private fun strengthDay(dayOfWeek: Int, routineId: String) = WeeklyPlanEntity(
        id = "weekly_plan_$dayOfWeek",
        dayOfWeek = dayOfWeek,
        type = "STRENGTH",
        routineId = routineId,
        cardioTypeId = null,
        cardioTargetDurationSec = null,
        isRestDay = false,
        notificationEnabled = true,
        notificationTime = "18:00",
    )

    private fun restDay(dayOfWeek: Int) = WeeklyPlanEntity(
        id = "weekly_plan_$dayOfWeek",
        dayOfWeek = dayOfWeek,
        type = "STRENGTH",
        routineId = null,
        cardioTypeId = null,
        cardioTargetDurationSec = null,
        isRestDay = true,
        notificationEnabled = false,
        notificationTime = null,
    )
}
