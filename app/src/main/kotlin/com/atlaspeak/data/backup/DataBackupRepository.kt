package com.atlaspeak.data.backup

import com.atlaspeak.domain.model.backup.BackupFailure
import com.atlaspeak.domain.model.backup.BackupHealthStatus
import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.BackupStatus
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.domain.repository.BackupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataBackupRepository @Inject constructor(
    private val driveBackupManager: DriveBackupManager,
    private val localBackupExportManager: LocalBackupExportManager,
    private val snapshotStore: BackupSnapshotStore,
    private val credentialStore: BackupCredentialStore,
) : BackupRepository {
    override suspend fun status(): BackupStatus = BackupStatus(
        autoBackupEnabled = snapshotStore.autoBackupEnabled(),
        lastBackupAt = snapshotStore.lastBackupAt(),
    )

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        snapshotStore.setAutoBackupEnabled(enabled)
    }

    override suspend fun saveAutoBackupPassword(password: CharArray) {
        credentialStore.saveAutoBackupPassword(password)
    }

    override suspend fun clearAutoBackupPassword() {
        credentialStore.clearAutoBackupPassword()
    }

    override suspend fun listDriveBackups(accessToken: String): List<DriveBackup> =
        driveBackupManager.listBackups(accessToken).map { it.toDomain() }

    override suspend fun createDriveBackup(accessToken: String, password: CharArray): BackupResult =
        driveBackupManager.createBackup(accessToken, password).toDomain()

    override suspend fun restoreDriveBackup(accessToken: String, fileId: String, password: CharArray): BackupResult =
        driveBackupManager.restoreBackup(accessToken, fileId, password).toDomain()

    override suspend fun writeEncryptedBackup(password: CharArray): SharedBackupExport =
        localBackupExportManager.writeEncryptedBackup(password).toDomain()

    override suspend fun writeManualJson(): SharedBackupExport =
        localBackupExportManager.writeManualJson().toDomain()

    override suspend fun writeCsvZip(): SharedBackupExport =
        localBackupExportManager.writeCsvZip().toDomain()

    override suspend fun health(): BackupHealthStatus = snapshotStore.backupHealth()

    override suspend fun clearDriveAuthorizationRequired() {
        snapshotStore.clearDriveAuthorizationRequired()
    }

    private fun BackupOperationResult.toDomain(): BackupResult = when (this) {
        is BackupOperationResult.Success -> BackupResult.Success(file?.toDomain())
        is BackupOperationResult.Failed -> BackupResult.Failed(reason.toDomain())
    }

    private fun BackupFailureReason.toDomain(): BackupFailure = when (this) {
        BackupFailureReason.NotAuthorized -> BackupFailure.NotAuthorized
        BackupFailureReason.EmptyPassword -> BackupFailure.EmptyPassword
        BackupFailureReason.Network -> BackupFailure.Network
        BackupFailureReason.Crypto -> BackupFailure.Crypto
        BackupFailureReason.InvalidBackup -> BackupFailure.InvalidBackup
        BackupFailureReason.Unknown -> BackupFailure.Unknown
    }

    private fun DriveBackupFile.toDomain(): DriveBackup = DriveBackup(
        id = id,
        name = name,
        createdTimeMillis = createdTimeMillis,
        sizeBytes = sizeBytes,
    )

    private fun SharedExportFile.toDomain(): SharedBackupExport = SharedBackupExport(
        uri = uri.toString(),
        mimeType = mimeType,
        fileName = fileName,
    )
}
