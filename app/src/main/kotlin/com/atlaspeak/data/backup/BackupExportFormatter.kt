package com.atlaspeak.data.backup

import javax.inject.Inject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class BackupExportFormatter @Inject constructor() {
    fun manualJson(snapshot: DatabaseBackupSnapshot): DatabaseBackupSnapshot {
        return snapshot.copy(tables = snapshot.tables.filterKeys { it !in SENSITIVE_EXPORT_TABLES })
    }

    fun csvFiles(snapshot: DatabaseBackupSnapshot): Map<String, String> {
        return manualJson(snapshot).tables.mapNotNull { (table, rows) ->
            if (rows.isEmpty()) return@mapNotNull null
            val headers = rows.flatMap { it.keys }.toSortedSet().toList()
            val csv = buildString {
                append(headers.joinToString(CSV_SEPARATOR))
                append(CSV_ROW_SEPARATOR)
                rows.forEach { row ->
                    append(headers.joinToString(CSV_SEPARATOR) { header -> escapeCsv(row[header]) })
                    append(CSV_ROW_SEPARATOR)
                }
            }
            "$table.csv" to csv
        }.toMap()
    }

    private fun escapeCsv(value: JsonElement?): String {
        val raw = when (value) {
            null,
            JsonNull,
            -> ""
            is JsonPrimitive -> value.contentValue()
            else -> value.toString()
        }
        val csvSafe = if (value is JsonPrimitive && value.isString) {
            raw.neutralizeSpreadsheetFormula()
        } else {
            raw
        }
        val escaped = csvSafe.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun String.neutralizeSpreadsheetFormula(): String {
        val firstMeaningfulChar = firstOrNull { !it.isWhitespace() } ?: return this
        return if (firstMeaningfulChar in SPREADSHEET_FORMULA_PREFIXES) {
            "$CSV_FORMULA_ESCAPE$this"
        } else {
            this
        }
    }

    private fun JsonPrimitive.contentValue(): String {
        booleanOrNull?.let { return it.toString() }
        longOrNull?.let { return it.toString() }
        doubleOrNull?.let { return it.toString() }
        return content
    }

    companion object {
        val SENSITIVE_EXPORT_TABLES = setOf("users", "auth_security")
        private const val CSV_SEPARATOR = ","
        private const val CSV_ROW_SEPARATOR = "\r\n"
        private const val CSV_FORMULA_ESCAPE = "'"
        private val SPREADSHEET_FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    }
}
