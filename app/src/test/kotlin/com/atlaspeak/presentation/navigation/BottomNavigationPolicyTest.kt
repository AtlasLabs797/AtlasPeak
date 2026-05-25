package com.atlaspeak.presentation.navigation

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BottomNavigationPolicyTest {
    private val atlasPeakAppSource = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/com/atlaspeak/presentation/navigation/AtlasPeakApp.kt")
        .toFile()
        .readText()

    @Test
    fun `bottom tabs preserve home as the back stack root`() {
        assertTrue(atlasPeakAppSource.contains("popUpTo(AppRoute.Home.route)"))
        assertFalse(atlasPeakAppSource.contains("findStartDestination"))
    }
}
