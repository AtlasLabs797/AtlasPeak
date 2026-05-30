package com.atlaspeak.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DatabaseBackupSnapshot(
    val formatVersion: Int = FORMAT_VERSION,
    val schemaVersion: Int,
    val exportedAt: Long,
    val tables: Map<String, List<Map<String, JsonElement>>>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

internal fun DatabaseBackupSnapshot.withoutVolatileBackupMetadata(): DatabaseBackupSnapshot =
    copy(
        exportedAt = 0,
        tables = tables.mapValues { (table, rows) ->
            if (table == SETTINGS_TABLE) {
                rows.map { row -> row - VOLATILE_SETTINGS_COLUMNS }
            } else {
                rows
            }
        },
    )

private const val SETTINGS_TABLE = "app_settings"

private val VOLATILE_SETTINGS_COLUMNS = setOf("last_backup_at")
