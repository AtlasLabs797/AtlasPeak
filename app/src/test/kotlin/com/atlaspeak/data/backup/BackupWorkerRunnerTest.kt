package com.atlaspeak.data.backup

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupWorkerRunnerTest {
    @Test
    fun `runner exits successfully without upload when auto backup is disabled`() = runTest {
        val store = FakeSnapshotStore(autoEnabled = false)
        val recorder = BackupRecorder()

        val result = runner(store, recorder).run()

        assertEquals(BackupWorkerRunResult.Success, result)
        assertEquals(0, recorder.uploadCount)
    }

    @Test
    fun `runner skips upload when stable snapshot hash has not changed`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder()
        val previousHash = runner(store, recorder).currentSnapshotHash()
        val credentials = FakeCredentials(lastHash = previousHash)

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Success, result)
        assertEquals(0, recorder.uploadCount)
    }

    @Test
    fun `runner saves new hash and clears password after successful upload`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder()
        val credentials = FakeCredentials(password = charArrayOf('p', 'a', 's', 's'))

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Success, result)
        assertEquals(1, recorder.uploadCount)
        assertTrue(credentials.savedHash.isNotBlank())
        assertEquals("\u0000\u0000\u0000\u0000", credentials.password.concatToString())
    }

    @Test
    fun `runner retries failed upload and still clears password`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder(result = BackupOperationResult.Failed(BackupFailureReason.Network))
        val credentials = FakeCredentials(password = charArrayOf('p', 'a', 's', 's'))

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Retry, result)
        assertEquals(1, recorder.uploadCount)
        assertEquals("", credentials.savedHash)
        assertEquals("\u0000\u0000\u0000\u0000", credentials.password.concatToString())
    }

    private fun runner(
        store: FakeSnapshotStore,
        recorder: BackupRecorder,
        credentials: FakeCredentials = FakeCredentials(),
    ) = BackupWorkerRunner(
        snapshotStore = store,
        backupJsonCodec = BackupJsonCodec(),
        createBackup = recorder::createBackup,
        silentAccessToken = { credentials.token },
        autoBackupPassword = { credentials.password },
        lastSnapshotHash = { credentials.lastHash },
        saveLastSnapshotHash = { credentials.savedHash = it },
    )

    private class FakeSnapshotStore(
        private val autoEnabled: Boolean = true,
    ) : BackupSnapshotStore {
        override suspend fun snapshot(): DatabaseBackupSnapshot = DatabaseBackupSnapshot(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = 1_800_000_000_000,
            tables = mapOf(
                "app_settings" to listOf(
                    mapOf(
                        "id" to JsonPrimitive(1),
                        "last_backup_at" to JsonPrimitive(1_800_000_000_000),
                    ),
                ),
                "users" to listOf(mapOf("id" to JsonPrimitive("user-1"))),
            ),
        )

        override suspend fun restore(snapshot: DatabaseBackupSnapshot) = Unit

        override suspend fun markBackupCompleted(timestampMillis: Long) = Unit

        override suspend fun lastBackupAt(): Long? = null

        override suspend fun autoBackupEnabled(): Boolean = autoEnabled

        override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit

        override suspend fun latestDataChangedAt(): Long? = null
    }

    private class BackupRecorder(
        private val result: BackupOperationResult = BackupOperationResult.Success(),
    ) {
        var uploadCount = 0

        suspend fun createBackup(accessToken: String, password: CharArray): BackupOperationResult {
            uploadCount += 1
            return result
        }
    }

    private class FakeCredentials(
        val token: String? = "drive-token",
        val password: CharArray = charArrayOf('p', 'a', 's', 's'),
        val lastHash: String? = null,
    ) {
        var savedHash: String = ""
    }
}
