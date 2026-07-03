package com.atlaspeak.domain.usecase.dashboard

import com.atlaspeak.domain.model.dashboard.DashboardConsistency
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardInterval
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSessionSummary
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.repository.DashboardRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class DashboardUseCase(
    private val dashboardRepository: DashboardRepository,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        dashboardRepository: DashboardRepository,
    ) : this(
        dashboardRepository = dashboardRepository,
        now = { System.currentTimeMillis() },
    )

    suspend fun snapshot(filters: DashboardFilters): DashboardSnapshot {
        val nowMillis = now()
        val weeklyWindow = DashboardPeriod.Week.window(nowMillis)
        val volumeWindow = filters.totalVolumePeriod.window(nowMillis)
        val activityWindow = filters.totalActivityPeriod.window(nowMillis)
        val consistencyWindow = filters.consistencyPeriod.window(nowMillis)
        val bodyWindow = filters.bodyWeightPeriod.window(nowMillis)
        val stepsWindow = filters.dailyStepsPeriod.window(nowMillis)
        val heartRateWindow = filters.heartRatePeriod.window(nowMillis)
        val sleepWindow = filters.sleepPeriod.window(nowMillis)
        val sessionStart = listOf(
            weeklyWindow.startInclusive,
            volumeWindow.startInclusive,
            activityWindow.startInclusive,
            consistencyWindow.startInclusive,
        ).min()
        val sessionEnd = nowMillis + 1
        val strengthSessions = dashboardRepository.completedStrengthSessions(sessionStart, sessionEnd)
        val cardioSessions = dashboardRepository.completedCardioSessions(sessionStart, sessionEnd)
        val plannedDays = dashboardRepository.plannedTrainingDays()
        val activeDates = (
            strengthSessions.mapNotNull { it.activeDateIn(consistencyWindow) } +
                cardioSessions.mapNotNull { it.activeDateIn(consistencyWindow) }
            ).distinct()
        val activeDays = if (plannedDays.isEmpty()) {
            activeDates.size
        } else {
            activeDates.count { it.dayOfWeek.value in plannedDays }
        }
        val targetDays = if (plannedDays.isEmpty()) {
            filters.consistencyPeriod.dayCount(nowMillis)
        } else {
            countPlannedDays(consistencyWindow, plannedDays)
        }

        return DashboardSnapshot(
            weeklyTrainingMinutes = (
                strengthSessions.sumDurationsIn(weeklyWindow) + cardioSessions.sumDurationsIn(weeklyWindow)
                ) / SECONDS_PER_MINUTE,
            totalVolumeKg = strengthSessions
                .filter { it.isIn(volumeWindow) }
                .sumOf { it.totalVolumeKg ?: 0.0 },
            consistency = DashboardConsistency(
                activeDays = activeDays,
                targetDays = targetDays.coerceAtLeast(1),
                usesWeeklyPlan = plannedDays.isNotEmpty(),
            ),
            bodyWeightPoints = dashboardRepository.bodyWeightPoints(
                bodyWindow.startInclusive,
                bodyWindow.endExclusive,
            ),
            dailySteps = dashboardRepository.stepIntervals(
                stepsWindow.startInclusive,
                stepsWindow.endExclusive,
            ).sumByDay(stepsWindow),
            heartRate = dashboardRepository.heartRateSamples(
                heartRateWindow.startInclusive,
                heartRateWindow.endExclusive,
            ).minByDay(),
            averageSleepHours = dashboardRepository.sleepIntervals(
                sleepWindow.startInclusive,
                sleepWindow.endExclusive,
            ).averageHours(sleepWindow),
            totalActivitySeconds = strengthSessions.sumDurationsIn(activityWindow) +
                cardioSessions.sumDurationsIn(activityWindow),
            volumePoints = strengthSessions
                .filter { it.isIn(volumeWindow) }
                .sumVolumeByDay(volumeWindow),
            weeklyMinutesPoints = (
                strengthSessions.sumMinutesByDay(weeklyWindow) +
                    cardioSessions.sumMinutesByDay(weeklyWindow)
                ),
        )
    }

    private fun List<DashboardSessionSummary>.sumDurationsIn(window: PeriodWindow): Int {
        return filter { it.isIn(window) }.sumOf { it.durationSeconds }
    }

    private fun List<DashboardSessionSummary>.sumVolumeByDay(window: PeriodWindow): List<DashboardPoint> {
        val zone = ZoneId.systemDefault()
        return filter { it.isIn(window) }
            .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (date, sessions) ->
                DashboardPoint(
                    timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    value = sessions.sumOf { it.totalVolumeKg ?: 0.0 },
                )
            }
    }

    private fun List<DashboardSessionSummary>.sumMinutesByDay(window: PeriodWindow): List<DashboardPoint> {
        val zone = ZoneId.systemDefault()
        return filter { it.isIn(window) }
            .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (date, sessions) ->
                DashboardPoint(
                    timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    value = sessions.sumOf { it.durationSeconds }.toDouble() / SECONDS_PER_MINUTE.toDouble(),
                )
            }
    }

    private fun DashboardSessionSummary.isIn(window: PeriodWindow): Boolean {
        return startTime >= window.startInclusive && startTime < window.endExclusive
    }

    private fun DashboardSessionSummary.activeDateIn(window: PeriodWindow): LocalDate? {
        return startTime.takeIf { isIn(window) }?.toLocalDate()
    }

    private fun List<DashboardInterval>.sumByDay(window: PeriodWindow): List<DashboardPoint> {
        val valuesByDay = linkedMapOf<LocalDate, Double>()
        forEach { interval ->
            interval.splitByDay(window) { date, value ->
                valuesByDay[date] = (valuesByDay[date] ?: 0.0) + value
            }
        }
        return valuesByDay.toSortedMap().map { (date, value) ->
            DashboardPoint(
                timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                value = value,
            )
        }
    }

    private fun DashboardInterval.splitByDay(
        window: PeriodWindow,
        onSegment: (LocalDate, Double) -> Unit,
    ) {
        val originalDuration = endTime - startTime
        if (originalDuration <= 0L) return
        var cursor = maxOf(startTime, window.startInclusive)
        val clippedEnd = minOf(endTime, window.endExclusive)
        if (cursor >= clippedEnd) return
        while (cursor < clippedEnd) {
            val date = cursor.toLocalDate()
            val nextMidnight = date.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val segmentEnd = minOf(clippedEnd, nextMidnight)
            val segmentValue = value * (segmentEnd - cursor).toDouble() / originalDuration.toDouble()
            onSegment(date, segmentValue)
            cursor = segmentEnd
        }
    }

    private fun List<DashboardPoint>.minByDay(): List<DashboardPoint> {
        return groupBy { it.timestamp.toLocalDate() }
            .toSortedMap()
            .map { (date, points) ->
                DashboardPoint(
                    timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    value = points.minOf { it.value },
                )
            }
    }

    private fun List<DashboardInterval>.averageHours(window: PeriodWindow): Double? {
        val durations = mapNotNull { interval ->
            val start = maxOf(interval.startTime, window.startInclusive)
            val end = minOf(interval.endTime, window.endExclusive)
            (end - start).takeIf { it > 0L }
        }
        if (durations.isEmpty()) return null
        return durations.map { it / MILLIS_PER_HOUR.toDouble() }.average()
    }

    private fun countPlannedDays(window: PeriodWindow, plannedDays: Set<Int>): Int {
        var cursor = window.startDate
        var count = 0
        while (!cursor.isAfter(window.targetEndDate)) {
            if (cursor.dayOfWeek.value in plannedDays) count += 1
            cursor = cursor.plusDays(1)
        }
        return count
    }

    private fun DashboardPeriod.window(nowMillis: Long): PeriodWindow {
        val zone = ZoneId.systemDefault()
        val today = nowMillis.toLocalDate()
        val startDate = when (this) {
            DashboardPeriod.Week -> today.minusDays((today.dayOfWeek.value - 1).toLong())
            DashboardPeriod.Month -> today.minusDays(29)
            DashboardPeriod.ThreeMonths -> today.minusDays(89)
            DashboardPeriod.Year -> today.minusDays(364)
            DashboardPeriod.YearToDate -> today.withDayOfYear(1)
        }
        val targetEndDate = when (this) {
            DashboardPeriod.Week -> startDate.plusDays(6)
            DashboardPeriod.Month,
            DashboardPeriod.ThreeMonths,
            DashboardPeriod.Year,
            DashboardPeriod.YearToDate -> today
        }
        return PeriodWindow(
            startInclusive = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = nowMillis + 1,
            startDate = startDate,
            targetEndDate = targetEndDate,
        )
    }

    private fun DashboardPeriod.dayCount(nowMillis: Long): Int {
        return when (this) {
            DashboardPeriod.Week -> 7
            DashboardPeriod.Month -> 30
            DashboardPeriod.ThreeMonths -> 90
            DashboardPeriod.Year -> 365
            DashboardPeriod.YearToDate -> {
                val start = window(nowMillis).startDate
                val end = nowMillis.toLocalDate()
                (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
            }
        }
    }

    private fun Long.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private data class PeriodWindow(
        val startInclusive: Long,
        val endExclusive: Long,
        val startDate: LocalDate,
        val targetEndDate: LocalDate,
    )

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MILLIS_PER_HOUR = 60L * 60L * 1_000L
    }
}
