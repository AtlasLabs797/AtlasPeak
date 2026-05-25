package com.atlaspeak.presentation.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensitiveRoutePolicyTest {
    @Test
    fun `all authenticated health and workout routes are protected from screenshots`() {
        listOf(
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
        ).forEach { route ->
            assertTrue(isSensitiveRoute(route), route)
        }
    }

    @Test
    fun `launch route stays outside FLAG_SECURE to avoid flashing policy churn`() {
        assertFalse(isSensitiveRoute(AppRoute.Launch.route))
        assertFalse(isSensitiveRoute(null))
    }

    @Test
    fun `debug emulator bypasses FLAG_SECURE for visual QA only`() {
        assertFalse(shouldApplySecureFlag(AppRoute.Onboarding.route, debugBuild = true, emulator = true))
        assertTrue(shouldApplySecureFlag(AppRoute.Onboarding.route, debugBuild = true, emulator = false))
        assertTrue(shouldApplySecureFlag(AppRoute.Onboarding.route, debugBuild = false, emulator = true))
        assertFalse(shouldApplySecureFlag(AppRoute.Launch.route, debugBuild = true, emulator = true))
    }
}
