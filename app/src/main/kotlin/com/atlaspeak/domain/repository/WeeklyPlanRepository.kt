package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.planning.WeeklyPlanDay

interface WeeklyPlanRepository {
    suspend fun plan(): List<WeeklyPlanDay>
    suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int>
    suspend fun upsert(day: WeeklyPlanDay)
}
