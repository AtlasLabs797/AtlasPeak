package com.atlaspeak.presentation.component

import com.atlaspeak.data.db.seed.SeedData
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExerciseIconTest {
    private val root = Path.of(System.getProperty("user.dir"))
    private val drawableDir = root.resolve("src/main/res/drawable")

    @Test
    fun `every preset exercise has exactly one icon and no stale entries`() {
        val presetIds = SeedData.exercises.map { it.id }.toSet()
        assertEquals(presetIds, presetExerciseIcons.keys)
    }

    @Test
    fun `no two exercises share the same drawable`() {
        val values = presetExerciseIcons.values
        assertEquals(values.size, values.toSet().size, "Duplicate drawable resources: $values")
    }

    @Test
    fun `every mapped drawable file exists on disk`() {
        val missing = presetExerciseIcons.keys.filterNot { id ->
            drawableFile(id).let { Files.exists(it) }
        }
        assertTrue(missing.isEmpty(), "Missing drawable files for: $missing")
    }

    @Test
    fun `no two icon drawables are byte-for-byte copies`() {
        val existing = presetExerciseIcons.keys.filter { Files.exists(drawableFile(it)) }
        val hashes = existing.associateWith { id -> sha256(Files.readAllBytes(drawableFile(id))) }
        val duplicates = hashes.entries
            .groupBy({ it.value }, { it.key })
            .values
            .filter { it.size > 1 }
        assertTrue(duplicates.isEmpty(), "Icons with identical content: $duplicates")
    }

    @Test
    fun `monogram uses first letters of the first two words`() {
        assertEquals("R", exerciseMonogram("Remo"))
        assertEquals("PA", exerciseMonogram("Press Arnold"))
        assertEquals("RC", exerciseMonogram("Remo con cable"))
    }

    @Test
    fun `monogram collapses extra whitespace`() {
        assertEquals("PA", exerciseMonogram("  Press   Arnold  "))
    }

    @Test
    fun `monogram uppercases lowercase input`() {
        assertEquals("PA", exerciseMonogram("press arnold"))
    }

    @Test
    fun `monogram keeps accented letters`() {
        assertEquals("SR", exerciseMonogram("sentadilla rumana"))
        assertEquals("Ó", exerciseMonogram("Óscar"))
    }

    @Test
    fun `monogram falls back to a placeholder for blank names`() {
        assertEquals("?", exerciseMonogram(""))
        assertEquals("?", exerciseMonogram("   "))
    }

    private fun drawableFile(exerciseId: String): Path {
        val suffix = exerciseId.removePrefix("preset_")
        return drawableDir.resolve("ic_exercise_$suffix.xml")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
