package com.atlaspeak.presentation.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atlaspeak.R

@Composable
fun AtlasPeakApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route.route }

    SecureScreenEffect(currentRoute)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AtlasPeakBottomBar(
                    selectedRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route.route) {
                            popUpTo(AppRoute.Home.route) {
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
        ) {
            NavigationBar(
                modifier = Modifier.height(72.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route.route,
                        onClick = { onTabSelected(tab) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
