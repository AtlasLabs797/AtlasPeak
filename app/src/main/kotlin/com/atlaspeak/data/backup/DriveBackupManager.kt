package com.atlaspeak.data.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.AEADBadTagException

class DriveBackupManager(
    private val snapshotStore: BackupSnapshotStore,
    private val backupJsonCodec: BackupJsonCodec,
    private val backupFileCodec: BackupFileCodec,
    private val driveBackupService: DriveBackupService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun createBackup(
        accessToken: String?,
        password: CharArray,
    ): BackupOperationResult {
        if (accessToken.isNullOrBlank()) return BackupOperationResult.Failed(BackupFailureReason.NotAuthorized)
        if (password.isEmpty()) return BackupOperationResult.Failed(BackupFailureReason.EmptyPassword)

        return runCatching {
            val now = clock()
            val payload = backupJsonCodec.encode(snapshotStore.snapshot()).encodeToByteArray()
            val encrypted = try {
                backupFileCodec.encrypt(payload, password)
            } finally {
                payload.fill(0)
            }
            val uploaded = driveBackupService.uploadBackup(
                accessToken = accessToken,
                fileName = backupFileName(now),
                encryptedBytes = encrypted,
            )
            trimOldBackups(accessToken)
            snapshotStore.markBackupCompleted(now)
            BackupOperationResult.Success(uploaded)
        }.getOrElse { error ->
            BackupOperationResult.Failed(error.toBackupFailure())
        }
    }

    suspend fun restoreBackup(
        accessToken: String?,
        fileId: String,
        password: CharArray,
    ): BackupOperationResult {
        if (accessToken.isNullOrBlank()) return BackupOperationResult.Failed(BackupFailureReason.NotAuthorized)
        if (password.isEmpty()) return BackupOperationResult.Failed(BackupFailureReason.EmptyPassword)

        return runCatching {
            val encrypted = driveBackupService.downloadBackup(accessToken, fileId)
            val plaintext = backupFileCodec.decrypt(encrypted, password)
            val json = try {
                plaintext.decodeToString()
            } finally {
                plaintext.fill(0)
            }
            val snapshot = backupJsonCodec.decode(json)
            snapshotStore.restore(snapshot)
            BackupOperationResult.Success()
        }.getOrElse { error ->
            BackupOperationResult.Failed(error.toBackupFailure())
        }
    }

    suspend fun listBackups(accessToken: String?): List<DriveBackupFile> {
        if (accessToken.isNullOrBlank()) return emptyList()
        return driveBackupService.listBackups(accessToken)
            .sortedWith(compareByDescending<DriveBackupFile> { it.createdTimeMillis ?: Long.MIN_VALUE }.thenBy { it.name })
    }

    private suspend fun trimOldBackups(accessToken: String) {
        val backups = listBackups(accessToken)
        backups.drop(MAX_BACKUPS).forEach { driveBackupService.deleteBackup(accessToken, it.id) }
    }

    private fun backupFileName(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "atlas_peak_backup_${formatter.format(Date(timestampMillis))}.enc"
    }

    private fun Throwable.toBackupFailure(): BackupFailureReason = when (this) {
        is AEADBadTagException -> BackupFailureReason.Crypto
        is IllegalArgumentException -> BackupFailureReason.InvalidBackup
        else -> BackupFailureReason.Unknown
    }

    companion object {
        const val MAX_BACKUPS = 5
    }
}
