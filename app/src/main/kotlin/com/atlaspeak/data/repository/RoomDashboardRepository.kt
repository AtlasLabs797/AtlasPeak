package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.dao.DashboardIntervalRow
import com.atlaspeak.data.db.dao.DashboardPointRow
import com.atlaspeak.data.db.dao.DashboardSessionRow
import com.atlaspeak.domain.model.dashboard.DashboardInterval
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSessionSummary
import com.atlaspeak.domain.repository.DashboardRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDashboardRepository @Inject constructor(
    private val database: AppDatabase,
) : DashboardRepository {
    override suspend fun plannedTrainingDays(): Set<Int> {
        return database.dashboardDao().getPlannedTrainingDays().toSet()
    }

    override suspend fun completedStrengthSessions(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardSessionSummary> {
        return database.dashboardDao()
            .getCompletedStrengthSessions(startInclusive, endExclusive)
            .map { it.toDomain() }
    }

    override suspend fun completedCardioSessions(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DashboardSessionSummary> {
        return database.dashboardDao()
            .getCompletedCardioSessions(startInclusive, endExclusive)
            .map { it.toDomain() }
    }

    override suspend fun bodyWeightPoints(startInclusive: Long, endExclusive: Long): List<DashboardPoint> {
        return database.dashboardDao().getBodyWeightPoints(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun stepIntervals(startInclusive: Long, endExclusive: Long): List<DashboardInterval> {
        return database.dashboardDao().getStepIntervals(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun heartRateSamples(startInclusive: Long, endExclusive: Long): List<DashboardPoint> {
        return database.dashboardDao().getHeartRateSamples(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun sleepIntervals(startInclusive: Long, endExclusive: Long): List<DashboardInterval> {
        return database.dashboardDao().getSleepIntervals(startInclusive, endExclusive).map { it.toDomain() }
    }

    private fun DashboardPointRow.toDomain() = DashboardPoint(
        timestamp = timestamp,
        value = value,
    )

    private fun DashboardIntervalRow.toDomain() = DashboardInterval(
        startTime = startTime,
        endTime = endTime,
        value = value,
    )

    private fun DashboardSessionRow.toDomain() = DashboardSessionSummary(
        startTime = startTime,
        durationSeconds = durationSeconds ?: 0,
        totalVolumeKg = totalVolumeKg,
    )
}
