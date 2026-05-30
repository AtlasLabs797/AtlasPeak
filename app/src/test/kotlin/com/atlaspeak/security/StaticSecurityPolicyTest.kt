package com.atlaspeak.security

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaticSecurityPolicyTest {
    private val root = Path.of(System.getProperty("user.dir"))
    private val mainSource = root.resolve("src/main")

    @Test
    fun `all notification builders and channels are private on lockscreen`() {
        val kotlin = mainSource.resolve("kotlin")
        val builderCount = countMatches(kotlin, Regex("""NotificationCompat[.]Builder\s*[(]"""))
        val privateBuilderCount = countMatches(kotlin, Regex("""[.]setVisibility\s*[(]NotificationCompat[.]VISIBILITY_PRIVATE[)]"""))
        val channelCount = countMatches(kotlin, Regex("""(?<![A-Za-z0-9_])NotificationChannel\s*[(]"""))
        val privateChannelCount = countMatches(kotlin, Regex("""lockscreenVisibility\s*="""))

        assertEquals(builderCount, privateBuilderCount)
        assertEquals(channelCount, privateChannelCount)
    }

    @Test
    fun `manifest does not request high risk permissions outside the v1 threat model`() {
        val manifest = mainSource.resolve("AndroidManifest.xml").toFile().readText()

        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
        assertFalse(manifest.contains("SCHEDULE_EXACT_ALARM"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun `production code does not contain direct platform logging calls`() {
        val filesWithLogging = Files.walk(mainSource.resolve("kotlin")).asSequence()
            .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
            .filter { path ->
                val content = path.toFile().readText()
                content.contains("Log.") || content.contains("println(") || content.contains("printStackTrace(")
            }
            .toList()

        assertTrue(filesWithLogging.isEmpty(), filesWithLogging.joinToString())
    }

    @Test
    fun `sqlcipher native library is loaded before application database access`() {
        val application = mainSource.resolve("kotlin/com/atlaspeak/AtlasPeakApplication.kt").toFile().readText()

        assertTrue(application.contains("override fun attachBaseContext(base: Context)"))
        assertTrue(application.contains("System.loadLibrary(\"sqlcipher\")"))
        assertTrue(
            application.indexOf("System.loadLibrary(\"sqlcipher\")") <
                application.indexOf("override fun onCreate()"),
        )
    }

    @Test
    fun `workout foreground service preserves failed startup state for the UI`() {
        val service = mainSource.resolve("kotlin/com/atlaspeak/service/WorkoutForegroundService.kt").toFile().readText()

        assertTrue(service.contains("stopTimer(clearState = !WorkoutTimerRegistry.state.value.failed)"))
        assertTrue(service.contains("ACTION_STOP -> stopTimer(clearState = true)"))
    }

    private fun countMatches(path: Path, pattern: Regex): Int =
        Files.walk(path).asSequence()
            .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
            .sumOf { file -> pattern.findAll(file.toFile().readText()).count() }
}
