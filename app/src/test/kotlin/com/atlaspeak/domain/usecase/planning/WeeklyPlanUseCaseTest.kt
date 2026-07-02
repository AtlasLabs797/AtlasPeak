package com.atlaspeak.domain.usecase.planning

import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanCompletionKey
import com.atlaspeak.domain.model.planning.WeeklyPlanSession
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.model.planning.WeeklyPlanUpdate
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeeklyPlanUseCaseTest {
    private val repository = FakeWeeklyPlanRepository()
    private val scheduler = FakeNotificationScheduler()
    private val useCase = WeeklyPlanUseCase(repository, scheduler)

    @Test
    fun `plans returns seven iso weekdays sorted`() = runTest {
        repository.saved = listOf(
            WeeklyPlanDay(
                dayOfWeek = 3,
                isRestDay = false,
                sessions = listOf(
                    WeeklyPlanSession(
                        id = "weekly_plan_3_0",
                        dayOfWeek = 3,
                        orderIndex = 0,
                        routineId = "pull",
                        routineName = "Pull",
                    ),
                ),
            ),
        )

        val plan = useCase.plan()

        assertEquals((1..7).toList(), plan.map { it.dayOfWeek })
        assertEquals("pull", plan.single { it.dayOfWeek == 3 }.routineId)
        assertNull(plan.single { it.dayOfWeek == 1 }.routineId)
    }

    @Test
    fun `update day rejects invalid day and invalid notification time`() = runTest {
        assertFalse(
            useCase.updateDay(
                WeeklyPlanUpdate(dayOfWeek = 0, routineId = "upper", isRestDay = false, notificationEnabled = true, notificationTime = "18:00"),
            ),
        )
        assertFalse(
            useCase.updateDay(
                WeeklyPlanUpdate(dayOfWeek = 2, routineId = "upper", isRestDay = false, notificationEnabled = true, notificationTime = "24:99"),
            ),
        )

        assertTrue(repository.saved.isEmpty())
        assertEquals(0, scheduler.rescheduleAllCount)
    }

    @Test
    fun `rest day clears routine and reminder before rescheduling`() = runTest {
        assertTrue(
            useCase.updateDay(
                WeeklyPlanUpdate(dayOfWeek = 5, routineId = "legs", isRestDay = true, notificationEnabled = true, notificationTime = "18:30"),
            ),
        )

        val saved = repository.saved.single()
        assertEquals(5, saved.dayOfWeek)
        assertNull(saved.routineId)
        assertTrue(saved.isRestDay)
        assertFalse(saved.notificationEnabled)
        assertNull(saved.notificationTime)
        assertEquals(1, scheduler.rescheduleAllCount)
    }

    @Test
    fun `training day stores routine and defaults reminder time`() = runTest {
        assertTrue(
            useCase.updateDay(
                WeeklyPlanUpdate(dayOfWeek = 1, routineId = "upper", isRestDay = false, notificationEnabled = true, notificationTime = null),
            ),
        )

        val saved = repository.saved.single()
        assertEquals("upper", saved.routineId)
        assertFalse(saved.isRestDay)
        assertTrue(saved.notificationEnabled)
        assertEquals("18:00", saved.notificationTime)
        assertEquals(1, scheduler.rescheduleAllCount)
    }

    @Test
    fun `cardio day rejects missing target duration`() = runTest {
        assertFalse(
            useCase.updateDay(
                WeeklyPlanUpdate(
                    dayOfWeek = 3,
                    type = WeeklyPlanDayType.Cardio,
                    routineId = null,
                    cardioTypeId = "cardio_static_bike",
                    cardioTargetDurationSec = null,
                    isRestDay = false,
                    notificationEnabled = true,
                    notificationTime = null,
                ),
            ),
        )

        assertTrue(repository.saved.isEmpty())
        assertEquals(0, scheduler.rescheduleAllCount)
    }

    @Test
    fun `cardio day stores cardio type and target duration`() = runTest {
        assertTrue(
            useCase.updateDay(
                WeeklyPlanUpdate(
                    dayOfWeek = 3,
                    type = WeeklyPlanDayType.Cardio,
                    routineId = null,
                    cardioTypeId = "cardio_static_bike",
                    cardioTargetDurationSec = 30 * 60,
                    isRestDay = false,
                    notificationEnabled = true,
                    notificationTime = null,
                ),
            ),
        )

        val saved = repository.saved.single()
        assertEquals(WeeklyPlanDayType.Cardio, saved.type)
        assertNull(saved.routineId)
        assertEquals("cardio_static_bike", saved.cardioTypeId)
        assertEquals(30 * 60, saved.cardioTargetDurationSec)
        assertTrue(saved.notificationEnabled)
        assertEquals("18:00", saved.notificationTime)
        assertEquals(1, scheduler.rescheduleAllCount)
    }

    private class FakeWeeklyPlanRepository : WeeklyPlanRepository {
        var saved = emptyList<WeeklyPlanDay>()

        override suspend fun plan(): List<WeeklyPlanDay> = saved

        override suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int> = emptySet()

        override suspend fun completedTrainingKeys(
            startInclusive: Long,
            endExclusive: Long,
        ): Set<WeeklyPlanCompletionKey> = emptySet()

        override suspend fun upsert(day: WeeklyPlanDay) {
            replaceDay(day)
        }

        override suspend fun replaceDay(day: WeeklyPlanDay) {
            saved = saved.filterNot { it.dayOfWeek == day.dayOfWeek } + day
        }
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        var rescheduleAllCount = 0

        override suspend fun rescheduleAll() {
            rescheduleAllCount += 1
        }
    }
}
