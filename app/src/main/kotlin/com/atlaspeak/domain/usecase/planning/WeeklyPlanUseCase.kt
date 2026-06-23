package com.atlaspeak.domain.usecase.planning

import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.model.planning.WeeklyPlanUpdate
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class WeeklyPlanUseCase(
    private val repository: WeeklyPlanRepository,
    private val notificationScheduler: NotificationScheduler,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        repository: WeeklyPlanRepository,
        notificationScheduler: NotificationScheduler,
    ) : this(
        repository = repository,
        notificationScheduler = notificationScheduler,
        now = { System.currentTimeMillis() },
    )

    suspend fun plan(): List<WeeklyPlanDay> {
        val saved = repository.plan().associateBy { it.dayOfWeek }
        val weekWindow = currentWeekWindow()
        val completedDays = repository.completedTrainingDays(weekWindow.startInclusive, weekWindow.endExclusive)
        return (FIRST_DAY..LAST_DAY).map { day ->
            (saved[day] ?: WeeklyPlanDay(dayOfWeek = day, isRestDay = day in DEFAULT_REST_DAYS))
                .copy(completedThisWeek = day in completedDays)
        }
    }

    suspend fun updateDay(update: WeeklyPlanUpdate): Boolean {
        if (update.dayOfWeek !in FIRST_DAY..LAST_DAY) return false
        if (!update.notificationTime.isValidClockTime()) return false
        val day = if (update.isRestDay) {
            WeeklyPlanDay(
                dayOfWeek = update.dayOfWeek,
                type = WeeklyPlanDayType.Strength,
                routineId = null,
                routineName = null,
                cardioTypeId = null,
                cardioTypeName = null,
                cardioTargetDurationSec = null,
                isRestDay = true,
                notificationEnabled = false,
                notificationTime = null,
            )
        } else if (update.type == WeeklyPlanDayType.Cardio) {
            WeeklyPlanDay(
                dayOfWeek = update.dayOfWeek,
                type = WeeklyPlanDayType.Cardio,
                routineId = null,
                routineName = null,
                cardioTypeId = update.cardioTypeId,
                cardioTypeName = null,
                cardioTargetDurationSec = (update.cardioTargetDurationSec ?: DEFAULT_CARDIO_TARGET_SECONDS)
                    .coerceAtLeast(MIN_CARDIO_TARGET_SECONDS),
                isRestDay = false,
                notificationEnabled = update.notificationEnabled && update.cardioTypeId != null,
                notificationTime = if (update.cardioTypeId == null) null else update.notificationTime ?: DEFAULT_REMINDER_TIME,
            )
        } else {
            WeeklyPlanDay(
                dayOfWeek = update.dayOfWeek,
                type = WeeklyPlanDayType.Strength,
                routineId = update.routineId,
                routineName = null,
                cardioTypeId = null,
                cardioTypeName = null,
                cardioTargetDurationSec = null,
                isRestDay = false,
                notificationEnabled = update.notificationEnabled && update.routineId != null,
                notificationTime = if (update.routineId == null) null else update.notificationTime ?: DEFAULT_REMINDER_TIME,
            )
        }
        repository.upsert(day)
        notificationScheduler.rescheduleAll()
        return true
    }

    private fun currentWeekWindow(): WeekWindow {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val nextMonday = monday.plusDays(DAYS_PER_WEEK.toLong())
        return WeekWindow(
            startInclusive = monday.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = nextMonday.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    private data class WeekWindow(
        val startInclusive: Long,
        val endExclusive: Long,
    )

    companion object {
        const val DEFAULT_REMINDER_TIME = "18:00"
        const val DEFAULT_CARDIO_TARGET_SECONDS = 45 * 60
        private const val FIRST_DAY = 1
        private const val LAST_DAY = 7
        private const val DAYS_PER_WEEK = 7
        private const val MIN_CARDIO_TARGET_SECONDS = 60
        private val DEFAULT_REST_DAYS = setOf(6, 7)
    }
}
