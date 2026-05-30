package com.atlaspeak.data.notification

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduleCalculator(
    private val zoneProvider: () -> ZoneId,
) {
    @Inject
    constructor() : this({ ZoneId.systemDefault() })

    fun nextDailyRunAt(nowMillis: Long, time: String): Long {
        val zone = zoneProvider()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val localTime = LocalTime.parse(time)
        var candidate = now.toLocalDate().atTime(localTime).atZone(zone)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun nextWeeklyRunAt(nowMillis: Long, dayOfWeek: Int, time: String): Long {
        val zone = zoneProvider()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val localTime = LocalTime.parse(time)
        val targetDay = DayOfWeek.of(dayOfWeek)
        val daysUntilTarget = (targetDay.value - now.dayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
        var candidate = now.toLocalDate().plusDays(daysUntilTarget.toLong()).atTime(localTime).atZone(zone)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
