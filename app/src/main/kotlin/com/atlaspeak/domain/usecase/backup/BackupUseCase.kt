package com.atlaspeak.domain.usecase.backup

import com.atlaspeak.domain.model.backup.BackupHealthStatus
import com.atlaspeak.domain.repository.BackupRepository
import javax.inject.Inject

class BackupUseCase @Inject constructor(
    private val repository: BackupRepository,
) {
    suspend fun status() = repository.status()

    suspend fun setAutoBackupEnabled(enabled: Boolean) = repository.setAutoBackupEnabled(enabled)

    suspend fun saveAutoBackupPassword(password: CharArray) = repository.saveAutoBackupPassword(password)

    suspend fun clearAutoBackupPassword() = repository.clearAutoBackupPassword()

    suspend fun listDriveBackups(accessToken: String) = repository.listDriveBackups(accessToken)

    suspend fun createDriveBackup(accessToken: String, password: CharArray) =
        repository.createDriveBackup(accessToken, password)

    suspend fun restoreDriveBackup(accessToken: String, fileId: String, password: CharArray) =
        repository.restoreDriveBackup(accessToken, fileId, password)

    suspend fun writeEncryptedBackup(password: CharArray) = repository.writeEncryptedBackup(password)

    suspend fun writeManualJson() = repository.writeManualJson()

    suspend fun writeCsvZip() = repository.writeCsvZip()

    // BUG-096 (Fase 7 P1)
    suspend fun health(): BackupHealthStatus = repository.health()
    suspend fun clearDriveAuthorizationRequired() = repository.clearDriveAuthorizationRequired()
}
