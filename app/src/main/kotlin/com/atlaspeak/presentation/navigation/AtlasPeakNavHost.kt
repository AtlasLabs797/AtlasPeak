package com.atlaspeak.presentation.navigation

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.atlaspeak.R
import com.atlaspeak.presentation.auth.LoginRoute
import com.atlaspeak.presentation.backup.BackupRestoreRoute
import com.atlaspeak.presentation.body.BodyCompositionRoute
import com.atlaspeak.presentation.cardio.ActiveCardioRoute
import com.atlaspeak.presentation.cardio.CardioCompleteRoute
import com.atlaspeak.presentation.home.HomeRoute
import com.atlaspeak.presentation.onboarding.OnboardingRoute
import com.atlaspeak.presentation.planning.NotificationSettingsRoute
import com.atlaspeak.presentation.planning.WeeklyPlanRoute
import com.atlaspeak.presentation.profile.ProfileRoute
import com.atlaspeak.presentation.progress.ProgressRoute
import com.atlaspeak.presentation.screen.PlaceholderScreen
import com.atlaspeak.presentation.workout.ActiveWorkoutRoute
import com.atlaspeak.presentation.workout.TrainRoute
import com.atlaspeak.presentation.workout.WorkoutCompleteRoute

@Composable
fun AtlasPeakNavHost(
    navController: NavHostController,
) {
    val reduceMotion = LocalContext.current.isAnimatorScaleOff()
    NavHost(
        navController = navController,
        startDestination = AppRoute.Launch.route,
        enterTransition = { atlasEnterTransition(reduceMotion) },
        exitTransition = { atlasExitTransition(reduceMotion) },
        popEnterTransition = { atlasEnterTransition(reduceMotion) },
        popExitTransition = { atlasExitTransition(reduceMotion) },
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
            HomeRoute()
        }
        composable(AppRoute.Train.route) {
            TrainRoute(
                onStartRoutine = { routineId ->
                    navController.navigate(AppRoute.ActiveWorkout.createRoute(routineId))
                },
                onStartCardio = { cardioTypeId, mode ->
                    navController.navigate(AppRoute.ActiveCardio.createRoute(cardioTypeId, mode))
                },
            )
        }
        composable(
            route = AppRoute.ActiveWorkout.route,
            arguments = listOf(navArgument(AppRoute.ActiveWorkout.ROUTINE_ID) { type = NavType.StringType }),
        ) {
            ActiveWorkoutRoute(
                onWorkoutCompleted = { sessionId ->
                    navController.navigate(AppRoute.WorkoutComplete.createRoute(sessionId)) {
                        popUpTo(AppRoute.Train.route)
                    }
                },
            )
        }
        composable(
            route = AppRoute.WorkoutComplete.route,
            arguments = listOf(navArgument(AppRoute.WorkoutComplete.SESSION_ID) { type = NavType.StringType }),
        ) {
            WorkoutCompleteRoute(
                onDone = {
                    navController.navigate(AppRoute.Train.route) {
                        popUpTo(AppRoute.Train.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = AppRoute.ActiveCardio.route,
            arguments = listOf(
                navArgument(AppRoute.ActiveCardio.CARDIO_TYPE_ID) { type = NavType.StringType },
                navArgument(AppRoute.ActiveCardio.MODE) { type = NavType.StringType },
                navArgument(AppRoute.ActiveCardio.TARGET_SECONDS) { type = NavType.IntType },
            ),
        ) {
            ActiveCardioRoute(
                onCardioCompleted = { sessionId ->
                    navController.navigate(AppRoute.CardioComplete.createRoute(sessionId)) {
                        popUpTo(AppRoute.Train.route)
                    }
                },
                onCardioCancelled = {
                    navController.navigate(AppRoute.Train.route) {
                        popUpTo(AppRoute.Train.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = AppRoute.CardioComplete.route,
            arguments = listOf(navArgument(AppRoute.CardioComplete.SESSION_ID) { type = NavType.StringType }),
        ) {
            CardioCompleteRoute(
                onDone = {
                    navController.navigate(AppRoute.Train.route) {
                        popUpTo(AppRoute.Train.route) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoute.Progress.route) {
            ProgressRoute()
        }
        composable(AppRoute.Body.route) {
            BodyCompositionRoute()
        }
        composable(AppRoute.Profile.route) {
            ProfileRoute(
                onWeeklyPlan = { navController.navigate(AppRoute.WeeklyPlan.route) },
                onSettings = { navController.navigate(AppRoute.Settings.route) },
                onBackupRestore = { navController.navigate(AppRoute.BackupRestore.route) },
            )
        }
        composable(AppRoute.WeeklyPlan.route) {
            WeeklyPlanRoute(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Settings.route) {
            NotificationSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.BackupRestore.route) {
            BackupRestoreRoute(onBack = { navController.popBackStack() })
        }
    }
}

private fun android.content.Context.isAnimatorScaleOff(): Boolean {
    return Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

private fun atlasEnterTransition(reduceMotion: Boolean): EnterTransition {
    return if (reduceMotion) EnterTransition.None else fadeIn(animationSpec = tween(220))
}

private fun atlasExitTransition(reduceMotion: Boolean): ExitTransition {
    return if (reduceMotion) ExitTransition.None else fadeOut(animationSpec = tween(120))
}
