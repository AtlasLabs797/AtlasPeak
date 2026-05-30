package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult

interface HealthConnectRepository {
    fun requiredPermissions(): Set<String>
    suspend fun availability(): HealthConnectAvailability
    suspend fun sync(): HealthConnectSyncResult
}
