package com.atlaspeak.data.backup

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DriveBackupManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val snapshot = DatabaseBackupSnapshot(
        schemaVersion = 2,
        exportedAt = 1_800_000_000_000,
        tables = mapOf("users" to listOf(mapOf("id" to JsonPrimitive("local")))),
    )

    @Test
    fun `createBackup uploads encrypted data then trims backups beyond five`() = runTest(dispatcher) {
        val store = FakeSnapshotStore(snapshot)
        val service = FakeDriveBackupService().apply {
            backupsAfterUpload = listOf(
                DriveBackupFile(id = "new", name = "atlas_peak_backup_20260524_120000.enc", createdTimeMillis = 6000, sizeBytes = 200),
                DriveBackupFile(id = "5", name = "atlas_peak_backup_old_5.enc", createdTimeMillis = 5000, sizeBytes = 200),
                DriveBackupFile(id = "4", name = "atlas_peak_backup_old_4.enc", createdTimeMillis = 4000, sizeBytes = 200),
                DriveBackupFile(id = "3", name = "atlas_peak_backup_old_3.enc", createdTimeMillis = 3000, sizeBytes = 200),
                DriveBackupFile(id = "2", name = "atlas_peak_backup_old_2.enc", createdTimeMillis = 2000, sizeBytes = 200),
                DriveBackupFile(id = "1", name = "atlas_peak_backup_old_1.enc", createdTimeMillis = 1000, sizeBytes = 200),
            )
        }
        val manager = manager(store, service)

        val result = manager.createBackup("access-token", "backup-password".toCharArray())

        assertTrue(result is BackupOperationResult.Success)
        assertEquals("access-token", service.uploadToken)
        assertTrue(service.uploadedName.startsWith("atlas_peak_backup_"))
        assertTrue(service.uploadedName.endsWith(".enc"))
        assertFalse(service.uploadedBytes.decodeToString().contains("users"))
        assertEquals(listOf("1"), service.deletedIds)
        assertEquals(1_800_000_000_000, store.lastBackupAt)
    }

    @Test
    fun `restoreBackup decrypts before writing and fails closed on wrong password`() = runTest(dispatcher) {
        val codec = BackupFileCodec(dispatcher = dispatcher)
        val encrypted = codec.encrypt(BackupJsonCodec().encode(snapshot).encodeToByteArray(), "right-password".toCharArray())
        val store = FakeSnapshotStore(snapshot)
        val service = FakeDriveBackupService().apply { downloadedBytes = encrypted }
        val manager = manager(store, service, codec)

        val result = manager.restoreBackup("access-token", "file-1", "wrong-password".toCharArray())

        assertTrue(result is BackupOperationResult.Failed)
        assertEquals(0, store.restoreCount)
    }

    private fun manager(
        store: FakeSnapshotStore,
        service: FakeDriveBackupService,
        codec: BackupFileCodec = BackupFileCodec(dispatcher = dispatcher),
    ) = DriveBackupManager(
        snapshotStore = store,
        backupJsonCodec = BackupJsonCodec(),
        backupFileCodec = codec,
        driveBackupService = service,
        clock = { 1_800_000_000_000 },
    )

    private class FakeSnapshotStore(private val snapshot: DatabaseBackupSnapshot) : BackupSnapshotStore {
        var lastBackupAt: Long? = null
        var restoreCount = 0

        override suspend fun snapshot(): DatabaseBackupSnapshot = snapshot

        override suspend fun restore(snapshot: DatabaseBackupSnapshot) {
            restoreCount += 1
        }

        override suspend fun markBackupCompleted(timestampMillis: Long) {
            lastBackupAt = timestampMillis
        }

        override suspend fun lastBackupAt(): Long? = lastBackupAt

        override suspend fun autoBackupEnabled(): Boolean = true

        override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit

        override suspend fun latestDataChangedAt(): Long? = 1_800_000_000_000
    }

    private class FakeDriveBackupService : DriveBackupService {
        var backupsAfterUpload = emptyList<DriveBackupFile>()
        var downloadedBytes = ByteArray(0)
        var uploadToken: String? = null
        var uploadedName = ""
        var uploadedBytes = ByteArray(0)
        val deletedIds = mutableListOf<String>()

        override suspend fun uploadBackup(accessToken: String, fileName: String, encryptedBytes: ByteArray): DriveBackupFile {
            uploadToken = accessToken
            uploadedName = fileName
            uploadedBytes = encryptedBytes
            return DriveBackupFile("new", fileName, 1_800_000_000_000, encryptedBytes.size.toLong())
        }

        override suspend fun listBackups(accessToken: String): List<DriveBackupFile> = backupsAfterUpload

        override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray = downloadedBytes

        override suspend fun deleteBackup(accessToken: String, fileId: String) {
            deletedIds += fileId
        }
    }
}
