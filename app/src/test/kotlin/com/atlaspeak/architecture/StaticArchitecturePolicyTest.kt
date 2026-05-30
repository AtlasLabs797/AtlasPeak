package com.atlaspeak.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaticArchitecturePolicyTest {
    private val root = Path.of(System.getProperty("user.dir"))
    private val mainSource = root.resolve("src/main/kotlin/com/atlaspeak")

    @Test
    fun `presentation layer does not import data layer`() {
        val violations = kotlinFiles(mainSource.resolve("presentation"))
            .filter { file ->
                file.toFile().readLines().any { line ->
                    line.startsWith("import com.atlaspeak.data.") ||
                        line.startsWith("import androidx.room.") ||
                        line.startsWith("import retrofit2.") ||
                        line.startsWith("import okhttp3.")
                }
            }
            .toList()

        assertTrue(violations.isEmpty(), violations.joinToString())
    }

    @Test
    fun `domain layer stays free of Android UI data and transport dependencies`() {
        val blockedImports = listOf(
            "import com.atlaspeak.data.",
            "import com.atlaspeak.presentation.",
            "import androidx.compose.",
            "import androidx.room.",
            "import androidx.work.",
            "import retrofit2.",
            "import okhttp3.",
        )
        val violations = kotlinFiles(mainSource.resolve("domain"))
            .filter { file ->
                file.toFile().readLines().any { line -> blockedImports.any(line::startsWith) }
            }
            .toList()

        assertTrue(violations.isEmpty(), violations.joinToString())
    }

    private fun kotlinFiles(path: Path) =
        Files.walk(path).asSequence()
            .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
}
