package com.atlaspeak.domain.usecase.privacy

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteAllUserDataUseCaseTest {
    @Test
    fun `deleting drive backups successfully returns Success`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = true)
        val useCase = DeleteAllUserDataUseCase(eraser)

        val result = useCase(deleteDriveBackups = true)

        assertEquals(DeleteAllUserDataResult.Success, result)
        assertEquals(1, eraser.cancelBackgroundWorkCalls)
        assertEquals(1, eraser.deleteDriveBackupsCalls)
        assertEquals(1, eraser.eraseLocalDataCalls)
    }

    @Test
    fun `skipping drive deletion returns Success without touching Drive`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = false)
        val useCase = DeleteAllUserDataUseCase(eraser)

        val result = useCase(deleteDriveBackups = false)

        assertEquals(DeleteAllUserDataResult.Success, result)
        assertEquals(0, eraser.deleteDriveBackupsCalls)
        assertEquals(1, eraser.eraseLocalDataCalls)
    }

    @Test
    fun `drive not authorized returns PartialSuccess but still erases local data`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = false)
        val useCase = DeleteAllUserDataUseCase(eraser)

        val result = useCase(deleteDriveBackups = true)

        assertEquals(DeleteAllUserDataResult.PartialSuccess(driveNotDeleted = true), result)
        assertEquals(1, eraser.eraseLocalDataCalls)
    }

    @Test
    fun `unexpected failure returns Failed`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = true, throwOnErase = true)
        val useCase = DeleteAllUserDataUseCase(eraser)

        val result = useCase(deleteDriveBackups = true)

        assertTrue(result is DeleteAllUserDataResult.Failed)
    }

    private class FakeUserDataEraser(
        private val driveDeletionSucceeds: Boolean,
        private val throwOnErase: Boolean = false,
    ) : UserDataEraser {
        var cancelBackgroundWorkCalls = 0
        var deleteDriveBackupsCalls = 0
        var eraseLocalDataCalls = 0

        override suspend fun cancelBackgroundWork() {
            cancelBackgroundWorkCalls += 1
        }

        override suspend fun deleteDriveBackups(): Boolean {
            deleteDriveBackupsCalls += 1
            return driveDeletionSucceeds
        }

        override suspend fun eraseLocalData() {
            eraseLocalDataCalls += 1
            if (throwOnErase) error("boom")
        }
    }
}
