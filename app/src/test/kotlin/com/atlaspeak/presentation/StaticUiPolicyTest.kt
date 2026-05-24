package com.atlaspeak.presentation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaticUiPolicyTest {
    private val root = Path.of(System.getProperty("user.dir"))
    private val presentationSource = root.resolve("src/main/kotlin/com/atlaspeak/presentation")

    @Test
    fun `presentation code does not add hardcoded visible strings`() {
        val findings = kotlinFiles().flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.contains("Text(\"") || hardcodedNamedString.containsMatchIn(line)) {
                    "${file.fileName}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

        assertTrue(findings.isEmpty(), findings.joinToString(separator = "\n"))
    }

    @Test
    fun `icon only buttons do not use null content descriptions`() {
        val findings = kotlinFiles().flatMap { file ->
            val lines = Files.readAllLines(file)
            lines.mapIndexedNotNull { index, line ->
                if (!line.contains("IconButton(")) return@mapIndexedNotNull null
                val iconButtonWindow = lines.drop(index).take(12).joinToString("\n")
                if (iconButtonWindow.contains("contentDescription = null")) {
                    "${file.fileName}:${index + 1}"
                } else {
                    null
                }
            }
        }

        assertTrue(findings.isEmpty(), findings.joinToString(separator = "\n"))
    }

    @Test
    fun `theme typography uses bundled Atlas Peak fonts`() {
        val typography = presentationSource.resolve("theme/Typography.kt").toFile().readText()

        assertFalse(typography.contains("FontFamily.SansSerif"))
        assertTrue(typography.contains("poppins_semibold"))
        assertTrue(typography.contains("poppins_bold"))
        assertTrue(typography.contains("inter_variable"))
    }

    @Test
    fun `spanish and english string resources expose the same keys`() {
        val res = root.resolve("src/main/res")
        val spanish = stringKeys(res.resolve("values/strings.xml"))
        val english = stringKeys(res.resolve("values-en/strings.xml"))

        assertTrue((spanish - english).isEmpty(), "Missing EN keys: ${(spanish - english).joinToString()}")
        assertTrue((english - spanish).isEmpty(), "Missing ES keys: ${(english - spanish).joinToString()}")
    }

    private fun kotlinFiles(): List<Path> =
        Files.walk(presentationSource).asSequence()
            .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
            .toList()

    private fun stringKeys(path: Path): Set<String> =
        stringName.findAll(path.toFile().readText())
            .map { it.groupValues[1] }
            .toSet()

    private companion object {
        val hardcodedNamedString = Regex("""\b(text|label|title|body|contentDescription)\s*=\s*"[^"]+"""")
        val stringName = Regex("""<string\s+name="([^"]+)"""")
    }
}
