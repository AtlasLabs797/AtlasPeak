package com.atlaspeak.presentation.backup

import com.atlaspeak.R
import com.atlaspeak.domain.model.auth.AuthSecurityState
import com.atlaspeak.domain.model.auth.LocalAuthPolicy
import com.atlaspeak.domain.model.auth.LocalUser
import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.BackupStatus
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.repository.BackupRepository
import com.atlaspeak.domain.security.PasswordHash
import com.atlaspeak.domain.security.PasswordHasher
import com.atlaspeak.domain.usecase.auth.LocalAuthUseCase
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
    fun `plaintext json export requires local password and clears state`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("wrong")
        viewModel.exportJson()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, backupRepository.manualJsonCalls)
        assertEquals("", viewModel.state.value.password)
        assertEquals(R.string.auth_error_invalid_credentials, viewModel.state.value.messageRes)
    }

    @Test
    fun `plaintext csv export runs after step up auth and clears state`() = runTest {
        val backupRepository = FakeBackupRepository()
        val viewModel = viewModel(backupRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("correct horse")
        viewModel.exportCsv()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, backupRepository.csvCalls)
        assertEquals("", viewModel.state.value.password)
        assertEquals(R.string.backup_export_created, viewModel.state.value.messageRes)
    }

    private fun viewModel(backupRepository: FakeBackupRepository): BackupRestoreViewModel {
        return BackupRestoreViewModel(
            backupUseCase = BackupUseCase(backupRepository),
            localAuthUseCase = LocalAuthUseCase(FakeAuthRepository(), FakePasswordHasher(), { 1_000_000L }),
        )
    }

    private class FakePasswordHasher : PasswordHasher {
        override suspend fun hashPassword(password: CharArray): PasswordHash =
            PasswordHash(hashBase64 = "hash:${password.concatToString()}", saltBase64 = "salt")

        override suspend fun verifyPassword(password: CharArray, storedHash: PasswordHash): Boolean =
            password.concatToString() == "correct horse"
    }

    private class FakeAuthRepository : AuthRepository {
        private var security = AuthSecurityState()

        override suspend fun getLocalUser(): LocalUser = LocalUser(
            id = "local-user",
            googleId = null,
            email = null,
            passwordHash = "hash:correct horse",
            passwordSalt = "salt",
            createdAt = 1L,
            lastLoginAt = 1L,
        )

        override suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long) = Unit

        override suspend fun markLogin(nowMillis: Long) = Unit

        override suspend fun isBiometricUnlockEnabled(): Boolean = true

        override suspend fun getUnlockTimeoutMinutes(): Int = 5

        override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = Unit

        override suspend fun getAuthSecurityState(): AuthSecurityState = security

        override suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState {
            security = security.copy(
                failedAttempts = security.failedAttempts + 1,
                lockedUntilMillis = if (security.failedAttempts + 1 >= LocalAuthPolicy.MAX_FAILED_ATTEMPTS) {
                    nowMillis + LocalAuthPolicy.LOCKOUT_MILLIS
                } else {
                    null
                },
            )
            return security
        }

        override suspend fun resetAuthSecurity() {
            security = AuthSecurityState()
        }
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
