package com.atlaspeak.domain.model.profile

data class UserProfile(
    val displayName: String?,
    val age: Int?,
    val heightCm: Double?,
    val gender: Gender?,
    val goalType: Goal?,
)

enum class Gender(val storageValue: String) {
    MALE("male"),
    FEMALE("female"),
    OTHER("other"),
    UNSPECIFIED("unspecified");

    companion object {
        fun fromStorageValue(value: String?): Gender? = entries.firstOrNull { it.storageValue == value }
    }
}

enum class Goal(val storageValue: String) {
    FAT_LOSS("fat_loss"),
    MUSCLE_GAIN("muscle_gain"),
    MAINTENANCE("maintenance"),
    ENDURANCE("endurance");

    companion object {
        fun fromStorageValue(value: String?): Goal? = entries.firstOrNull { it.storageValue == value }
    }
}
