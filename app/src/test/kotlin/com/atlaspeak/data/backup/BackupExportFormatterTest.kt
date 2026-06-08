package com.atlaspeak.data.backup

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupExportFormatterTest {
    private val formatter = BackupExportFormatter()

    @Test
    fun `manual export excludes auth secrets but keeps user data tables`() {
        val snapshot = DatabaseBackupSnapshot(
            schemaVersion = 2,
            exportedAt = 1_800_000_000_000,
            tables = mapOf(
                "users" to listOf(mapOf("password_hash" to JsonPrimitive("hash"))),
                "auth_security" to listOf(mapOf("failed_attempts" to JsonPrimitive(1))),
                "user_profile" to listOf(mapOf("display_name" to JsonPrimitive("Ana"))),
            ),
        )

        val export = formatter.manualJson(snapshot)

        assertFalse(export.tables.containsKey("users"))
        assertFalse(export.tables.containsKey("auth_security"))
        assertTrue(export.tables.containsKey("user_profile"))
    }

    @Test
    fun `csv files are generated per exportable table with escaped cells`() {
        val snapshot = DatabaseBackupSnapshot(
            schemaVersion = 2,
            exportedAt = 1_800_000_000_000,
            tables = mapOf(
                "workout_sessions" to listOf(
                    mapOf(
                        "id" to JsonPrimitive("session-1"),
                        "notes" to JsonPrimitive("comma, quote \" and\nnewline"),
                        "completed" to JsonPrimitive(1),
                    ),
                ),
                "users" to listOf(mapOf("password_hash" to JsonPrimitive("hash"))),
            ),
        )

        val files = formatter.csvFiles(snapshot)

        assertEquals(setOf("workout_sessions.csv"), files.keys)
        assertEquals(
            "completed,id,notes\r\n1,session-1,\"comma, quote \"\" and\nnewline\"\r\n",
            files.getValue("workout_sessions.csv"),
        )
    }

    @Test
    fun `csv export neutralizes spreadsheet formulas in text cells`() {
        val snapshot = DatabaseBackupSnapshot(
            schemaVersion = 2,
            exportedAt = 1_800_000_000_000,
            tables = mapOf(
                "hc_sleep_sessions" to listOf(
                    mapOf(
                        "duration_ms" to JsonPrimitive(-42),
                        "notes" to JsonPrimitive("+SUM(1;2)"),
                        "provider_record_id" to JsonPrimitive("-cmd"),
                        "source_package" to JsonPrimitive("@evil.provider"),
                        "title" to JsonPrimitive("=HYPERLINK(\"https://attacker.invalid\")"),
                    ),
                ),
            ),
        )

        val files = formatter.csvFiles(snapshot)

        assertEquals(
            "duration_ms,notes,provider_record_id,source_package,title\r\n" +
                "-42,'+SUM(1;2),'-cmd,'@evil.provider,\"'=HYPERLINK(\"\"https://attacker.invalid\"\")\"\r\n",
            files.getValue("hc_sleep_sessions.csv"),
        )
    }
}
