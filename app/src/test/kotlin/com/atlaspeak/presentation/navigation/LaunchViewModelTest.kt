package com.atlaspeak.presentation.navigation

import com.atlaspeak.domain.usecase.security.DatabaseKeyCheckResult
import com.atlaspeak.domain.usecase.security.DatabaseKeyChecker
import com.atlaspeak.domain.repository.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchViewModelTest {
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
    fun `launch gate sends first run to onboarding and completed users to home`() = runTest {
        val repository = FakeOnboardingRepository(completed = false)
        val viewModel = LaunchViewModel(repository, FakeDatabaseKeyChecker())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Onboarding, viewModel.state.value)

        repository.completed.value = true
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Home, viewModel.state.value)
    }

    @Test
    fun `launch gate falls back to onboarding when completion flow fails`() = runTest {
        val repository = FakeOnboardingRepository(completed = false).apply {
            onboardingCompletedFlow = flow { throw IllegalStateException("boom") }
        }
        val viewModel = LaunchViewModel(repository, FakeDatabaseKeyChecker())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Onboarding, viewModel.state.value)
    }

    @Test
    fun `launch gate does not stay loading when completion flow never emits`() = runTest {
        val repository = FakeOnboardingRepository(completed = false).apply {
            onboardingCompletedFlow = emptyFlow()
        }
        val viewModel = LaunchViewModel(repository, FakeDatabaseKeyChecker())

        dispatcher.scheduler.advanceTimeBy(3_001)

        assertEquals(LaunchState.Onboarding, viewModel.state.value)
    }

    @Test
    fun `launch gate routes to recovery when the database key is unavailable`() = runTest {
        val repository = FakeOnboardingRepository(completed = true)
        val viewModel = LaunchViewModel(
            repository,
            FakeDatabaseKeyChecker(result = DatabaseKeyCheckResult.KeyUnavailable),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Recovery, viewModel.state.value)
    }

    @Test
    fun `launch gate never reads onboarding completion when the database key is unavailable`() = runTest {
        val repository = FakeOnboardingRepository(completed = false).apply {
            onboardingCompletedFlow = flow { throw AssertionError("must not be collected") }
        }
        val viewModel = LaunchViewModel(
            repository,
            FakeDatabaseKeyChecker(result = DatabaseKeyCheckResult.KeyUnavailable),
        )
        dispatcher.scheduler.advanceTimeBy(3_001)

        assertEquals(LaunchState.Recovery, viewModel.state.value)
    }

    private class FakeDatabaseKeyChecker(
        private val result: DatabaseKeyCheckResult = DatabaseKeyCheckResult.Ok,
    ) : DatabaseKeyChecker {
        override suspend fun check(): DatabaseKeyCheckResult = result
    }

    private class FakeOnboardingRepository(
        completed: Boolean,
    ) : OnboardingRepository {
        val completed = MutableStateFlow(completed)
        var onboardingCompletedFlow: Flow<Boolean> = this.completed
        override val onboardingCompleted: Flow<Boolean> get() = onboardingCompletedFlow

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            this.completed.value = completed
        }
    }
}
