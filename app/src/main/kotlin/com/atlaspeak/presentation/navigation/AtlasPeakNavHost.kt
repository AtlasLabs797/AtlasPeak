package com.atlaspeak.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.atlaspeak.R
import com.atlaspeak.presentation.screen.PlaceholderScreen

@Composable
fun AtlasPeakNavHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Home.route,
    ) {
        composable(AppRoute.Login.route) {
            PlaceholderScreen(titleRes = R.string.auth_unlock)
        }
        composable(AppRoute.Biometric.route) {
            PlaceholderScreen(titleRes = R.string.auth_unlock)
        }
        composable(AppRoute.Onboarding.route) {
            PlaceholderScreen(titleRes = R.string.onboarding_welcome_title)
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
    }
}
