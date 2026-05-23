package com.atlaspeak.presentation.navigation

sealed class AppRoute(val route: String) {
    data object Login : AppRoute("login")
    data object Biometric : AppRoute("biometric")
    data object Onboarding : AppRoute("onboarding")
    data object Home : AppRoute("home")
    data object Train : AppRoute("train")
    data object Progress : AppRoute("progress")
    data object Body : AppRoute("body")
    data object Profile : AppRoute("profile")
    data object ActiveWorkout : AppRoute("active_workout")
    data object WorkoutComplete : AppRoute("workout_complete")
    data object ActiveCardio : AppRoute("active_cardio")
    data object CardioComplete : AppRoute("cardio_complete")
    data object BackupRestore : AppRoute("backup_restore")
}
