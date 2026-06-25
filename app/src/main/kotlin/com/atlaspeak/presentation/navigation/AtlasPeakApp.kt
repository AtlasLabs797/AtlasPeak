package com.atlaspeak.presentation.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atlaspeak.R
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun AtlasPeakApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = shouldShowBottomBar(currentRoute)
    val selectedBottomRoute = selectedBottomTabRoute(currentRoute)

    SecureScreenEffect(currentRoute)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AtlasPeakBottomBar(
                    selectedRoute = selectedBottomRoute,
                    onTabSelected = { route ->
                        navController.navigateToBottomTab(route)
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            AtlasPeakNavHost(navController = navController)
        }
    }
}

@Composable
private fun AtlasPeakBottomBar(
    selectedRoute: String?,
    onTabSelected: (BottomTab) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = spacing.screen, vertical = spacing.compact),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(282.dp),
            shape = RoundedCornerShape(999.dp),
            color = atlasColors.surface.copy(alpha = 0.92f),
            contentColor = atlasColors.ink,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, atlasColors.line2),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.xs, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomTabs.forEach { tab ->
                    val selected = selectedRoute == tab.route.route
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(horizontal = spacing.xxs),
                        onClick = { onTabSelected(tab) },
                        shape = if (selected) CircleShape else RoundedCornerShape(14.dp),
                        color = if (selected) atlasColors.ink else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (selected) {
                            atlasColors.onAccent
                        } else {
                            atlasColors.ink4
                        },
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.contentDescriptionRes),
                                modifier = Modifier.size(if (selected) 23.dp else 22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class BottomTab(
    val route: AppRoute,
    val contentDescriptionRes: Int,
    val icon: ImageVector,
)

internal val bottomNavigationBackStackRootRoute = AppRoute.AppGraph.route

internal fun NavHostController.navigateToBottomTab(tab: BottomTab) {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute == tab.route.route) return

    // Patrón estándar M3 de bottom navigation: guardar/restaurar el estado de cada pestaña
    // para que cambiar de tab no destruya borradores (rutinas a medias, scroll, etc.).
    navigate(tab.route.route) {
        popUpTo(bottomNavigationBackStackRootRoute) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun shouldShowBottomBar(route: String?): Boolean {
    return route in bottomNavigationChromeRoutes
}

internal fun selectedBottomTabRoute(route: String?): String? {
    return when (route) {
        AppRoute.EditProfile.route,
        AppRoute.WeeklyPlan.route,
        AppRoute.Settings.route,
        AppRoute.BackupRestore.route -> AppRoute.Profile.route
        else -> bottomTabs.firstOrNull { it.route.route == route }?.route?.route
    }
}

internal val bottomTabs = listOf(
    BottomTab(AppRoute.Home, R.string.nav_cd_home, Icons.Filled.Home),
    BottomTab(AppRoute.Train, R.string.nav_cd_train, Icons.Filled.FitnessCenter),
    BottomTab(AppRoute.Progress, R.string.nav_cd_progress, Icons.Filled.Insights),
    BottomTab(AppRoute.Body, R.string.nav_cd_body, Icons.Filled.MonitorWeight),
    BottomTab(AppRoute.Profile, R.string.nav_cd_profile, Icons.Filled.Person),
)

private val bottomNavigationChromeRoutes = bottomTabs.map { it.route.route }.toSet() + setOf(
    AppRoute.EditProfile.route,
    AppRoute.WeeklyPlan.route,
    AppRoute.Settings.route,
    AppRoute.BackupRestore.route,
)
