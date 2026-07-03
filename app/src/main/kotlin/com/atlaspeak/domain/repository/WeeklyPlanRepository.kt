package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanCompletionKey

interface WeeklyPlanRepository {
    suspend fun plan(): List<WeeklyPlanDay>
    suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int>
    suspend fun completedTrainingKeys(startInclusive: Long, endExclusive: Long): Set<WeeklyPlanCompletionKey>
    suspend fun upsert(day: WeeklyPlanDay)
    suspend fun replaceDay(day: WeeklyPlanDay)
}
