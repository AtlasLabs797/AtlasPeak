package com.atlaspeak.presentation.profile

import androidx.annotation.StringRes
import com.atlaspeak.R
import com.atlaspeak.domain.model.profile.Gender
import com.atlaspeak.domain.model.profile.Goal

@StringRes
fun Gender.labelRes(): Int = when (this) {
    Gender.MALE -> R.string.onboarding_gender_male
    Gender.FEMALE -> R.string.onboarding_gender_female
    Gender.OTHER -> R.string.onboarding_gender_other
    Gender.UNSPECIFIED -> R.string.onboarding_gender_unspecified
}

@StringRes
fun Goal.labelRes(): Int = when (this) {
    Goal.FAT_LOSS -> R.string.onboarding_goal_fat_loss
    Goal.MUSCLE_GAIN -> R.string.onboarding_goal_muscle_gain
    Goal.MAINTENANCE -> R.string.onboarding_goal_maintenance
    Goal.ENDURANCE -> R.string.onboarding_goal_endurance
}
