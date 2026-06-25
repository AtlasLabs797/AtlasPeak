package com.atlaspeak.presentation.navigation

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BottomNavigationPolicyTest {
    private val atlasPeakAppSource = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/com/atlaspeak/presentation/navigation/AtlasPeakApp.kt")
        .toFile()
        .readText()
    private val atlasPeakNavHostSource = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/com/atlaspeak/presentation/navigation/AtlasPeakNavHost.kt")
        .toFile()
        .readText()

    @Test
    fun `bottom tabs use the authenticated graph as their stable root and preserve tab state`() {
        assertEquals(AppRoute.AppGraph.route, bottomNavigationBackStackRootRoute)
        assertTrue(atlasPeakAppSource.contains("navController.navigateToBottomTab(route)"))
        assertTrue(atlasPeakAppSource.contains("popUpTo(bottomNavigationBackStackRootRoute)"))
        assertTrue(atlasPeakAppSource.contains("saveState = true"))
        assertTrue(atlasPeakAppSource.contains("restoreState = true"))
        assertFalse(atlasPeakAppSource.contains("saveState = false"))
        assertFalse(atlasPeakAppSource.contains("restoreState = false"))
        assertFalse(atlasPeakAppSource.contains("findStartDestination"))
    }

    @Test
    fun `launch enters the authenticated app graph`() {
        assertTrue(atlasPeakNavHostSource.contains("navigation("))
        assertTrue(atlasPeakNavHostSource.contains("route = AppRoute.AppGraph.route"))
        assertTrue(atlasPeakNavHostSource.contains("startDestination = AppRoute.Home.route"))
        assertTrue(atlasPeakNavHostSource.contains("navController.navigate(AppRoute.AppGraph.route)"))
        assertFalse(atlasPeakNavHostSource.contains("AppRoute.Main"))
    }

    @Test
    fun `bottom bar is visible on app chrome routes and hidden on fullscreen routes`() {
        val tabRoutes = bottomTabs.map { it.route.route }

        assertEquals(
            listOf(
                AppRoute.Home.route,
                AppRoute.Train.route,
                AppRoute.Progress.route,
                AppRoute.Body.route,
                AppRoute.Profile.route,
            ),
            tabRoutes,
        )
        tabRoutes.forEach { route -> assertTrue(shouldShowBottomBar(route)) }
        assertTrue(shouldShowBottomBar(AppRoute.EditProfile.route))
        assertTrue(shouldShowBottomBar(AppRoute.WeeklyPlan.route))
        assertTrue(shouldShowBottomBar(AppRoute.Settings.route))
        assertTrue(shouldShowBottomBar(AppRoute.BackupRestore.route))
        assertFalse(shouldShowBottomBar(null))
        assertFalse(shouldShowBottomBar("unknown"))
        assertFalse(shouldShowBottomBar(AppRoute.Launch.route))
        assertFalse(shouldShowBottomBar(AppRoute.Onboarding.route))
        assertFalse(shouldShowBottomBar(AppRoute.AppGraph.route))
        assertFalse(shouldShowBottomBar(AppRoute.ActiveWorkout.route))
        assertFalse(shouldShowBottomBar(AppRoute.WorkoutComplete.route))
        assertFalse(shouldShowBottomBar(AppRoute.ActiveCardio.route))
        assertFalse(shouldShowBottomBar(AppRoute.CardioComplete.route))
    }

    @Test
    fun `profile subroutes keep profile selected in bottom bar`() {
        assertEquals(AppRoute.Profile.route, selectedBottomTabRoute(AppRoute.Profile.route))
        assertEquals(AppRoute.Profile.route, selectedBottomTabRoute(AppRoute.EditProfile.route))
        assertEquals(AppRoute.Profile.route, selectedBottomTabRoute(AppRoute.WeeklyPlan.route))
        assertEquals(AppRoute.Profile.route, selectedBottomTabRoute(AppRoute.Settings.route))
        assertEquals(AppRoute.Profile.route, selectedBottomTabRoute(AppRoute.BackupRestore.route))
        assertEquals(AppRoute.Home.route, selectedBottomTabRoute(AppRoute.Home.route))
        assertEquals(null, selectedBottomTabRoute(AppRoute.Onboarding.route))
    }

    @Test
    fun `bottom tabs are icon only but still accessible`() {
        assertFalse(atlasPeakAppSource.contains("label ="))
        assertFalse(atlasPeakAppSource.contains("labelRes"))
        assertTrue(atlasPeakAppSource.contains("contentDescription = stringResource(tab.contentDescriptionRes)"))
    }
}
