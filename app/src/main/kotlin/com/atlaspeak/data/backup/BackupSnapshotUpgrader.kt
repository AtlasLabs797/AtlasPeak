package com.atlaspeak.data.backup

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/**
 * Upgrades snapshots written by older app versions to the current backup schema.
 *
 * Restores run against the already-migrated Room database and
 * [RoomBackupSnapshotStore] validates each row against the live column set, so a
 * snapshot exported before a Room migration would otherwise be rejected as invalid
 * even though its data is perfectly recoverable. Each step here mirrors the column
 * additions of the corresponding Room migration in `AppDatabase`, backfilling the
 * same default the migration used (nullable columns start as NULL).
 */
class BackupSnapshotUpgrader @Inject constructor() {
    fun upgradeToCurrent(snapshot: DatabaseBackupSnapshot): DatabaseBackupSnapshot {
        require(snapshot.schemaVersion <= BackupJsonCodec.CURRENT_SCHEMA_VERSION) {
            "Backup schema version ${snapshot.schemaVersion} is newer than the supported " +
                "version ${BackupJsonCodec.CURRENT_SCHEMA_VERSION}; update the app to restore it"
        }
        return UPGRADE_STEPS
            .filter { it.fromVersion >= snapshot.schemaVersion }
            .sortedBy { it.fromVersion }
            .fold(snapshot) { upgraded, step -> step.apply(upgraded) }
    }

    private sealed interface UpgradeStep {
        val fromVersion: Int
        fun apply(snapshot: DatabaseBackupSnapshot): DatabaseBackupSnapshot
    }

    private data class AddedColumnsUpgradeStep(
        override val fromVersion: Int,
        /** Table name to the columns the next schema version added, with their backfill value. */
        val addedColumns: Map<String, Map<String, JsonElement>>,
    ) : UpgradeStep {
        override fun apply(snapshot: DatabaseBackupSnapshot): DatabaseBackupSnapshot = snapshot.copy(
            schemaVersion = fromVersion + 1,
            tables = snapshot.tables.mapValues { (table, rows) ->
                val columns = addedColumns[table] ?: return@mapValues rows
                // Backfill first so a column already present in the row keeps its value.
                rows.map { row -> columns + row }
            },
        )
    }

    private data object ReindexWeeklyPlanUpgradeStep : UpgradeStep {
        override val fromVersion: Int = 4

        override fun apply(snapshot: DatabaseBackupSnapshot): DatabaseBackupSnapshot {
            val weeklyPlanRows = snapshot.tables[WEEKLY_PLAN_TABLE].orEmpty()
            val reindexed = weeklyPlanRows
                .groupBy { jsonIntOrNull(it[DAY_OF_WEEK_COLUMN]) ?: Int.MAX_VALUE }
                .flatMap { (_, rows) ->
                    rows.sortedWith(
                        compareBy<Map<String, JsonElement>>(
                            { jsonIntOrNull(it[ORDER_INDEX_COLUMN]) ?: 0 },
                            { it[ID_COLUMN]?.toString().orEmpty() },
                        ),
                    ).mapIndexed { index, row -> row + (ORDER_INDEX_COLUMN to JsonPrimitive(index)) }
                }
            return snapshot.copy(
                schemaVersion = fromVersion + 1,
                tables = snapshot.tables + (WEEKLY_PLAN_TABLE to reindexed),
            )
        }
    }

    private companion object {
        // Mirrors AppDatabase.MIGRATION_1_2. Room 2->3 (MIGRATION_2_3) only relaxed NOT NULL
        // constraints without changing any column set, which is why the backup schema —
        // and BackupJsonCodec.CURRENT_SCHEMA_VERSION — stayed at 2 while Room moved to 3.
        val UPGRADE_STEPS = listOf(
            AddedColumnsUpgradeStep(
                fromVersion = 1,
                addedColumns = mapOf(
                    "body_composition" to mapOf("body_water_mass_kg" to JsonNull),
                ),
            ),
            AddedColumnsUpgradeStep(
                fromVersion = 2,
                addedColumns = mapOf(
                    "weekly_plan" to mapOf(
                        "type" to JsonPrimitive("STRENGTH"),
                        "cardio_type_id" to JsonNull,
                        "cardio_target_duration_sec" to JsonNull,
                    ),
                ),
            ),
            AddedColumnsUpgradeStep(
                fromVersion = 3,
                addedColumns = mapOf(
                    "weekly_plan" to mapOf("order_index" to JsonPrimitive(0)),
                ),
            ),
            ReindexWeeklyPlanUpgradeStep,
        )

        const val WEEKLY_PLAN_TABLE = "weekly_plan"
        const val ID_COLUMN = "id"
        const val DAY_OF_WEEK_COLUMN = "day_of_week"
        const val ORDER_INDEX_COLUMN = "order_index"

        fun jsonIntOrNull(value: JsonElement?): Int? = (value as? JsonPrimitive)?.intOrNull
    }
}
