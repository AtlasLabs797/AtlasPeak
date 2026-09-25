package com.atlaspeak.presentation.home

import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardInterval
import com.atlaspeak.domain.model.dashboard.DashboardPoint
import com.atlaspeak.domain.model.dashboard.DashboardSessionSummary
import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanCompletionKey
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.DashboardRepository
import com.atlaspeak.domain.repository.HealthConnectRepository
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import com.atlaspeak.domain.usecase.dashboard.DashboardUseCase
import com.atlaspeak.domain.usecase.healthconnect.SyncHealthConnectUseCase
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private var nextSyncResult: HealthConnectSyncResult =
        HealthConnectSyncResult(availability = HealthConnectAvailability.Available)
    private val dashboardUseCase = DashboardUseCase(
        dashboardRepository = FakeDashboardRepository(),
        now = { 1_700_000_000_000L },
    )
    private val weeklyPlanUseCase = WeeklyPlanUseCase(
        repository = FakeWeeklyPlanRepository(),
        notificationScheduler = FakeNotificationScheduler(),
        now = { 1_700_000_000_000L },
    )
    private val syncHealthConnectUseCase = SyncHealthConnectUseCase(FakeHealthConnectRepository { nextSyncResult })
    private val profileRepository = FakeProfileRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        nextSyncResult = HealthConnectSyncResult(availability = HealthConnectAvailability.Available)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = HomeViewModel(
        dashboardUseCase = dashboardUseCase,
        weeklyPlanUseCase = weeklyPlanUseCase,
        syncHealthConnectUseCase = syncHealthConnectUseCase,
        profileRepository = profileRepository,
        workoutRepository = FakeWorkoutRepository(),
        cardioRepository = FakeCardioRepository(),
    )

    @Test
    fun `sync success maps to Success`() = runTest(dispatcher) {
        nextSyncResult = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            importedRecords = 3,
            exportedRecords = 1,
        )
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertTrue(
            viewModel.state.value.healthConnectSync is HomeHealthConnectSync.Success,
            "status was ${viewModel.state.value.healthConnectSync}",
        )
    }

    @Test
    fun `missing permissions maps to MissingPermissions`() = runTest(dispatcher) {
        nextSyncResult = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            missingPermissions = true,
            importedRecords = 0,
            exportedRecords = 0,
        )
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(HomeHealthConnectSync.MissingPermissions, viewModel.state.value.healthConnectSync)
    }

    @Test
    fun `update required maps to UpdateRequired`() = runTest(dispatcher) {
        nextSyncResult = HealthConnectSyncResult(availability = HealthConnectAvailability.UpdateRequired)
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(HomeHealthConnectSync.UpdateRequired, viewModel.state.value.healthConnectSync)
    }

    @Test
    fun `unavailable maps to Unavailable`() = runTest(dispatcher) {
        nextSyncResult = HealthConnectSyncResult(availability = HealthConnectAvailability.Unavailable)
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(HomeHealthConnectSync.Unavailable, viewModel.state.value.healthConnectSync)
    }

    @Test
    fun `partial success maps to PartialSuccess`() = runTest(dispatcher) {
        // BUG-107: HealthConnectManager.sync() en produccion siempre marca
        // `missingPermissions = skippedCapabilities.isNotEmpty()`, asi que un
        // resultado parcial real SIEMPRE trae `missingPermissions = true`.
        // El test original dejaba `missingPermissions` en su default (false),
        // lo que ocultaba el bug donde `toHomeSyncStatus` evaluaba
        // `missingPermissions` antes que `partiallySuccessful`.
        nextSyncResult = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            missingPermissions = true,
            importedRecords = 1,
            exportedRecords = 0,
            failed = false,
            completedCapabilities = setOf(com.atlaspeak.domain.model.healthconnect.HealthConnectCapability.Steps),
            skippedCapabilities = setOf(com.atlaspeak.domain.model.healthconnect.HealthConnectCapability.HeartRate),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertTrue(
            viewModel.state.value.healthConnectSync is HomeHealthConnectSync.PartialSuccess,
            "status was ${viewModel.state.value.healthConnectSync}",
        )
    }

    @Test
    fun `resume refresh right after init does not cancel the initial sync`() = runTest(dispatcher) {
        // BUG-106: HomeRoute dispara refresh(syncBefore = true) en init y, casi
        // al mismo tiempo, ON_RESUME llama refresh() (sin sync). Antes ambos
        // compartian el mismo Job, asi que el segundo cancelaba el sync
        // inicial y el banner se quedaba clavado en "Syncing". Aqui se
        // simula esa secuencia sin avanzar el dispatcher entre medio.
        nextSyncResult = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            importedRecords = 3,
            exportedRecords = 1,
        )
        val viewModel = newViewModel()
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(
            viewModel.state.value.healthConnectSync is HomeHealthConnectSync.Success,
            "status was ${viewModel.state.value.healthConnectSync}",
        )
    }

    @Test
    fun `dismiss sets status back to Idle`() = runTest(dispatcher) {
        nextSyncResult = HealthConnectSyncResult(availability = HealthConnectAvailability.Unavailable)
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.dismissHealthConnectSyncStatus()
        assertEquals(HomeHealthConnectSync.Idle, viewModel.state.value.healthConnectSync)
    }

    @Test
    fun `sync exception maps to Failed`() = runTest(dispatcher) {
        val failingUseCase = SyncHealthConnectUseCase(FailingHealthConnectRepository())
        val viewModel = HomeViewModel(
            dashboardUseCase = dashboardUseCase,
            weeklyPlanUseCase = weeklyPlanUseCase,
            syncHealthConnectUseCase = failingUseCase,
            profileRepository = profileRepository,
            workoutRepository = FakeWorkoutRepository(),
            cardioRepository = FakeCardioRepository(),
        )
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.healthConnectSync as? HomeHealthConnectSync.Failed)
    }

    private class FakeDashboardRepository : DashboardRepository {
        override suspend fun plannedTrainingDays(): Set<Int> = emptySet()
        override suspend fun completedStrengthSessions(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardSessionSummary> = emptyList()
        override suspend fun completedCardioSessions(
            startInclusive: Long,
            endExclusive: Long,
        ): List<DashboardSessionSummary> = emptyList()
        override suspend fun bodyWeightPoints(startInclusive: Long, endExclusive: Long): List<DashboardPoint> = emptyList()
        override suspend fun stepIntervals(startInclusive: Long, endExclusive: Long): List<DashboardInterval> = emptyList()
        override suspend fun heartRateSamples(startInclusive: Long, endExclusive: Long): List<DashboardPoint> = emptyList()
        override suspend fun sleepIntervals(startInclusive: Long, endExclusive: Long): List<DashboardInterval> = emptyList()
    }

    private class FakeWeeklyPlanRepository : WeeklyPlanRepository {
        override suspend fun plan(): List<WeeklyPlanDay> = emptyList()
        override suspend fun completedTrainingDays(startInclusive: Long, endExclusive: Long): Set<Int> = emptySet()
        override suspend fun completedTrainingKeys(
            startInclusive: Long,
            endExclusive: Long,
        ): Set<WeeklyPlanCompletionKey> = emptySet()
        override suspend fun upsert(day: WeeklyPlanDay) = Unit
        override suspend fun replaceDay(day: WeeklyPlanDay) = Unit
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        override suspend fun rescheduleAll() = Unit
    }

    private class FakeHealthConnectRepository(
        private val nextResult: () -> HealthConnectSyncResult,
    ) : HealthConnectRepository {
        override fun requiredPermissions(): Set<String> = emptySet()
        override suspend fun availability(): HealthConnectAvailability = HealthConnectAvailability.Available
        override suspend fun sync(): HealthConnectSyncResult = nextResult()
    }

    private class FailingHealthConnectRepository : HealthConnectRepository {
        override fun requiredPermissions(): Set<String> = emptySet()
        override suspend fun availability(): HealthConnectAvailability = HealthConnectAvailability.Available
        override suspend fun sync(): HealthConnectSyncResult =
            throw RuntimeException("simulated sync failure")
    }

    private class FakeProfileRepository : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = null
        override suspend fun saveProfile(profile: UserProfile) = Unit
    }

    private class FakeWorkoutRepository : com.atlaspeak.domain.repository.WorkoutRepository {
        override suspend fun createSession(session: com.atlaspeak.domain.model.workout.WorkoutSession) = session
        override suspend fun session(id: String): com.atlaspeak.domain.model.workout.WorkoutSession? = null
        override suspend fun sessions(): List<com.atlaspeak.domain.model.workout.WorkoutSession> = emptyList()
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun upsertSet(set: com.atlaspeak.domain.model.workout.WorkoutSet) = Unit
        override suspend fun deleteSet(id: String) = Unit
        override suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double? = null
        override suspend fun updateSessionCompletion(sessionId: String, endTime: Long, durationSeconds: Int, totalVolumeKg: Double) = Unit
        override suspend fun findActiveSession(): com.atlaspeak.domain.model.workout.WorkoutSession? = null
    }

    private class FakeCardioRepository : com.atlaspeak.domain.repository.CardioRepository {
        override suspend fun cardioTypes(includeArchived: Boolean) = emptyList<com.atlaspeak.domain.model.cardio.CardioType>()
        override suspend fun upsertCustomType(type: com.atlaspeak.domain.model.cardio.CardioType) = Unit
        override suspend fun archiveType(id: String) = Unit
        override suspend fun createSession(session: com.atlaspeak.domain.model.cardio.CardioSession) = session
        override suspend fun session(id: String) = null as com.atlaspeak.domain.model.cardio.CardioSession?
        override suspend fun sessions() = emptyList<com.atlaspeak.domain.model.cardio.CardioSession>()
        override suspend fun updateSession(session: com.atlaspeak.domain.model.cardio.CardioSession) = Unit
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun findActiveSession() = null as com.atlaspeak.domain.model.cardio.CardioSession?
        override suspend fun addRoutePoint(point: com.atlaspeak.domain.model.cardio.CardioRoutePoint) = Unit
        override suspend fun routePoints(sessionId: String) = emptyList<com.atlaspeak.domain.model.cardio.CardioRoutePoint>()
        override suspend fun routePointsCount(sessionId: String) = 0
        override suspend fun routeDistanceKm(sessionId: String) = 0.0
        override suspend fun deleteRoutePoints(sessionId: String) = Unit
        override suspend fun finalizeCardioSessionRoute(session: com.atlaspeak.domain.model.cardio.CardioSession) = Unit
    }
}