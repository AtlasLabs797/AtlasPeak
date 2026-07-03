package com.atlaspeak.domain.model.healthconnect

enum class HealthConnectAvailability {
    Available,
    Unavailable,
    UpdateRequired,
}

enum class HealthConnectCapability {
    Steps,
    ActiveCalories,
    Sleep,
    HeartRate,
    BodyCompositionRead,
    WorkoutWrite,
    BodyCompositionWrite,
}

data class HealthConnectSyncResult(
    val availability: HealthConnectAvailability,
    val missingPermissions: Boolean = false,
    val importedRecords: Int = 0,
    val exportedRecords: Int = 0,
    val failed: Boolean = false,
    val completedCapabilities: Set<HealthConnectCapability> = emptySet(),
    val skippedCapabilities: Set<HealthConnectCapability> = emptySet(),
) {
    val successful: Boolean = availability == HealthConnectAvailability.Available && !missingPermissions && !failed
    val partiallySuccessful: Boolean =
        availability == HealthConnectAvailability.Available &&
            !failed &&
            completedCapabilities.isNotEmpty() &&
            skippedCapabilities.isNotEmpty()
}
