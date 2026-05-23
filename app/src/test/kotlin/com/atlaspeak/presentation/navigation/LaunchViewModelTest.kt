package com.atlaspeak.presentation.navigation

import com.atlaspeak.domain.repository.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `launch gate sends first run to onboarding and completed users to login`() = runTest {
        val repository = FakeOnboardingRepository(completed = false)
        val viewModel = LaunchViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Onboarding, viewModel.state.value)

        repository.completed.value = true
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LaunchState.Login, viewModel.state.value)
    }

    private class FakeOnboardingRepository(
        completed: Boolean,
    ) : OnboardingRepository {
        val completed = MutableStateFlow(completed)
        override val onboardingCompleted: Flow<Boolean> = this.completed

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            this.completed.value = completed
        }
    }
}
