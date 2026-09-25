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

    @Test
    fun `completing an active session pops itself so back never re-triggers completion`() {
        // BUG: popUpTo(Train) no saca nada cuando la sesion se arranco desde Home
        // (Train no esta en el back stack), asi que "atras" desde Completado volvia a
        // ActiveWorkout/ActiveCardio, cuyo LaunchedEffect re-navegaba a Completado
        // (trampa de back button). popUpTo(route: String) en Navigation 2.8.5 hace match
        // exacto contra el route-pattern registrado de la destination (NavDestination.route),
        // asi que pasar el pattern sin resolver (con placeholders) es valido y saca
        // exactamente esa pantalla del back stack.
        assertTrue(
            atlasPeakNavHostSource.contains(
                "popUpTo(AppRoute.ActiveWorkout.route) { inclusive = true }",
            ),
        )
        assertTrue(
            atlasPeakNavHostSource.contains(
                "popUpTo(AppRoute.ActiveCardio.route) { inclusive = true }",
            ),
        )
    }

    @Test
    fun `active session and profile subroutes use launchSingleTop to avoid double tap duplicates`() {
        // Comprobacion textual, en la linea del resto de StaticUiPolicy-style tests: cada
        // destino susceptible de doble tap (ActiveWorkout/ActiveCardio desde Home y Train,
        // y las subrutas de Profile) debe abrir su navigate(...) con launchSingleTop = true.
        val occurrences = Regex("launchSingleTop = true").findAll(atlasPeakNavHostSource).count()
        // Launch->AppGraph, Onboarding->AppGraph, Home->ActiveWorkout, Home->ActiveCardio,
        // Train->ActiveWorkout, Train->ActiveCardio, Discard/Complete->Train (x4),
        // EditProfile, WeeklyPlan, Settings, BackupRestore = 14 (minimo; rutas nuevas pueden sumar).
        assertTrue(occurrences >= 14, atlasPeakNavHostSource)

        listOf(
            "AppRoute.EditProfile.route) { launchSingleTop = true }",
            "AppRoute.WeeklyPlan.route) { launchSingleTop = true }",
            "AppRoute.Settings.route) { launchSingleTop = true }",
            "AppRoute.BackupRestore.route) { launchSingleTop = true }",
        ).forEach { needle ->
            assertTrue(atlasPeakNavHostSource.contains(needle), "missing launchSingleTop for $needle")
        }
    }
}
