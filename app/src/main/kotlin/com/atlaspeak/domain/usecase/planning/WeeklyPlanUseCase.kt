package com.atlaspeak.domain.usecase.planning

import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.model.planning.WeeklyPlanSession
import com.atlaspeak.domain.model.planning.WeeklyPlanSessionUpdate
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
        val completedKeys = repository.completedTrainingKeys(weekWindow.startInclusive, weekWindow.endExclusive)
        return (FIRST_DAY..LAST_DAY).map { day ->
            (saved[day] ?: WeeklyPlanDay(dayOfWeek = day, isRestDay = day in DEFAULT_REST_DAYS))
                .let { planDay ->
                    planDay.copy(
                        sessions = planDay.sessions.map { session ->
                            val targetId = if (session.type == WeeklyPlanDayType.Cardio) {
                                session.cardioTypeId
                            } else {
                                session.routineId
                            }
                            session.copy(
                                completedThisWeek = completedKeys.any {
                                    it.dayOfWeek == day &&
                                        it.type == session.type &&
                                        it.targetId == targetId
                                },
                            )
                        },
                    )
                }
        }
    }

    suspend fun updateDay(update: WeeklyPlanUpdate): Boolean {
        if (update.dayOfWeek !in FIRST_DAY..LAST_DAY) return false
        val requestedSessions = update.sessions.ifEmpty { listOf(update.toLegacySessionUpdate()) }
        if (requestedSessions.any { !it.notificationTime.isValidClockTime() }) return false
        if (
            !update.isRestDay &&
            requestedSessions.any {
                it.type == WeeklyPlanDayType.Cardio &&
                    it.cardioTypeId != null &&
                    (it.cardioTargetDurationSec == null || it.cardioTargetDurationSec < MIN_CARDIO_TARGET_SECONDS)
            }
        ) {
            return false
        }
        val day = if (update.isRestDay) {
            WeeklyPlanDay(
                dayOfWeek = update.dayOfWeek,
                isRestDay = true,
                sessions = emptyList(),
            )
        } else {
            val sessions = requestedSessions.mapIndexedNotNull { index, sessionUpdate ->
                sessionUpdate.toSession(update.dayOfWeek, index)
            }
            WeeklyPlanDay(
                dayOfWeek = update.dayOfWeek,
                isRestDay = sessions.isEmpty(),
                sessions = sessions,
            )
        }
        repository.replaceDay(day)
        notificationScheduler.rescheduleAll()
        return true
    }

    private fun WeeklyPlanUpdate.toLegacySessionUpdate() = WeeklyPlanSessionUpdate(
        type = type,
        routineId = routineId,
        cardioTypeId = cardioTypeId,
        cardioTargetDurationSec = cardioTargetDurationSec,
        notificationEnabled = notificationEnabled,
        notificationTime = notificationTime,
    )

    private fun WeeklyPlanSessionUpdate.toSession(dayOfWeek: Int, orderIndex: Int): WeeklyPlanSession? {
        return when (type) {
            WeeklyPlanDayType.Cardio -> {
                val targetId = cardioTypeId ?: return null
                val targetSeconds = cardioTargetDurationSec ?: return null
                WeeklyPlanSession(
                    id = id ?: "weekly_plan_${dayOfWeek}_$orderIndex",
                    dayOfWeek = dayOfWeek,
                    orderIndex = orderIndex,
                    type = WeeklyPlanDayType.Cardio,
                    cardioTypeId = targetId,
                    cardioTargetDurationSec = targetSeconds.coerceAtLeast(MIN_CARDIO_TARGET_SECONDS),
                    notificationEnabled = notificationEnabled,
                    notificationTime = notificationTime ?: DEFAULT_REMINDER_TIME,
                )
            }
            WeeklyPlanDayType.Strength -> {
                val targetId = routineId ?: return null
                WeeklyPlanSession(
                    id = id ?: "weekly_plan_${dayOfWeek}_$orderIndex",
                    dayOfWeek = dayOfWeek,
                    orderIndex = orderIndex,
                    type = WeeklyPlanDayType.Strength,
                    routineId = targetId,
                    notificationEnabled = notificationEnabled,
                    notificationTime = notificationTime ?: DEFAULT_REMINDER_TIME,
                )
            }
        }
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
