package com.atlaspeak.presentation.onboarding

import com.atlaspeak.domain.model.auth.AuthSecurityState
import com.atlaspeak.domain.model.auth.LocalAuthResult
import com.atlaspeak.domain.model.auth.LocalUser
import com.atlaspeak.domain.model.onboarding.OnboardingStep
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.security.PasswordHash
import com.atlaspeak.domain.security.PasswordHasher
import com.atlaspeak.domain.usecase.auth.LocalAuthUseCase
import com.atlaspeak.domain.usecase.onboarding.PasswordStrengthEvaluator
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val onboardingRepository = FakeOnboardingRepository()
    private val profileRepository = FakeProfileRepository()
    private val authRepository = FakeAuthRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `password step blocks weak or mismatched passwords`() = runTest {
        val viewModel = newViewModel()
        viewModel.goToStep(OnboardingStep.Password)

        viewModel.onPasswordChanged("short")
        viewModel.onConfirmPasswordChanged("short")
        viewModel.primaryAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OnboardingStep.Password, viewModel.state.value.currentStep)
        assertFalse(authRepository.passwordConfigured)

        viewModel.onPasswordChanged("LongEnough42!")
        viewModel.onConfirmPasswordChanged("different")
        viewModel.primaryAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OnboardingStep.Password, viewModel.state.value.currentStep)
        assertFalse(authRepository.passwordConfigured)
    }

    @Test
    fun `password step stores password and advances`() = runTest {
        val viewModel = newViewModel()
        viewModel.goToStep(OnboardingStep.Password)

        viewModel.onPasswordChanged("LongEnough42!")
        viewModel.onConfirmPasswordChanged("LongEnough42!")
        viewModel.primaryAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(authRepository.passwordConfigured)
        assertEquals(OnboardingStep.Profile, viewModel.state.value.currentStep)
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

    @Test
    fun `finish persists biometric opt in after password step`() = runTest {
        val viewModel = newViewModel()
        viewModel.onBiometricsEnabledChanged(true)
        viewModel.goToStep(OnboardingStep.Done)

        viewModel.primaryAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(authRepository.biometricsEnabled)
    }

    private fun newViewModel() = OnboardingViewModel(
        localAuthUseCase = LocalAuthUseCase(authRepository, FakePasswordHasher()),
        onboardingRepository = onboardingRepository,
        profileRepository = profileRepository,
        passwordStrengthEvaluator = PasswordStrengthEvaluator(),
        notificationSettingsUseCase = NotificationSettingsUseCase(
            FakeNotificationSettingsRepository(),
            FakeNotificationScheduler(),
        ),
    )

    private class FakePasswordHasher : PasswordHasher {
        override suspend fun hashPassword(password: CharArray): PasswordHash {
            return PasswordHash(hashBase64 = "hash", saltBase64 = "salt")
        }

        override suspend fun verifyPassword(password: CharArray, storedHash: PasswordHash): Boolean = true
    }

    private class FakeAuthRepository : AuthRepository {
        var passwordConfigured = false
        var biometricsEnabled = false

        override suspend fun getLocalUser(): LocalUser? {
            return if (passwordConfigured) {
                LocalUser("user", null, null, "hash", "salt", 1L, null)
            } else {
                null
            }
        }

        override suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long) {
            passwordConfigured = true
        }

        override suspend fun markLogin(nowMillis: Long) = Unit

        override suspend fun isBiometricUnlockEnabled(): Boolean = biometricsEnabled

        override suspend fun getUnlockTimeoutMinutes(): Int = 5

        override suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
            biometricsEnabled = enabled
        }

        override suspend fun getAuthSecurityState(): AuthSecurityState = AuthSecurityState()

        override suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState = AuthSecurityState()

        override suspend fun resetAuthSecurity() = Unit
    }

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
