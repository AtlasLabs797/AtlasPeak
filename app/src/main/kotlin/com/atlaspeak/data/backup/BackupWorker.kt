package com.atlaspeak.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlaspeak.data.drive.DriveAccessTokenProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.security.MessageDigest
import java.util.Base64

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val snapshotStore: BackupSnapshotStore,
    private val backupJsonCodec: BackupJsonCodec,
    private val driveBackupManager: DriveBackupManager,
    private val driveAccessTokenProvider: DriveAccessTokenProvider,
    private val credentialStore: BackupCredentialStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!snapshotStore.autoBackupEnabled()) return Result.success()
        val token = driveAccessTokenProvider.silentAccessToken() ?: return Result.success()
        val password = credentialStore.autoBackupPassword() ?: return Result.success()
        return try {
            val snapshot = snapshotStore.snapshot()
            val currentHash = snapshot.stableHash()
            if (credentialStore.lastSnapshotHash() == currentHash) return Result.success()

            when (driveBackupManager.createBackup(token, password)) {
                is BackupOperationResult.Success -> {
                    credentialStore.saveLastSnapshotHash(currentHash)
                    Result.success()
                }
                is BackupOperationResult.Failed -> Result.retry()
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun DatabaseBackupSnapshot.stableHash(): String {
        val canonical = withoutVolatileBackupMetadata()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(backupJsonCodec.encode(canonical).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}
