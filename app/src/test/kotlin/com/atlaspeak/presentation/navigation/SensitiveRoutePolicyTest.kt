package com.atlaspeak.presentation.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensitiveRoutePolicyTest {
    @Test
    fun `sensitive routes are still classified but no longer block screenshots`() {
        listOf(
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
        ).forEach { route ->
            assertTrue(isSensitiveRoute(route), route)
            assertFalse(shouldApplySecureFlag(route), route)
        }
    }

    @Test
    fun `non sensitive routes do not block screenshots`() {
        assertFalse(isSensitiveRoute(AppRoute.Launch.route))
        assertFalse(isSensitiveRoute(null))
        assertFalse(shouldApplySecureFlag(AppRoute.Launch.route))
        assertFalse(shouldApplySecureFlag(null))
    }
}
