package com.atlaspeak.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.atlaspeak.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Singleton
class RoomBackupSnapshotStore @Inject constructor(
    private val database: AppDatabase,
) : BackupSnapshotStore {
    override suspend fun snapshot(): DatabaseBackupSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            DatabaseBackupSnapshot(
                schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
                exportedAt = System.currentTimeMillis(),
                tables = AppDatabase.TABLE_ORDER.associateWith { table ->
                    db.query("SELECT * FROM $table").use { cursor -> cursor.rowsAsJson() }
                },
            )
        }
    }

    override suspend fun restore(snapshot: DatabaseBackupSnapshot) = withContext(Dispatchers.IO) {
        require(snapshot.tables.keys == AppDatabase.TABLES) { "Backup table set does not match the app schema" }
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            val tableColumns = AppDatabase.TABLE_ORDER.associateWith { table -> db.columnsFor(table) }
            snapshot.validateAgainst(tableColumns)
            AppDatabase.TABLE_ORDER.asReversed().forEach { table ->
                db.execSQL("DELETE FROM $table")
            }
            AppDatabase.TABLE_ORDER.forEach { table ->
                snapshot.tables.getValue(table).forEach { row ->
                    db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, row.toContentValues())
                }
            }
        }
    }

    override suspend fun markBackupCompleted(timestampMillis: Long) {
        database.settingsDao().updateLastBackupAt(timestampMillis)
    }

    override suspend fun lastBackupAt(): Long? = database.settingsDao().getSettings()?.lastBackupAt

    override suspend fun autoBackupEnabled(): Boolean = database.settingsDao().getSettings()?.backupAutoEnabled ?: false

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        database.settingsDao().updateBackupAutoEnabled(enabled)
    }

    override suspend fun latestDataChangedAt(): Long? = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        TRACKED_CHANGE_COLUMNS.mapNotNull { (table, column) ->
            runCatching {
                db.query("SELECT MAX($column) FROM $table").use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
            }.getOrNull()
        }.maxOrNull()
    }

    private fun Cursor.rowsAsJson(): List<Map<String, JsonElement>> {
        val columns = columnNames.toList()
        val rows = mutableListOf<Map<String, JsonElement>>()
        while (moveToNext()) {
            rows += columns.mapIndexed { index, name -> name to valueAt(index) }.toMap()
        }
        return rows
    }

    private fun Cursor.valueAt(index: Int): JsonElement = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(index))
        Cursor.FIELD_TYPE_BLOB -> JsonPrimitive(android.util.Base64.encodeToString(getBlob(index), android.util.Base64.NO_WRAP))
        else -> JsonNull
    }

    private fun Map<String, JsonElement>.toContentValues(): ContentValues {
        val values = ContentValues(size)
        forEach { (column, value) ->
            when (value) {
                JsonNull -> values.putNull(column)
                is JsonPrimitive -> values.putPrimitive(column, value)
                else -> error("Backup rows must be validated before restore")
            }
        }
        return values
    }

    private fun DatabaseBackupSnapshot.validateAgainst(tableColumns: Map<String, Map<String, ColumnInfo>>) {
        AppDatabase.TABLE_ORDER.forEach { table ->
            val columns = tableColumns.getValue(table)
            val expectedColumnNames = columns.keys
            tables.getValue(table).forEach { row ->
                require(row.keys == expectedColumnNames) { "Backup row column set does not match the app schema" }
                row.forEach { (column, value) ->
                    value.requireCompatibleWith(table, columns.getValue(column))
                }
            }
        }
    }

    private fun JsonElement.requireCompatibleWith(table: String, column: ColumnInfo) {
        when (this) {
            JsonNull -> require(!column.required) { "Backup row contains null for required column $table.${column.name}" }
            is JsonPrimitive -> require(isCompatibleWith(column)) { "Backup row value type does not match $table.${column.name}" }
            else -> throw IllegalArgumentException("Backup row contains a non-primitive value")
        }
    }

    private fun JsonPrimitive.isCompatibleWith(column: ColumnInfo): Boolean = when (column.affinity) {
        SqliteAffinity.INTEGER -> !isJsonString && (booleanOrNull != null || longOrNull != null)
        SqliteAffinity.REAL -> !isJsonString && booleanOrNull == null && doubleOrNull != null
        SqliteAffinity.TEXT -> isJsonString
        SqliteAffinity.BLOB -> isJsonString && content.isBase64()
        SqliteAffinity.NUMERIC -> !isJsonString && (booleanOrNull != null || doubleOrNull != null || longOrNull != null)
    }

    private fun String.isBase64(): Boolean =
        isNotEmpty() && length % 4 == 0 && all { it in BASE64_CHARS || it == '=' }

    private val JsonPrimitive.isJsonString: Boolean
        get() = toString().startsWith("\"")

    private fun ContentValues.putPrimitive(column: String, value: JsonPrimitive) {
        when {
            value.isJsonString -> put(column, value.content)
            value.booleanOrNull != null -> put(column, if (value.booleanOrNull == true) 1 else 0)
            value.longOrNull != null -> put(column, value.longOrNull)
            value.doubleOrNull != null -> put(column, value.doubleOrNull)
            else -> put(column, value.content)
        }
    }

    private fun SupportSQLiteDatabase.columnsFor(table: String): Map<String, ColumnInfo> {
        return query("PRAGMA table_info($table)").use { cursor ->
            buildMap {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    put(
                        name,
                        ColumnInfo(
                            name = name,
                            type = cursor.getString(typeIndex).orEmpty(),
                            required = cursor.getInt(notNullIndex) != 0 || cursor.getInt(primaryKeyIndex) != 0,
                        ),
                    )
                }
            }
        }
    }

    private data class ColumnInfo(
        val name: String,
        val type: String,
        val required: Boolean,
    ) {
        val affinity: SqliteAffinity = run {
            val normalized = type.uppercase()
            when {
                "INT" in normalized -> SqliteAffinity.INTEGER
                "CHAR" in normalized || "CLOB" in normalized || "TEXT" in normalized -> SqliteAffinity.TEXT
                "BLOB" in normalized || normalized.isBlank() -> SqliteAffinity.BLOB
                "REAL" in normalized || "FLOA" in normalized || "DOUB" in normalized -> SqliteAffinity.REAL
                else -> SqliteAffinity.NUMERIC
            }
        }
    }

    private enum class SqliteAffinity {
        INTEGER,
        REAL,
        TEXT,
        BLOB,
        NUMERIC,
    }

    private companion object {
        private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val TRACKED_CHANGE_COLUMNS = listOf(
            "user_profile" to "updated_at",
            "exercises" to "created_at",
            "routines" to "updated_at",
            "workout_sessions" to "start_time",
            "workout_sessions" to "end_time",
            "workout_sets" to "completed_at",
            "body_composition" to "created_at",
            "weekly_plan" to "day_of_week",
            "hc_steps_records" to "last_modified_at",
            "hc_active_calories_records" to "last_modified_at",
            "hc_sleep_sessions" to "last_modified_at",
            "hc_sleep_stages" to "last_modified_at",
            "hc_heart_rate_samples" to "last_modified_at",
        )
    }
}
