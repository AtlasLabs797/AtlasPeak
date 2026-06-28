package com.atlaspeak.data.backup

import com.atlaspeak.data.db.AppDatabase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupSnapshotUpgraderTest {
    private val upgrader = BackupSnapshotUpgrader()

    @Test
    fun `schema v1 snapshot gains body water mass column with null backfill`() {
        val upgraded = upgrader.upgradeToCurrent(
            snapshot(
                schemaVersion = 1,
                bodyCompositionRows = listOf(
                    mapOf(
                        "id" to JsonPrimitive("entry-1"),
                        "weight_kg" to JsonPrimitive(82.5),
                    ),
                ),
            ),
        )

        assertEquals(BackupJsonCodec.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion)
        val row = upgraded.tables.getValue("body_composition").single()
        assertEquals(JsonNull, row.getValue("body_water_mass_kg"))
        // Pre-existing data must survive the upgrade untouched.
        assertEquals(JsonPrimitive("entry-1"), row.getValue("id"))
        assertEquals(JsonPrimitive(82.5), row.getValue("weight_kg"))
    }

    @Test
    fun `upgrade keeps an already present column value instead of clobbering it`() {
        val upgraded = upgrader.upgradeToCurrent(
            snapshot(
                schemaVersion = 1,
                bodyCompositionRows = listOf(
                    mapOf(
                        "id" to JsonPrimitive("entry-1"),
                        "body_water_mass_kg" to JsonPrimitive(40.2),
                    ),
                ),
            ),
        )

        assertEquals(
            JsonPrimitive(40.2),
            upgraded.tables.getValue("body_composition").single().getValue("body_water_mass_kg"),
        )
    }

    @Test
    fun `schema v2 snapshot gains cardio planning columns`() {
        val upgraded = upgrader.upgradeToCurrent(
            snapshot(
                schemaVersion = 2,
                bodyCompositionRows = emptyList(),
                weeklyPlanRows = listOf(
                    mapOf(
                        "id" to JsonPrimitive("weekly_plan_1"),
                        "day_of_week" to JsonPrimitive(1),
                        "routine_id" to JsonPrimitive("routine-1"),
                    ),
                ),
            ),
        )

        assertEquals(BackupJsonCodec.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion)
        val row = upgraded.tables.getValue("weekly_plan").single()
        assertEquals(JsonPrimitive("STRENGTH"), row.getValue("type"))
        assertEquals(JsonNull, row.getValue("cardio_type_id"))
        assertEquals(JsonNull, row.getValue("cardio_target_duration_sec"))
        assertEquals(JsonPrimitive("routine-1"), row.getValue("routine_id"))
        assertEquals(JsonPrimitive(0), row.getValue("order_index"))
    }


    @Test
    fun `current schema snapshot passes through unchanged`() {
        val current = snapshot(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION,
            bodyCompositionRows = listOf(
                mapOf(
                    "id" to JsonPrimitive("entry-1"),
                    "body_water_mass_kg" to JsonNull,
                ),
            ),
        )

        assertEquals(current, upgrader.upgradeToCurrent(current))
    }

    @Test
    fun `future schema snapshot is rejected with a specific message`() {
        val future = snapshot(
            schemaVersion = BackupJsonCodec.CURRENT_SCHEMA_VERSION + 1,
            bodyCompositionRows = emptyList(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            upgrader.upgradeToCurrent(future)
        }
        assertTrue(error.message.orEmpty().contains("newer than the supported"))
    }

    private fun snapshot(
        schemaVersion: Int,
        bodyCompositionRows: List<Map<String, JsonElement>>,
        weeklyPlanRows: List<Map<String, JsonElement>> = emptyList(),
    ) = DatabaseBackupSnapshot(
        schemaVersion = schemaVersion,
        exportedAt = 1_800_000_000_000,
        tables = AppDatabase.TABLES.associateWith { table ->
            when (table) {
                "body_composition" -> bodyCompositionRows
                "weekly_plan" -> weeklyPlanRows
                else -> emptyList()
            }
        },
    )
}
