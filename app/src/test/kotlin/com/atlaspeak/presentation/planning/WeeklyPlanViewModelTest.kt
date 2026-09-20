package com.atlaspeak.presentation.planning

import com.atlaspeak.R
import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.planning.WeeklyPlanCompletionKey
import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.model.planning.WeeklyPlanSession
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
import com.atlaspeak.domain.usecase.workout.RoutineUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyPlanViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rest day with sessions requires second confirmation before clearing sessions`() = runTest {
        val weeklyPlanRepository = FakeWeeklyPlanRepository(
            days = listOf(
                WeeklyPlanDay(
                    dayOfWeek = 1,
                    isRestDay = false,
                    sessions = listOf(
                        WeeklyPlanSession(
                            id = "monday-strength",
                            dayOfWeek = 1,
                            orderIndex = 0,
                            type = WeeklyPlanDayType.Strength,
                            routineId = "routine-1",
                            routineName = "Strength",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = viewModel(weeklyPlanRepository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setRestDay(dayOfWeek = 1, restDay = true)

        val firstTapDay = viewModel.state.value.days.first { it.dayOfWeek == 1 }
        assertFalse(firstTapDay.isRestDay)
        assertEquals(1, firstTapDay.sessions.size)
        assertEquals(R.string.weekly_plan_rest_day_confirm_again, viewModel.state.value.messageRes)

        viewModel.setRestDay(dayOfWeek = 1, restDay = true)

        val confirmedDay = viewModel.state.value.days.first { it.dayOfWeek == 1 }
        assertTrue(confirmedDay.isRestDay)
        assertEquals(emptyList<WeeklyPlanSessionDraft>(), confirmedDay.sessions)
    }

    private fun viewModel(weeklyPlanRepository: WeeklyPlanRepository): WeeklyPlanViewModel {
        return WeeklyPlanViewModel(
            weeklyPlanUseCase = WeeklyPlanUseCase(
                repository = weeklyPlanRepository,
                notificationScheduler = FakeNotificationScheduler(),
                now = { 0L },
            ),
            routineUseCase = RoutineUseCase(FakeRoutineRepository()),
            cardioUseCase = CardioUseCase(
                repository = FakeCardioRepository(),
                bodyCompositionRepository = FakeBodyCompositionRepository(),
                now = { 0L },
            ),
        )
    }

    private class FakeWeeklyPlanRepository(
        private val days: List<WeeklyPlanDay>,
    ) : WeeklyPlanRepository {
        override suspend fun plan(): List<WeeklyPlanDay> = days
        override suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int> = emptySet()
        override suspend fun completedTrainingKeys(
            startInclusive: Long,
            endExclusive: Long,
        ): Set<WeeklyPlanCompletionKey> = emptySet()
        override suspend fun upsert(day: WeeklyPlanDay) = Unit
        override suspend fun replaceDay(day: WeeklyPlanDay) = Unit
    }

    private class FakeRoutineRepository : RoutineRepository {
        override suspend fun routines(includeArchived: Boolean): List<Routine> = emptyList()
        override suspend fun routine(id: String): Routine? = null
        override suspend fun upsertRoutine(routine: Routine) = Unit
        override suspend fun archiveRoutine(id: String) = Unit
    }

    private class FakeCardioRepository : CardioRepository {
        override suspend fun cardioTypes(includeArchived: Boolean): List<CardioType> = emptyList()
        override suspend fun upsertCustomType(type: CardioType) = Unit
        override suspend fun archiveType(id: String) = Unit
        override suspend fun createSession(session: CardioSession): CardioSession = session
        override suspend fun session(id: String): CardioSession? = null
        override suspend fun sessions(): List<CardioSession> = emptyList()
        override suspend fun findActiveSession(): CardioSession? = null
        override suspend fun updateSession(session: CardioSession) = Unit
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun addRoutePoint(point: com.atlaspeak.domain.model.cardio.CardioRoutePoint) = Unit
        override suspend fun routePoints(sessionId: String): List<com.atlaspeak.domain.model.cardio.CardioRoutePoint> = emptyList()
        override suspend fun routePointsCount(sessionId: String): Int = 0
        override suspend fun routeDistanceKm(sessionId: String): Double = 0.0
        override suspend fun deleteRoutePoints(sessionId: String) = Unit
        override suspend fun finalizeCardioSessionRoute(session: CardioSession) = Unit
    }

    private class FakeBodyCompositionRepository : BodyCompositionRepository {
        override suspend fun entries(): List<BodyCompositionEntry> = emptyList()
        override suspend fun upsert(entry: BodyCompositionEntry) = Unit
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        override suspend fun rescheduleAll() = Unit
    }
}
