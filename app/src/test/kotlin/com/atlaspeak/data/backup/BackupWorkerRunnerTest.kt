package com.atlaspeak.data.backup

import com.atlaspeak.data.drive.DriveAccessTokenResult
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

    @Test
    fun `runner skips upload when Drive authorization is missing`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder()
        val credentials = FakeCredentials(token = DriveAccessTokenResult.MissingAuthorization)

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Success, result)
        assertEquals(0, recorder.uploadCount)
    }

    @Test
    fun `runner retries when silent Drive authorization fails`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder()
        val credentials = FakeCredentials(token = DriveAccessTokenResult.Failed)

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Retry, result)
        assertEquals(0, recorder.uploadCount)
    }

    @Test
    fun `runner does not retry permanent backup failures`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder(result = BackupOperationResult.Failed(BackupFailureReason.EmptyPassword))
        val credentials = FakeCredentials(password = charArrayOf('p', 'a', 's', 's'))

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Success, result)
        assertEquals(1, recorder.uploadCount)
        assertEquals("", credentials.savedHash)
        assertEquals("\u0000\u0000\u0000\u0000", credentials.password.concatToString())
    }

    @Test
    fun `runner takes a single snapshot and reuses it for hash and upload`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder()
        val credentials = FakeCredentials(password = charArrayOf('p', 'a', 's', 's'))

        runner(store, recorder, credentials).run()

        assertEquals(1, store.snapshotCalls)
        assertEquals(store.snapshot().exportedAt, recorder.lastSnapshot?.exportedAt)
    }

    @Test
    fun `runner marks Drive authorization required when upload fails with NotAuthorized`() = runTest {
        val store = FakeSnapshotStore()
        val recorder = BackupRecorder(result = BackupOperationResult.Failed(BackupFailureReason.NotAuthorized))
        val credentials = FakeCredentials(password = charArrayOf('p', 'a', 's', 's'))

        val result = runner(store, recorder, credentials).run()

        assertEquals(BackupWorkerRunResult.Success, result)
        assertEquals(1, store.markDriveAuthorizationRequiredCalls)
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
        var snapshotCalls = 0
        var markDriveAuthorizationRequiredCalls = 0

        override suspend fun snapshot(): DatabaseBackupSnapshot {
            snapshotCalls += 1
            return DatabaseBackupSnapshot(
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
        }

        override suspend fun restore(snapshot: DatabaseBackupSnapshot) = Unit

        override suspend fun markBackupCompleted(timestampMillis: Long) = Unit

        override suspend fun lastBackupAt(): Long? = null

        override suspend fun autoBackupEnabled(): Boolean = autoEnabled

        override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit

        override suspend fun latestDataChangedAt(): Long? = null

        // BUG-096 (Fase 7 P1): implementaciones no-op para los nuevos
        // metodos del estado funcional. Las pruebas viven en BackupHealthStoreTest.
        override suspend fun backupHealth(): com.atlaspeak.domain.model.backup.BackupHealthStatus =
            com.atlaspeak.domain.model.backup.BackupHealthStatus(null, null, null, false)
        override suspend fun recordBackupSuccess(timestampMillis: Long) = Unit
        override suspend fun recordBackupFailure(reason: com.atlaspeak.domain.model.backup.BackupFailure, timestampMillis: Long) = Unit
        override suspend fun clearDriveAuthorizationRequired() = Unit
        override suspend fun markDriveAuthorizationRequired() {
            markDriveAuthorizationRequiredCalls += 1
        }
    }

    private class BackupRecorder(
        private val result: BackupOperationResult = BackupOperationResult.Success(),
    ) {
        var uploadCount = 0
        var lastSnapshot: DatabaseBackupSnapshot? = null

        suspend fun createBackup(accessToken: String, password: CharArray, snapshot: DatabaseBackupSnapshot): BackupOperationResult {
            uploadCount += 1
            lastSnapshot = snapshot
            return result
        }
    }

    private class FakeCredentials(
        val token: DriveAccessTokenResult = DriveAccessTokenResult.Granted("drive-token"),
        val password: CharArray = charArrayOf('p', 'a', 's', 's'),
        val lastHash: String? = null,
    ) {
        var savedHash: String = ""
    }
}
