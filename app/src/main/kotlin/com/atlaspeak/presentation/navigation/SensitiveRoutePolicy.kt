package com.atlaspeak.presentation.navigation

internal fun isSensitiveRoute(route: String?): Boolean = route in sensitiveRoutes

private val sensitiveRoutes = setOf(
    AppRoute.Login.route,
    AppRoute.Biometric.route,
    AppRoute.Onboarding.route,
    AppRoute.Home.route,
    AppRoute.Train.route,
    AppRoute.ActiveWorkout.route,
    AppRoute.WorkoutComplete.route,
    AppRoute.ActiveCardio.route,
    AppRoute.CardioComplete.route,
    AppRoute.Progress.route,
    AppRoute.Body.route,
    AppRoute.Profile.route,
    AppRoute.WeeklyPlan.route,
    AppRoute.Settings.route,
    AppRoute.BackupRestore.route,
)
