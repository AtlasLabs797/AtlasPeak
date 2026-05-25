package com.atlaspeak.presentation.onboarding

import com.atlaspeak.domain.model.onboarding.OnboardingStep
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.usecase.planning.NotificationSettingsUseCase
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val onboardingRepository = FakeOnboardingRepository()
    private val profileRepository = FakeProfileRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `primary action advances through optional setup without password`() = runTest {
        val viewModel = newViewModel()

        viewModel.primaryAction()

        assertEquals(OnboardingStep.Google, viewModel.state.value.currentStep)
    }

    @Test
    fun `finish persists optional profile and onboarding completed flag`() = runTest {
        val viewModel = newViewModel()
        viewModel.onDisplayNameChanged("Atlas User")
        viewModel.onAgeChanged("34")
        viewModel.onHeightChanged("180")
        viewModel.goToStep(OnboardingStep.Done)

        viewModel.primaryAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(onboardingRepository.completed.value)
        assertEquals("Atlas User", profileRepository.profile?.displayName)
        assertEquals(34, profileRepository.profile?.age)
        assertEquals(180.0, profileRepository.profile?.heightCm)
    }

    private fun newViewModel() = OnboardingViewModel(
        onboardingRepository = onboardingRepository,
        profileRepository = profileRepository,
        notificationSettingsUseCase = NotificationSettingsUseCase(
            FakeNotificationSettingsRepository(),
            FakeNotificationScheduler(),
        ),
    )

    private class FakeOnboardingRepository : OnboardingRepository {
        val completed = MutableStateFlow(false)
        override val onboardingCompleted: Flow<Boolean> = completed

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            this.completed.value = completed
        }
    }

    private class FakeProfileRepository : ProfileRepository {
        var profile: UserProfile? = null

        override suspend fun getProfile(): UserProfile? = profile

        override suspend fun saveProfile(profile: UserProfile) {
            this.profile = profile
        }
    }

    private class FakeNotificationSettingsRepository : NotificationSettingsRepository {
        private var settings = NotificationSettings()

        override suspend fun settings(): NotificationSettings = settings

        override suspend fun update(settings: NotificationSettings) {
            this.settings = settings
        }
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        override suspend fun rescheduleAll() = Unit
    }
}
