package com.atlaspeak.presentation.backup

import com.atlaspeak.R
import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.BackupStatus
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.domain.repository.BackupRepository
import com.atlaspeak.domain.usecase.backup.BackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `plaintext json export does not require entry password`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.requestExportJson()
        assertEquals(CleartextExportType.Json, viewModel.state.value.pendingCleartextExport)
        viewModel.confirmCleartextExport()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, backupRepository.manualJsonCalls)
        assertEquals(null, viewModel.state.value.pendingCleartextExport)
        assertEquals(R.string.backup_export_created, viewModel.state.value.messageRes)
    }

    @Test
    fun `plaintext csv export leaves backup password state alone`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("backup passphrase")
        viewModel.requestExportCsv()
        assertEquals(CleartextExportType.Csv, viewModel.state.value.pendingCleartextExport)
        viewModel.confirmCleartextExport()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, backupRepository.csvCalls)
        assertEquals("backup passphrase", viewModel.state.value.password)
        assertEquals(R.string.backup_export_created, viewModel.state.value.messageRes)
    }

    private fun viewModel(backupRepository: FakeBackupRepository): BackupRestoreViewModel {
        return BackupRestoreViewModel(
            backupUseCase = BackupUseCase(backupRepository),
        )
    }

    private class FakeBackupRepository : BackupRepository {
        var manualJsonCalls = 0
        var csvCalls = 0

        override suspend fun status(): BackupStatus = BackupStatus(
            autoBackupEnabled = false,
            lastBackupAt = null,
        )

        override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit

        override suspend fun saveAutoBackupPassword(password: CharArray) = Unit

        override suspend fun clearAutoBackupPassword() = Unit

        override suspend fun listDriveBackups(accessToken: String): List<DriveBackup> = emptyList()

        override suspend fun createDriveBackup(accessToken: String, password: CharArray): BackupResult =
            BackupResult.Success()

        override suspend fun restoreDriveBackup(accessToken: String, fileId: String, password: CharArray): BackupResult =
            BackupResult.Success()

        override suspend fun writeEncryptedBackup(password: CharArray): SharedBackupExport = export()

        override suspend fun writeManualJson(): SharedBackupExport {
            manualJsonCalls += 1
            return export()
        }

        override suspend fun writeCsvZip(): SharedBackupExport {
            csvCalls += 1
            return export()
        }

        private fun export(): SharedBackupExport = SharedBackupExport(
            uri = "content://atlas/export",
            mimeType = "application/json",
            fileName = "export.json",
        )
    }
}
