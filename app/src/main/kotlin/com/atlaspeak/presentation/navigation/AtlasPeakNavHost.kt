package com.atlaspeak.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.atlaspeak.R
import com.atlaspeak.presentation.auth.LoginRoute
import com.atlaspeak.presentation.onboarding.OnboardingRoute
import com.atlaspeak.presentation.screen.PlaceholderScreen

@Composable
fun AtlasPeakNavHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Launch.route,
    ) {
        composable(AppRoute.Launch.route) {
            val viewModel: LaunchViewModel = hiltViewModel()
            val state = viewModel.state.collectAsStateWithLifecycle().value
            LaunchedEffect(state) {
                when (state) {
                    LaunchState.Loading -> Unit
                    LaunchState.Onboarding -> navController.navigate(AppRoute.Onboarding.route) {
                        popUpTo(AppRoute.Launch.route) { inclusive = true }
                    }
                    LaunchState.Login -> navController.navigate(AppRoute.Login.route) {
                        popUpTo(AppRoute.Launch.route) { inclusive = true }
                    }
                }
            }
            PlaceholderScreen(titleRes = R.string.state_loading)
        }
        composable(AppRoute.Login.route) {
            LoginRoute(
                onAuthenticated = {
                    navController.navigate(AppRoute.Home.route) {
                        popUpTo(AppRoute.Login.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoute.Biometric.route) {
            PlaceholderScreen(titleRes = R.string.auth_unlock)
        }
        composable(AppRoute.Onboarding.route) {
            OnboardingRoute(
                onCompleted = {
                    navController.navigate(AppRoute.Home.route) {
                        popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoute.Home.route) {
            PlaceholderScreen(titleRes = R.string.screen_home_title)
        }
        composable(AppRoute.Train.route) {
            PlaceholderScreen(titleRes = R.string.screen_train_title)
        }
        composable(AppRoute.Progress.route) {
            PlaceholderScreen(titleRes = R.string.screen_progress_title)
        }
        composable(AppRoute.Body.route) {
            PlaceholderScreen(titleRes = R.string.screen_body_title)
        }
        composable(AppRoute.Profile.route) {
            PlaceholderScreen(titleRes = R.string.screen_profile_title)
        }
        composable(AppRoute.BackupRestore.route) {
            PlaceholderScreen(titleRes = R.string.screen_backup_restore_title)
        }
    }
}
