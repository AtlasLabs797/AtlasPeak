package com.atlaspeak.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkoutTimerRegistryTest {
    @AfterEach
    fun resetRegistry() {
        WorkoutTimerRegistry.update(WorkoutTimerState())
    }

    @Test
    fun `rest timer expires into alerting state`() {
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = "session-1",
                startedAt = 0L,
                running = true,
            ),
        )

        WorkoutTimerRegistry.startRestTimer(
            id = "rest-1",
            totalSeconds = 2,
            endsAtMillis = 2_000L,
            soundEnabled = true,
            vibrationEnabled = true,
            nowMillis = 0L,
        )
        WorkoutTimerRegistry.tick(2_000L)

        val restTimer = requireNotNull(WorkoutTimerRegistry.state.value.restTimer)
        assertEquals("rest-1", restTimer.id)
        assertEquals(0, restTimer.remainingSeconds)
        assertTrue(restTimer.alerting)
        assertTrue(restTimer.soundEnabled)
        assertTrue(restTimer.vibrationEnabled)
    }

    @Test
    fun `expired rest timer stays active until explicitly cleared`() {
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = "session-1",
                startedAt = 0L,
                running = true,
            ),
        )

        WorkoutTimerRegistry.startRestTimer(
            id = "rest-1",
            totalSeconds = 1,
            endsAtMillis = 1_000L,
            soundEnabled = true,
            vibrationEnabled = true,
            nowMillis = 0L,
        )
        WorkoutTimerRegistry.tick(1_000L)
        WorkoutTimerRegistry.tick(5_000L)

        val restTimer = requireNotNull(WorkoutTimerRegistry.state.value.restTimer)
        assertEquals(0, restTimer.remainingSeconds)
        assertTrue(restTimer.alerting)

        WorkoutTimerRegistry.clearRestTimer()

        assertNull(WorkoutTimerRegistry.state.value.restTimer)
    }

    @Test
    fun `new rest timer replaces the previous one`() {
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = "session-1",
                startedAt = 0L,
                running = true,
            ),
        )

        WorkoutTimerRegistry.startRestTimer(
            id = "rest-1",
            totalSeconds = 90,
            endsAtMillis = 90_000L,
            soundEnabled = true,
            vibrationEnabled = true,
            nowMillis = 0L,
        )
        WorkoutTimerRegistry.startRestTimer(
            id = "rest-2",
            totalSeconds = 30,
            endsAtMillis = 30_000L,
            soundEnabled = false,
            vibrationEnabled = true,
            nowMillis = 0L,
        )

        val restTimer = requireNotNull(WorkoutTimerRegistry.state.value.restTimer)
        assertEquals("rest-2", restTimer.id)
        assertEquals(30, restTimer.totalSeconds)
        assertEquals(30, restTimer.remainingSeconds)
        assertFalse(restTimer.alerting)
        assertFalse(restTimer.soundEnabled)
        assertTrue(restTimer.vibrationEnabled)
    }
}
