package com.atlaspeak.domain.model.healthconnect

enum class HealthConnectAvailability {
    Available,
    Unavailable,
    UpdateRequired,
}

data class HealthConnectSyncResult(
    val availability: HealthConnectAvailability,
    val missingPermissions: Boolean = false,
    val importedRecords: Int = 0,
    val exportedRecords: Int = 0,
    val failed: Boolean = false,
) {
    val successful: Boolean = availability == HealthConnectAvailability.Available && !missingPermissions && !failed
}
