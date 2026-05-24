package com.atlaspeak.domain.model.cardio

data class CardioType(
    val id: String,
    val name: String,
    val hasGps: Boolean,
    val isPreset: Boolean,
    val isArchived: Boolean,
)

sealed class CardioMode {
    data object Timer : CardioMode()
    data class Countdown(val targetDurationSeconds: Int) : CardioMode()
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
)

data class CardioSession(
    val id: String,
    val cardioTypeId: String,
    val cardioTypeName: String,
    val mode: CardioMode,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Int?,
    val distanceKm: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val caloriesBurned: Int?,
    val hasGps: Boolean,
    val route: List<LocationPoint>,
    val completed: Boolean,
)
