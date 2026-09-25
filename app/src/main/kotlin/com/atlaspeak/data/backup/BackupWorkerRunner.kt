package com.atlaspeak.data.backup

import java.security.MessageDigest
import java.util.Base64
import com.atlaspeak.data.drive.DriveAccessTokenResult
import com.atlaspeak.domain.model.backup.BackupFailure

internal class BackupWorkerRunner(
    private val snapshotStore: BackupSnapshotStore,
    private val backupJsonCodec: BackupJsonCodec,
    private val createBackup: suspend (String, CharArray) -> BackupOperationResult,
    private val silentAccessToken: suspend () -> DriveAccessTokenResult,
    private val autoBackupPassword: suspend () -> CharArray?,
    private val lastSnapshotHash: suspend () -> String?,
    private val saveLastSnapshotHash: suspend (String) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun run(): BackupWorkerRunResult {
        if (!snapshotStore.autoBackupEnabled()) return BackupWorkerRunResult.Success
        val token = when (val result = silentAccessToken()) {
            is DriveAccessTokenResult.Granted -> result.accessToken
            DriveAccessTokenResult.MissingAuthorization -> {
                // BUG-096 (Fase 7 P1): el resultado tecnico del worker es Success
                // (no queremos retries infinitos) pero el estado funcional del
                // backup queda marcado para que la UI avise al usuario y le
                // pida reconectar Drive.
                snapshotStore.markDriveAuthorizationRequired()
                return BackupWorkerRunResult.Success
            }
            DriveAccessTokenResult.Failed -> return BackupWorkerRunResult.Retry
        }
        val password = autoBackupPassword()
        if (password == null) {
            snapshotStore.recordBackupFailure(BackupFailure.EmptyPassword, now())
            return BackupWorkerRunResult.Success
        }
        return try {
            val currentHash = currentSnapshotHash()
            if (lastSnapshotHash() == currentHash) {
                // Sin cambios desde el ultimo backup: lo damos por bueno y
                // actualizamos `lastAttemptAt` sin tocar `lastError`.
                snapshotStore.recordBackupSuccess(now())
                return BackupWorkerRunResult.Success
            }

            when (val result = createBackup(token, password)) {
                is BackupOperationResult.Success -> {
                    saveLastSnapshotHash(currentHash)
                    snapshotStore.recordBackupSuccess(now())
                    BackupWorkerRunResult.Success
                }
                is BackupOperationResult.Failed -> {
                    snapshotStore.recordBackupFailure(result.reason.toDomain(), now())
                    if (result.reason.shouldRetry()) {
                        BackupWorkerRunResult.Retry
                    } else {
                        BackupWorkerRunResult.Success
                    }
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun currentSnapshotHash(): String = snapshotStore.snapshot().stableHash()

    private fun DatabaseBackupSnapshot.stableHash(): String {
        val canonical = withoutVolatileBackupMetadata()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(backupJsonCodec.encode(canonical).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}

private fun BackupFailureReason.toDomain(): BackupFailure = when (this) {
    BackupFailureReason.NotAuthorized -> BackupFailure.NotAuthorized
    BackupFailureReason.EmptyPassword -> BackupFailure.EmptyPassword
    BackupFailureReason.Network -> BackupFailure.Network
    BackupFailureReason.Crypto -> BackupFailure.Crypto
    BackupFailureReason.InvalidBackup -> BackupFailure.InvalidBackup
    BackupFailureReason.Unknown -> BackupFailure.Unknown
}

private fun BackupFailureReason.shouldRetry(): Boolean = when (this) {
    BackupFailureReason.Network,
    BackupFailureReason.Unknown,
    -> true
    BackupFailureReason.NotAuthorized,
    BackupFailureReason.InvalidBackup,
    BackupFailureReason.EmptyPassword,
    BackupFailureReason.Crypto,
    -> false
}

internal enum class BackupWorkerRunResult {
    Success,
    Retry,
}