package com.atlaspeak.domain.usecase.healthconnect

import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.repository.HealthConnectRepository
import javax.inject.Inject

class SyncHealthConnectUseCase @Inject constructor(
    private val repository: HealthConnectRepository,
) {
    fun requiredPermissions(): Set<String> = repository.requiredPermissions()

    suspend fun availability(): HealthConnectAvailability = repository.availability()

    suspend operator fun invoke(): HealthConnectSyncResult {
        return runCatching { repository.sync() }.getOrElse {
            HealthConnectSyncResult(
                availability = HealthConnectAvailability.Available,
                failed = true,
            )
        }
    }
}
