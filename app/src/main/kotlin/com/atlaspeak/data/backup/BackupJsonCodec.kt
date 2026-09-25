package com.atlaspeak.data.backup

import com.atlaspeak.data.db.AppDatabase
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupJsonCodec @Inject constructor(
    private val upgrader: BackupSnapshotUpgrader,
) {
    constructor() : this(BackupSnapshotUpgrader())

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: DatabaseBackupSnapshot): String = json.encodeToString(snapshot)

    fun decode(value: String): DatabaseBackupSnapshot {
        val snapshot = json.decodeFromString<DatabaseBackupSnapshot>(value)
        require(snapshot.formatVersion == DatabaseBackupSnapshot.FORMAT_VERSION) { "Unsupported backup format" }
        require(snapshot.schemaVersion <= CURRENT_SCHEMA_VERSION) { "Unsupported future schema" }
        return upgrader.upgradeToCurrent(snapshot).also { upgraded ->
            require(upgraded.schemaVersion == CURRENT_SCHEMA_VERSION) { "Backup schema upgrade failed" }
            require(upgraded.tables.keys == AppDatabase.TABLES) { "Backup table set does not match the app schema" }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 8
    }
}
