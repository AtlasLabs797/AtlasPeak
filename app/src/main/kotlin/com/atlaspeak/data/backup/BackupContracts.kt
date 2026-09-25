package com.atlaspeak.data.backup

import com.atlaspeak.domain.model.backup.BackupFailure
import com.atlaspeak.domain.model.backup.BackupHealthStatus

data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdTimeMillis: Long?,
    val sizeBytes: Long?,
)

sealed interface BackupOperationResult {
    data class Success(val file: DriveBackupFile? = null) : BackupOperationResult
    data class Failed(val reason: BackupFailureReason) : BackupOperationResult
}

enum class BackupFailureReason {
    NotAuthorized,
    EmptyPassword,
    Network,
    Crypto,
    InvalidBackup,
    Unknown,
}

interface BackupSnapshotStore {
    suspend fun snapshot(): DatabaseBackupSnapshot
    suspend fun restore(snapshot: DatabaseBackupSnapshot)
    suspend fun markBackupCompleted(timestampMillis: Long)
    suspend fun lastBackupAt(): Long?
    suspend fun autoBackupEnabled(): Boolean
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun latestDataChangedAt(): Long?
    // BUG-096 (Fase 7 P1): estado funcional del backup automatico para que la
    // UI pueda mostrar avisos no silenciosos.
    suspend fun backupHealth(): BackupHealthStatus
    suspend fun recordBackupSuccess(timestampMillis: Long)
    suspend fun recordBackupFailure(reason: BackupFailure, timestampMillis: Long)
    suspend fun clearDriveAuthorizationRequired()
    suspend fun markDriveAuthorizationRequired()
}

interface DriveBackupService {
    suspend fun uploadBackup(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray,
    ): DriveBackupFile

    suspend fun listBackups(accessToken: String): List<DriveBackupFile>

    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray

    suspend fun deleteBackup(accessToken: String, fileId: String)
}
