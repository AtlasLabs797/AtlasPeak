package com.atlaspeak.presentation.home

import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanCompletionKey
import com.atlaspeak.domain.model.planning.WeeklyPlanUpdate
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.HealthConnectRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.usecase.dashboard.DashboardFilters
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
    private val dashboardUseCase = FakeDashboardUseCase()
    private val weeklyPlanUseCase = FakeWeeklyPlanUseCase()
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
        nextSyncResult = HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            importedRecords = 1,
            exportedRecords = 0,
            failed = false,
            completedCapabilities = setOf(com.atlaspeak.domain.model.healthconnect.HealthConnectCapability.Steps),
            skippedCapabilities = setOf(com.atlaspeak.domain.model.healthconnect.HealthConnectCapability.HeartRate),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.healthConnectSync is HomeHealthConnectSync.PartialSuccess)
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
        )
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.healthConnectSync as? HomeHealthConnectSync.Failed)
    }

    private class FakeDashboardUseCase : DashboardUseCase {
        override suspend fun snapshot(filters: DashboardFilters): DashboardSnapshot = DashboardSnapshot()
    }

    private class FakeWeeklyPlanUseCase : WeeklyPlanUseCase {
        override suspend fun plan(): List<WeeklyPlanDay> = emptyList()
        override suspend fun completedTrainingDays(start: java.time.LocalDate, end: java.time.LocalDate): Set<Int> = emptySet()
        override suspend fun completedTrainingKeys(start: java.time.LocalDate, end: java.time.LocalDate): Set<WeeklyPlanCompletionKey> = emptySet()
        override suspend fun updateDay(update: WeeklyPlanUpdate): Boolean = true
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
        override suspend fun updateProfile(profile: UserProfile) = Unit
    }
}