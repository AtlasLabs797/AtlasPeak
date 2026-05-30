package com.atlaspeak.data.backup

import com.atlaspeak.data.db.AppDatabase
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupJsonCodecTest {
    private val codec = BackupJsonCodec()

    @Test
    fun `round trip keeps every Room table for encrypted restore backups`() {
        val snapshot = DatabaseBackupSnapshot(
            schemaVersion = 2,
            exportedAt = 1_800_000_000_000,
            tables = AppDatabase.TABLES.associateWith { table ->
                listOf(mapOf("id" to JsonPrimitive("$table-id")))
            },
        )

        val restored = codec.decode(codec.encode(snapshot))

        assertEquals(AppDatabase.TABLES, restored.tables.keys)
        assertEquals("users-id", restored.tables.getValue("users").single().getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `decode rejects unsupported future schema versions`() {
        val snapshot = DatabaseBackupSnapshot(
            schemaVersion = 999,
            exportedAt = 1_800_000_000_000,
            tables = AppDatabase.TABLES.associateWith { emptyList() },
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(codec.encode(snapshot))
        }
    }

    @Test
    fun `decode rejects missing required tables`() {
        val snapshot = DatabaseBackupSnapshot(
            schemaVersion = 2,
            exportedAt = 1_800_000_000_000,
            tables = AppDatabase.TABLES.minus("users").associateWith { emptyList() },
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(codec.encode(snapshot))
        }
        assertTrue(AppDatabase.TABLES.contains("users"))
    }

    @Test
    fun `backup change hash canonicalization ignores last backup timestamp only`() {
        val snapshot = settingsSnapshot(lastBackupAt = 1_700_000_000_000, notificationsEnabled = true)
        val changedOnlyByBackupTimestamp = settingsSnapshot(lastBackupAt = 1_800_000_000_000, notificationsEnabled = true)
        val changedUserSetting = settingsSnapshot(lastBackupAt = 1_800_000_000_000, notificationsEnabled = false)

        assertEquals(
            codec.encode(snapshot.withoutVolatileBackupMetadata()),
            codec.encode(changedOnlyByBackupTimestamp.withoutVolatileBackupMetadata()),
        )
        assertTrue(
            codec.encode(snapshot.withoutVolatileBackupMetadata()) !=
                codec.encode(changedUserSetting.withoutVolatileBackupMetadata()),
        )
    }

    private fun settingsSnapshot(lastBackupAt: Long, notificationsEnabled: Boolean) = DatabaseBackupSnapshot(
        schemaVersion = 2,
        exportedAt = lastBackupAt,
        tables = AppDatabase.TABLES.associateWith { table ->
            if (table == "app_settings") {
                listOf(
                    mapOf(
                        "id" to JsonPrimitive(1),
                        "notifications_enabled" to JsonPrimitive(notificationsEnabled),
                        "last_backup_at" to JsonPrimitive(lastBackupAt),
                    ),
                )
            } else {
                emptyList()
            }
        },
    )
}
