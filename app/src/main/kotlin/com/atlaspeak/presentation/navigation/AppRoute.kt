package com.atlaspeak.presentation.navigation

import com.atlaspeak.domain.model.cardio.CardioMode

sealed class AppRoute(val route: String) {
    data object Launch : AppRoute("launch")
    data object Onboarding : AppRoute("onboarding")
    data object AppGraph : AppRoute("app")
    data object Home : AppRoute("home")
    data object Train : AppRoute("train")
    data object Progress : AppRoute("progress")
    data object Body : AppRoute("body")
    data object Profile : AppRoute("profile")
    data object EditProfile : AppRoute("edit_profile")
    data object WeeklyPlan : AppRoute("weekly_plan")
    data object Settings : AppRoute("settings")
    data object ActiveWorkout : AppRoute("active_workout/{routineId}?weeklyPlanSessionId={weeklyPlanSessionId}") {
        const val ROUTINE_ID = "routineId"
        const val WEEKLY_PLAN_SESSION_ID = "weeklyPlanSessionId"
        fun createRoute(routineId: String, weeklyPlanSessionId: String? = null): String =
            if (weeklyPlanSessionId == null) {
                "active_workout/$routineId"
            } else {
                "active_workout/$routineId?weeklyPlanSessionId=$weeklyPlanSessionId"
            }
    }
    data object WorkoutComplete : AppRoute("workout_complete/{sessionId}") {
        const val SESSION_ID = "sessionId"
        fun createRoute(sessionId: String) = "workout_complete/$sessionId"
    }
    data object ActiveCardio : AppRoute(
        "active_cardio/{cardioTypeId}/{mode}/{targetSeconds}?weeklyPlanSessionId={weeklyPlanSessionId}",
    ) {
        const val CARDIO_TYPE_ID = "cardioTypeId"
        const val MODE = "mode"
        const val TARGET_SECONDS = "targetSeconds"
        const val WEEKLY_PLAN_SESSION_ID = "weeklyPlanSessionId"
        const val MODE_TIMER = "timer"
        const val MODE_COUNTDOWN = "countdown"

        fun createRoute(
            cardioTypeId: String,
            mode: CardioMode,
            weeklyPlanSessionId: String? = null,
        ): String {
            val modeSegment = if (mode is CardioMode.Countdown) MODE_COUNTDOWN else MODE_TIMER
            val targetSeconds = (mode as? CardioMode.Countdown)?.targetDurationSeconds ?: 0
            val base = "active_cardio/$cardioTypeId/$modeSegment/$targetSeconds"
            return if (weeklyPlanSessionId == null) base else "$base?weeklyPlanSessionId=$weeklyPlanSessionId"
        }
    }
    data object CardioComplete : AppRoute("cardio_complete/{sessionId}") {
        const val SESSION_ID = "sessionId"
        fun createRoute(sessionId: String) = "cardio_complete/$sessionId"
    }
    data object BackupRestore : AppRoute("backup_restore")
}
