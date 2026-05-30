package com.atlaspeak.data.notification

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationScheduleCalculatorTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val calculator = NotificationScheduleCalculator(zoneProvider = { zone })

    @Test
    fun `next daily delay targets today when time is still ahead`() {
        val now = millis(2026, 5, 18, 8, 0)

        val next = calculator.nextDailyRunAt(now, "08:30")

        assertEquals(millis(2026, 5, 18, 8, 30), next)
    }

    @Test
    fun `next daily delay rolls to tomorrow when time already passed`() {
        val now = millis(2026, 5, 18, 8, 31)

        val next = calculator.nextDailyRunAt(now, "08:30")

        assertEquals(millis(2026, 5, 19, 8, 30), next)
    }

    @Test
    fun `next weekly delay targets same weekday when time is ahead`() {
        val monday = millis(2026, 5, 18, 17, 0)

        val next = calculator.nextWeeklyRunAt(monday, DayOfWeek.MONDAY.value, "18:00")

        assertEquals(millis(2026, 5, 18, 18, 0), next)
    }

    @Test
    fun `next weekly delay rolls one week when weekday time passed`() {
        val monday = millis(2026, 5, 18, 18, 1)

        val next = calculator.nextWeeklyRunAt(monday, DayOfWeek.MONDAY.value, "18:00")

        assertEquals(millis(2026, 5, 25, 18, 0), next)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}
