package com.atlaspeak.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atlaspeak.R

@Composable
fun AtlasPeakApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route.route }

    SecureScreenEffect(currentRoute)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AtlasPeakBottomBar(
                    selectedRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            AtlasPeakNavHost(navController = navController)
        }
    }
}

@Composable
private fun AtlasPeakBottomBar(
    selectedRoute: String?,
    onTabSelected: (BottomTab) -> Unit,
) {
    NavigationBar {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedRoute == tab.route.route,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.contentDescriptionRes),
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

private data class BottomTab(
    val route: AppRoute,
    val labelRes: Int,
    val contentDescriptionRes: Int,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(AppRoute.Home, R.string.nav_home, R.string.nav_cd_home, Icons.Filled.Home),
    BottomTab(AppRoute.Train, R.string.nav_train, R.string.nav_cd_train, Icons.Filled.FitnessCenter),
    BottomTab(AppRoute.Progress, R.string.nav_progress, R.string.nav_cd_progress, Icons.Filled.Insights),
    BottomTab(AppRoute.Body, R.string.nav_body, R.string.nav_cd_body, Icons.Filled.MonitorWeight),
    BottomTab(AppRoute.Profile, R.string.nav_profile, R.string.nav_cd_profile, Icons.Filled.Person),
)
