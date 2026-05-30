package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.dashboard.DashboardInterval
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSessionSummary

interface DashboardRepository {
    suspend fun plannedTrainingDays(): Set<Int>
    suspend fun completedStrengthSessions(startInclusive: Long, endExclusive: Long): List<DashboardSessionSummary>
    suspend fun completedCardioSessions(startInclusive: Long, endExclusive: Long): List<DashboardSessionSummary>
    suspend fun bodyWeightPoints(startInclusive: Long, endExclusive: Long): List<DashboardPoint>
    suspend fun stepIntervals(startInclusive: Long, endExclusive: Long): List<DashboardInterval>
    suspend fun heartRateSamples(startInclusive: Long, endExclusive: Long): List<DashboardPoint>
    suspend fun sleepIntervals(startInclusive: Long, endExclusive: Long): List<DashboardInterval>
}
