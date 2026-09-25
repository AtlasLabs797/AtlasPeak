package com.atlaspeak.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atlaspeak.R
import com.atlaspeak.presentation.theme.LocalAtlasColors
import java.util.Locale

/**
 * Ejercicio preset -> drawable dedicado. Las 37 entradas cubren todos los
 * `preset_*` de SeedData.kt. Los ejercicios personalizados (UUID) no tienen
 * entrada y caen en el fallback de monograma de [ExerciseIcon].
 */
val presetExerciseIcons: Map<String, Int> = mapOf(
    "preset_bench_press" to R.drawable.ic_exercise_bench_press,
    "preset_incline_press" to R.drawable.ic_exercise_incline_press,
    "preset_push_up" to R.drawable.ic_exercise_push_up,
    "preset_pull_up" to R.drawable.ic_exercise_pull_up,
    "preset_barbell_row" to R.drawable.ic_exercise_barbell_row,
    "preset_lat_pulldown" to R.drawable.ic_exercise_lat_pulldown,
    "preset_squat" to R.drawable.ic_exercise_squat,
    "preset_deadlift" to R.drawable.ic_exercise_deadlift,
    "preset_lunge" to R.drawable.ic_exercise_lunge,
    "preset_leg_press" to R.drawable.ic_exercise_leg_press,
    "preset_overhead_press" to R.drawable.ic_exercise_overhead_press,
    "preset_lateral_raise" to R.drawable.ic_exercise_lateral_raise,
    "preset_face_pull" to R.drawable.ic_exercise_face_pull,
    "preset_barbell_curl" to R.drawable.ic_exercise_barbell_curl,
    "preset_hammer_curl" to R.drawable.ic_exercise_hammer_curl,
    "preset_triceps_pushdown" to R.drawable.ic_exercise_triceps_pushdown,
    "preset_dips" to R.drawable.ic_exercise_dips,
    "preset_plank" to R.drawable.ic_exercise_plank,
    "preset_hanging_leg_raise" to R.drawable.ic_exercise_hanging_leg_raise,
    "preset_hip_thrust" to R.drawable.ic_exercise_hip_thrust,
    "preset_calf_raise" to R.drawable.ic_exercise_calf_raise,
    "preset_burpee" to R.drawable.ic_exercise_burpee,
    "preset_back_squat" to R.drawable.ic_exercise_back_squat,
    "preset_romanian_deadlift" to R.drawable.ic_exercise_romanian_deadlift,
    "preset_dumbbell_lunge" to R.drawable.ic_exercise_dumbbell_lunge,
    "preset_seated_db_press" to R.drawable.ic_exercise_seated_db_press,
    "preset_cable_triceps_extension" to R.drawable.ic_exercise_cable_triceps_extension,
    "preset_goblet_squat" to R.drawable.ic_exercise_goblet_squat,
    "preset_hack_squat" to R.drawable.ic_exercise_hack_squat,
    "preset_leg_curl" to R.drawable.ic_exercise_leg_curl,
    "preset_ab_wheel" to R.drawable.ic_exercise_ab_wheel,
    "preset_side_plank" to R.drawable.ic_exercise_side_plank,
    "preset_incline_db_press" to R.drawable.ic_exercise_incline_db_press,
    "preset_neutral_pulldown" to R.drawable.ic_exercise_neutral_pulldown,
    "preset_cable_row" to R.drawable.ic_exercise_cable_row,
    "preset_one_arm_db_row" to R.drawable.ic_exercise_one_arm_db_row,
    "preset_triceps_dips" to R.drawable.ic_exercise_triceps_dips,
)

/**
 * Icon-badge suave (DESIGN.md §6.3) para un ejercicio: drawable dedicado si es
 * preset, o monograma del nombre si es un ejercicio personalizado sin icono.
 */
@Composable
fun ExerciseIcon(
    exerciseId: String,
    exerciseName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val atlasColors = LocalAtlasColors.current
    val drawableRes = presetExerciseIcons[exerciseId]
    PremiumIconBadge(modifier = modifier, size = size, filled = false) {
        if (drawableRes != null) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                modifier = Modifier.size(size * 0.7f),
                colorFilter = ColorFilter.tint(atlasColors.ink2),
            )
        } else {
            Text(
                text = exerciseMonogram(exerciseName),
                style = MaterialTheme.typography.labelLarge,
                color = atlasColors.ink2,
            )
        }
    }
}

/**
 * Deriva un monograma corto de 1-2 letras del nombre del ejercicio, en
 * mayusculas, para usarlo como fallback visual cuando no hay drawable
 * dedicado (ejercicios personalizados). Es texto derivado de datos del
 * usuario, no una cadena fija.
 */
internal fun exerciseMonogram(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val letters = when {
        words.isEmpty() -> return "?"
        words.size == 1 -> words[0].take(1)
        else -> words[0].take(1) + words[1].take(1)
    }
    return letters.uppercase(Locale.ROOT)
}
