package com.atlaspeak.presentation.backup

import com.atlaspeak.R
import com.atlaspeak.domain.model.backup.BackupFailure
import com.atlaspeak.domain.model.backup.BackupHealthStatus
import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.BackupStatus
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.domain.repository.BackupRepository
import com.atlaspeak.domain.usecase.backup.BackupPassphrasePolicy
import com.atlaspeak.domain.usecase.backup.BackupUseCase
import com.atlaspeak.domain.usecase.backup.PassphraseBlocklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `updating automatic backup password saves password and clears entry`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("new password")
        viewModel.onConfirmPasswordChanged("new password")
        viewModel.updateAutoBackupPassword()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("new password", backupRepository.savedPassword)
        assertEquals("", viewModel.state.value.password)
        assertEquals("", viewModel.state.value.confirmPassword)
        assertEquals(R.string.backup_auto_password_updated, viewModel.state.value.messageRes)
    }

    @Test
    fun `updating automatic backup password is blocked when confirmation does not match`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("new password")
        viewModel.onConfirmPasswordChanged("typo password")
        viewModel.updateAutoBackupPassword()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, backupRepository.savedPassword)
        assertEquals(R.string.backup_password_mismatch, viewModel.state.value.messageRes)
    }

    @Test
    fun `creating a Drive backup is blocked when the passphrase is too short`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("short")
        viewModel.onConfirmPasswordChanged("short")
        viewModel.createDriveBackup("access-token")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, backupRepository.createDriveBackupCalls)
        assertEquals(R.string.backup_password_too_short, viewModel.state.value.messageRes)
    }

    @Test
    fun `creating a Drive backup is blocked when the passphrase is in the common password blocklist`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository, blocklist = { it == "password123" })
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password123")
        viewModel.createDriveBackup("access-token")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, backupRepository.createDriveBackupCalls)
        assertEquals(R.string.backup_password_common, viewModel.state.value.messageRes)
    }

    @Test
    fun `creating a Drive backup succeeds with a valid confirmed passphrase`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("correct horse battery")
        viewModel.onConfirmPasswordChanged("correct horse battery")
        viewModel.createDriveBackup("access-token")
        dispatcher.scheduler.advanceUntilIdle()

        // Tras el exito, createDriveBackup encadena loadDriveBackups, que
        // sobrescribe messageRes con el resultado del listado (vacio en este
        // fake); por eso solo se comprueba que la subida se intento.
        assertEquals(1, backupRepository.createDriveBackupCalls)
        assertEquals("", viewModel.state.value.password)
        assertEquals("", viewModel.state.value.confirmPassword)
    }

    @Test
    fun `restore does not apply the passphrase policy, only non-empty`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        // Contrasena corta y sin confirmacion: restaurar debe aceptar
        // cualquier contrasena existente no vacia, no la politica de creacion.
        viewModel.onPasswordChanged("short")
        viewModel.restoreDriveBackup("access-token", "file-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, backupRepository.restoreDriveBackupCalls)
        assertEquals(R.string.backup_restore_success, viewModel.state.value.messageRes)
    }

    @Test
    fun `restore shows a wrong password message when the failure reason is Crypto`() = runTest {
        val backupRepository = FakeBackupRepository(restoreFailure = BackupFailure.Crypto)
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("some password")
        viewModel.restoreDriveBackup("access-token", "file-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.backup_restore_error_wrong_password, viewModel.state.value.messageRes)
    }

    @Test
    fun `restore shows a network message when the failure reason is Network`() = runTest {
        val backupRepository = FakeBackupRepository(restoreFailure = BackupFailure.Network)
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("some password")
        viewModel.restoreDriveBackup("access-token", "file-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.backup_restore_error_network, viewModel.state.value.messageRes)
    }

    @Test
    fun `restore shows an invalid file message when the failure reason is InvalidBackup`() = runTest {
        val backupRepository = FakeBackupRepository(restoreFailure = BackupFailure.InvalidBackup)
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("some password")
        viewModel.restoreDriveBackup("access-token", "file-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.backup_restore_error_invalid, viewModel.state.value.messageRes)
    }

    @Test
    fun `a Keystore failure enabling automatic backup shows an error and does not crash or flip the flag`() = runTest {
        val backupRepository = FakeBackupRepository(saveAutoBackupPasswordThrows = true)
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("correct horse battery")
        viewModel.onConfirmPasswordChanged("correct horse battery")
        viewModel.setAutoBackupEnabled(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.autoBackupEnabled)
        assertEquals(R.string.backup_auto_backup_error, viewModel.state.value.messageRes)
    }

    @Test
    fun `a Keystore failure updating the automatic backup password shows an error and does not crash`() = runTest {
        val backupRepository = FakeBackupRepository(saveAutoBackupPasswordThrows = true)
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("correct horse battery")
        viewModel.onConfirmPasswordChanged("correct horse battery")
        viewModel.updateAutoBackupPassword()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.backup_auto_backup_error, viewModel.state.value.messageRes)
    }

    private fun viewModel(
        backupRepository: FakeBackupRepository,
        blocklist: (String) -> Boolean = { false },
    ): BackupRestoreViewModel {
        return BackupRestoreViewModel(
            backupUseCase = BackupUseCase(backupRepository),
            backupPassphrasePolicy = BackupPassphrasePolicy(PassphraseBlocklist { blocklist(it) }),
        )
    }

    private class FakeBackupRepository(
        private val restoreFailure: BackupFailure? = null,
        private val saveAutoBackupPasswordThrows: Boolean = false,
    ) : BackupRepository {
        var manualJsonCalls = 0
        var csvCalls = 0
        var savedPassword: String? = null
        var createDriveBackupCalls = 0
        var restoreDriveBackupCalls = 0

        override suspend fun status(): BackupStatus = BackupStatus(
            autoBackupEnabled = false,
            lastBackupAt = null,
        )

        override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit

        override suspend fun saveAutoBackupPassword(password: CharArray) {
            if (saveAutoBackupPasswordThrows) {
                throw IllegalStateException("Keystore unavailable")
            }
            savedPassword = password.concatToString()
        }

        override suspend fun clearAutoBackupPassword() = Unit

        override suspend fun listDriveBackups(accessToken: String): List<DriveBackup> = emptyList()

        override suspend fun createDriveBackup(accessToken: String, password: CharArray): BackupResult {
            createDriveBackupCalls += 1
            return BackupResult.Success()
        }

        override suspend fun restoreDriveBackup(accessToken: String, fileId: String, password: CharArray): BackupResult {
            restoreDriveBackupCalls += 1
            return restoreFailure?.let { BackupResult.Failed(it) } ?: BackupResult.Success()
        }

        override suspend fun writeEncryptedBackup(password: CharArray): SharedBackupExport = export()

        override suspend fun writeManualJson(): SharedBackupExport {
            manualJsonCalls += 1
            return export()
        }

        override suspend fun writeCsvZip(): SharedBackupExport {
            csvCalls += 1
            return export()
        }

        override suspend fun health(): BackupHealthStatus = BackupHealthStatus(lastSuccessfulBackupAt = null, lastAttemptAt = null, lastError = null, requiresDriveAuthorization = false)
        override suspend fun clearDriveAuthorizationRequired() = Unit

        private fun export(): SharedBackupExport = SharedBackupExport(
            uri = "content://atlas/export",
            mimeType = "application/json",
            fileName = "export.json",
        )
    }
}
