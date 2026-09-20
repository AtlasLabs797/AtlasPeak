package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.dao.WeeklyPlanRow
import com.atlaspeak.data.db.entity.WeeklyPlanEntity
import com.atlaspeak.domain.model.planning.WeeklyPlanCompletionKey
import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanSession
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWeeklyPlanRepository @Inject constructor(
    private val database: AppDatabase,
) : WeeklyPlanRepository {
    override suspend fun plan(): List<WeeklyPlanDay> {
        return database.weeklyPlanDao().getPlan()
            .groupBy { it.dayOfWeek }
            .toSortedMap()
            .map { (dayOfWeek, rows) -> rows.toDomainDay(dayOfWeek) }
    }

    override suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int> {
        return database.weeklyPlanDao()
            .getCompletedTrainingSessionStartTimes(startInclusive, endExclusive)
            .map { it.toIsoDayOfWeek() }
            .toSet()
    }

    override suspend fun completedTrainingKeys(startInclusive: Long, endExclusive: Long): Set<WeeklyPlanCompletionKey> {
        return database.weeklyPlanDao()
            .getCompletedTrainingRows(startInclusive, endExclusive)
            .mapNotNull { row ->
                val type = row.type.toPlanType()
                val targetId = if (type == WeeklyPlanDayType.Cardio) row.cardioTypeId else row.routineId
                targetId?.let {
                    WeeklyPlanCompletionKey(
                        dayOfWeek = row.startTime.toIsoDayOfWeek(),
                        type = type,
                        targetId = it,
                        planSessionId = row.weeklyPlanSessionId,
                    )
                }
            }
            .toSet()
    }

    override suspend fun upsert(day: WeeklyPlanDay) {
        replaceDay(day)
    }

    override suspend fun replaceDay(day: WeeklyPlanDay) {
        database.withTransaction {
            database.weeklyPlanDao().deleteDay(day.dayOfWeek)
            database.weeklyPlanDao().upsert(day.toEntities())
        }
    }

    private fun List<WeeklyPlanRow>.toDomainDay(dayOfWeek: Int): WeeklyPlanDay {
        val restOnly = any { it.isRestDay } && none { !it.isRestDay && (it.routineId != null || it.cardioTypeId != null) }
        return WeeklyPlanDay(
            dayOfWeek = dayOfWeek,
            isRestDay = restOnly,
            sessions = if (restOnly) {
                emptyList()
            } else {
                filterNot { it.isRestDay }
                    .mapNotNull { it.toDomainSession() }
                    .sortedBy { it.orderIndex }
            },
        )
    }

    private fun WeeklyPlanRow.toDomainSession(): WeeklyPlanSession? {
        val planType = type.toPlanType()
        if (planType == WeeklyPlanDayType.Strength && routineId == null) return null
        if (planType == WeeklyPlanDayType.Cardio && cardioTypeId == null) return null
        return WeeklyPlanSession(
            id = id,
            dayOfWeek = dayOfWeek,
            orderIndex = orderIndex,
            type = planType,
            routineId = routineId,
            routineName = routineName,
            cardioTypeId = cardioTypeId,
            cardioTypeName = cardioTypeName,
            cardioTargetDurationSec = cardioTargetDurationSec,
            notificationEnabled = notificationEnabled,
            notificationTime = notificationTime,
        )
    }

    private fun WeeklyPlanDay.toEntities(): List<WeeklyPlanEntity> {
        if (isRestDay || sessions.isEmpty()) {
            return listOf(
                WeeklyPlanEntity(
                    id = "$PLAN_ID_PREFIX${dayOfWeek}_rest",
                    dayOfWeek = dayOfWeek,
                    orderIndex = 0,
                    type = "STRENGTH",
                    routineId = null,
                    cardioTypeId = null,
                    cardioTargetDurationSec = null,
                    isRestDay = true,
                    notificationEnabled = false,
                    notificationTime = null,
                ),
            )
        }
        return sessions
            .sortedBy { it.orderIndex }
            .mapIndexed { index, session ->
                WeeklyPlanEntity(
                    id = session.id.takeIf { it.isNotBlank() } ?: "$PLAN_ID_PREFIX${dayOfWeek}_$index",
                    dayOfWeek = dayOfWeek,
                    orderIndex = index,
                    type = session.type.toEntityValue(),
                    routineId = session.routineId,
                    cardioTypeId = session.cardioTypeId,
                    cardioTargetDurationSec = session.cardioTargetDurationSec,
                    isRestDay = false,
                    notificationEnabled = session.notificationEnabled,
                    notificationTime = session.notificationTime,
                )
            }
    }

    private fun Long.toIsoDayOfWeek(): Int {
        return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).dayOfWeek.value
    }

    private companion object {
        const val PLAN_ID_PREFIX = "weekly_plan_"
    }
}

private fun WeeklyPlanDayType.toEntityValue(): String = when (this) {
    WeeklyPlanDayType.Strength -> "STRENGTH"
    WeeklyPlanDayType.Cardio -> "CARDIO"
}

private fun String.toPlanType(): WeeklyPlanDayType = when (this) {
    "CARDIO" -> WeeklyPlanDayType.Cardio
    else -> WeeklyPlanDayType.Strength
}
