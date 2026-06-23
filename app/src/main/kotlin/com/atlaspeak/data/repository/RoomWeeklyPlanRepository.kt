package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.dao.WeeklyPlanRow
import com.atlaspeak.data.db.entity.WeeklyPlanEntity
import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWeeklyPlanRepository @Inject constructor(
    private val database: AppDatabase,
) : WeeklyPlanRepository {
    override suspend fun plan(): List<WeeklyPlanDay> {
        return database.weeklyPlanDao().getPlan().map { it.toDomain() }
    }

    override suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int> {
        return database.weeklyPlanDao()
            .getCompletedTrainingSessionStartTimes(startInclusive, endExclusive)
            .map { it.toIsoDayOfWeek() }
            .toSet()
    }

    override suspend fun upsert(day: WeeklyPlanDay) {
        database.weeklyPlanDao().upsert(
            WeeklyPlanEntity(
                id = PLAN_ID_PREFIX + day.dayOfWeek,
                dayOfWeek = day.dayOfWeek,
                type = day.type.toEntityValue(),
                routineId = day.routineId,
                cardioTypeId = day.cardioTypeId,
                cardioTargetDurationSec = day.cardioTargetDurationSec,
                isRestDay = day.isRestDay,
                notificationEnabled = day.notificationEnabled,
                notificationTime = day.notificationTime,
            ),
        )
    }

    private fun WeeklyPlanRow.toDomain() = WeeklyPlanDay(
        dayOfWeek = dayOfWeek,
        type = type.toPlanType(),
        routineId = routineId,
        routineName = routineName,
        cardioTypeId = cardioTypeId,
        cardioTypeName = cardioTypeName,
        cardioTargetDurationSec = cardioTargetDurationSec,
        isRestDay = isRestDay,
        notificationEnabled = notificationEnabled,
        notificationTime = notificationTime,
    )

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
