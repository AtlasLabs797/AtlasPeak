package com.atlaspeak.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlaspeak.data.drive.DriveAccessTokenProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
        val result = BackupWorkerRunner(
            snapshotStore = snapshotStore,
            backupJsonCodec = backupJsonCodec,
            createBackup = driveBackupManager::createBackup,
            silentAccessToken = driveAccessTokenProvider::silentAccessToken,
            autoBackupPassword = credentialStore::autoBackupPassword,
            lastSnapshotHash = credentialStore::lastSnapshotHash,
            saveLastSnapshotHash = credentialStore::saveLastSnapshotHash,
        ).run()
        return when (result) {
            BackupWorkerRunResult.Success -> Result.success()
            BackupWorkerRunResult.Retry -> Result.retry()
        }
    }
}
