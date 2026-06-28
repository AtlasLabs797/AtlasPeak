package com.atlaspeak.domain.usecase.healthconnect

import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectCapability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.repository.HealthConnectRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncHealthConnectUseCaseTest {
    private val repository = FakeHealthConnectRepository()
    private val useCase = SyncHealthConnectUseCase(repository)

    @Test
    fun `sync returns successful result when repository imports and exports`() = runTest {
        repository.result = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            importedRecords = 4,
            exportedRecords = 2,
        )

        val result = useCase()

        assertTrue(result.successful)
        assertEquals(4, result.importedRecords)
        assertEquals(2, result.exportedRecords)
    }

    @Test
    fun `sync reports missing permissions without crashing`() = runTest {
        repository.result = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            missingPermissions = true,
        )

        val result = useCase()

        assertFalse(result.successful)
        assertTrue(result.missingPermissions)
    }

    @Test
    fun `sync reports partial success when some capabilities ran`() = runTest {
        repository.result = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            missingPermissions = true,
            importedRecords = 3,
            completedCapabilities = setOf(HealthConnectCapability.BodyCompositionRead),
            skippedCapabilities = setOf(HealthConnectCapability.Sleep),
        )

        val result = useCase()

        assertFalse(result.successful)
        assertTrue(result.partiallySuccessful)
        assertEquals(3, result.importedRecords)
    }

    @Test
    fun `sync converts repository exception to failed result`() = runTest {
        repository.failure = IllegalStateException("health connect died")

        val result = useCase()

        assertFalse(result.successful)
        assertTrue(result.failed)
        assertEquals(HealthConnectAvailability.Available, result.availability)
    }

    @Test
    fun `required permissions are exposed from repository`() {
        repository.permissions = setOf("READ_STEPS", "WRITE_WEIGHT")

        assertEquals(repository.permissions, useCase.requiredPermissions())
    }

    private class FakeHealthConnectRepository : HealthConnectRepository {
        var permissions: Set<String> = emptySet()
        var result = HealthConnectSyncResult(HealthConnectAvailability.Unavailable)
        var failure: Throwable? = null

        override fun requiredPermissions(): Set<String> = permissions

        override suspend fun availability(): HealthConnectAvailability = result.availability

        override suspend fun sync(): HealthConnectSyncResult {
            failure?.let { throw it }
            return result
        }
    }
}
