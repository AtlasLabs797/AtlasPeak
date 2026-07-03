package com.atlaspeak.presentation.navigation

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
        val viewModel = LaunchViewModel(repository)
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
        val viewModel = LaunchViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Onboarding, viewModel.state.value)
    }

    @Test
    fun `launch gate does not stay loading when completion flow never emits`() = runTest {
        val repository = FakeOnboardingRepository(completed = false).apply {
            onboardingCompletedFlow = emptyFlow()
        }
        val viewModel = LaunchViewModel(repository)

        dispatcher.scheduler.advanceTimeBy(3_001)

        assertEquals(LaunchState.Onboarding, viewModel.state.value)
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
