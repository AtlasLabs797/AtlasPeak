package com.atlaspeak.domain.usecase.dashboard

import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardInterval
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSessionSummary
import com.atlaspeak.domain.repository.DashboardRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardUseCaseTest {
    private val dashboardRepository = FakeDashboardRepository()
    private val now = localMillis(2023, 11, 14, 23, 13)
    private val useCase = DashboardUseCase(
        dashboardRepository = dashboardRepository,
        now = { now },
    )

    @Test
    fun `snapshot aggregates volume activity weekly minutes and fallback consistency`() = runTest {
        dashboardRepository.strengthSessions = listOf(
            session(startTime = now - DAYS_1, durationSeconds = 3_600, volumeKg = 1_200.0),
            session(startTime = now - DAYS_40, durationSeconds = 3_600, volumeKg = 900.0),
        )
        dashboardRepository.cardioSessions = listOf(
            session(startTime = now - HOUR, durationSeconds = 1_800),
        )

        val snapshot = useCase.snapshot(DashboardFilters())

        assertEquals(1_200.0, snapshot.totalVolumeKg)
        assertEquals(2, snapshot.consistency.activeDays)
        assertEquals(7, snapshot.consistency.targetDays)
        assertEquals(90, snapshot.weeklyTrainingMinutes)
        assertEquals(5_400, snapshot.totalActivitySeconds)
    }

    @Test
    fun `snapshot uses weekly plan days when a plan is configured`() = runTest {
        dashboardRepository.strengthSessions = listOf(session(startTime = now - DAYS_1))
        dashboardRepository.plannedTrainingDays = setOf(1, 3, 5)

        val snapshot = useCase.snapshot(DashboardFilters(consistencyPeriod = DashboardPeriod.Week))

        assertEquals(1, snapshot.consistency.activeDays)
        assertEquals(3, snapshot.consistency.targetDays)
        assertEquals(true, snapshot.consistency.usesWeeklyPlan)
    }

    @Test
    fun `snapshot ignores active days outside weekly plan`() = runTest {
        dashboardRepository.strengthSessions = listOf(session(startTime = now - HOUR))
        dashboardRepository.plannedTrainingDays = setOf(1, 3, 5)

        val snapshot = useCase.snapshot(DashboardFilters(consistencyPeriod = DashboardPeriod.Week))

        assertEquals(0, snapshot.consistency.activeDays)
        assertEquals(3, snapshot.consistency.targetDays)
    }

    @Test
    fun `snapshot does not count eight calendar days for weekly plan target`() = runTest {
        dashboardRepository.plannedTrainingDays = setOf(2)

        val snapshot = useCase.snapshot(DashboardFilters(consistencyPeriod = DashboardPeriod.Week))

        assertEquals(1, snapshot.consistency.targetDays)
    }

    @Test
    fun `snapshot groups health and body metrics into chart points`() = runTest {
        dashboardRepository.bodyWeights = listOf(
            DashboardPoint(now - DAYS_2, 80.0),
            DashboardPoint(now - DAYS_1, 79.5),
        )
        dashboardRepository.stepIntervals = listOf(
            DashboardInterval(now - DAYS_1 - 3 * HOUR, now - DAYS_1 - 2 * HOUR, 2_000.0),
            DashboardInterval(now - DAYS_1 - 2 * HOUR, now - DAYS_1 - HOUR, 3_000.0),
            DashboardInterval(now - 3 * HOUR, now - 2 * HOUR, 4_000.0),
        )
        dashboardRepository.heartRates = listOf(
            DashboardPoint(now - 3 * HOUR, 58.0),
            DashboardPoint(now - 2 * HOUR, 62.0),
        )
        dashboardRepository.sleepIntervals = listOf(
            DashboardInterval(now - DAYS_1 - 10 * HOUR, now - DAYS_1 - 3 * HOUR, 0.0),
            DashboardInterval(now - 10 * HOUR, now - 2 * HOUR, 0.0),
        )

        val snapshot = useCase.snapshot(DashboardFilters())

        assertEquals(listOf(80.0, 79.5), snapshot.bodyWeightPoints.map { it.value })
        assertEquals(5_000.0, snapshot.dailySteps[0].value)
        assertEquals(4_000.0, snapshot.dailySteps[1].value)
        assertEquals(listOf(58.0), snapshot.heartRate.map { it.value })
        assertEquals(7.5, snapshot.averageSleepHours)
    }

    @Test
    fun `snapshot splits step intervals that cross midnight`() = runTest {
        dashboardRepository.stepIntervals = listOf(
            DashboardInterval(
                startTime = localMillis(2023, 11, 13, 23, 0),
                endTime = localMillis(2023, 11, 14, 1, 0),
                value = 120.0,
            ),
        )

        val snapshot = useCase.snapshot(DashboardFilters(dailyStepsPeriod = DashboardPeriod.Week))

        assertEquals(2, snapshot.dailySteps.size)
        assertEquals(60.0, snapshot.dailySteps[0].value, 0.001)
        assertEquals(60.0, snapshot.dailySteps[1].value, 0.001)
    }

    private fun session(
        startTime: Long,
        durationSeconds: Int = 1_800,
        volumeKg: Double? = 500.0,
    ) = DashboardSessionSummary(
        startTime = startTime,
        durationSeconds = durationSeconds,
        totalVolumeKg = volumeKg,
    )

    private class FakeDashboardRepository : DashboardRepository {
        var plannedTrainingDays = emptySet<Int>()
        var strengthSessions = emptyList<DashboardSessionSummary>()
        var cardioSessions = emptyList<DashboardSessionSummary>()
        var bodyWeights = emptyList<DashboardPoint>()
        var stepIntervals = emptyList<DashboardInterval>()
        var heartRates = emptyList<DashboardPoint>()
        var sleepIntervals = emptyList<DashboardInterval>()

        override suspend fun plannedTrainingDays(): Set<Int> = plannedTrainingDays

        override suspend fun completedStrengthSessions(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardSessionSummary> = strengthSessions.filter { it.startTime in startInclusive until endExclusive }

        override suspend fun completedCardioSessions(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardSessionSummary> = cardioSessions.filter { it.startTime in startInclusive until endExclusive }

        override suspend fun bodyWeightPoints(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardPoint> = bodyWeights.filter { it.timestamp in startInclusive until endExclusive }

        override suspend fun stepIntervals(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardInterval> = stepIntervals.filter {
            it.startTime < endExclusive && it.endTime > startInclusive
        }

        override suspend fun heartRateSamples(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardPoint> = heartRates.filter { it.timestamp in startInclusive until endExclusive }

        override suspend fun sleepIntervals(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardInterval> = sleepIntervals.filter {
            it.startTime < endExclusive && it.endTime > startInclusive
        }
    }

    private companion object {
        const val HOUR = 60L * 60L * 1_000L
        const val DAYS_1 = 24L * HOUR
        const val DAYS_2 = 2L * DAYS_1
        const val DAYS_40 = 40L * DAYS_1

        fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
            return LocalDate.of(year, month, day)
                .atTime(LocalTime.of(hour, minute))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
    }
}
