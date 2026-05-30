package com.atlaspeak.data.backup

import java.security.MessageDigest
import java.util.Base64

internal class BackupWorkerRunner(
    private val snapshotStore: BackupSnapshotStore,
    private val backupJsonCodec: BackupJsonCodec,
    private val createBackup: suspend (String, CharArray) -> BackupOperationResult,
    private val silentAccessToken: suspend () -> String?,
    private val autoBackupPassword: suspend () -> CharArray?,
    private val lastSnapshotHash: suspend () -> String?,
    private val saveLastSnapshotHash: suspend (String) -> Unit,
) {
    suspend fun run(): BackupWorkerRunResult {
        if (!snapshotStore.autoBackupEnabled()) return BackupWorkerRunResult.Success
        val token = silentAccessToken() ?: return BackupWorkerRunResult.Success
        val password = autoBackupPassword() ?: return BackupWorkerRunResult.Success
        return try {
            val currentHash = currentSnapshotHash()
            if (lastSnapshotHash() == currentHash) return BackupWorkerRunResult.Success

            when (createBackup(token, password)) {
                is BackupOperationResult.Success -> {
                    saveLastSnapshotHash(currentHash)
                    BackupWorkerRunResult.Success
                }
                is BackupOperationResult.Failed -> BackupWorkerRunResult.Retry
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

internal enum class BackupWorkerRunResult {
    Success,
    Retry,
}
