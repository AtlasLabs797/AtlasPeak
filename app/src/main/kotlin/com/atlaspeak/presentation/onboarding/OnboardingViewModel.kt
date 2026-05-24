package com.atlaspeak.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.auth.LocalAuthResult
import com.atlaspeak.domain.model.onboarding.OnboardingStep
import com.atlaspeak.domain.model.onboarding.PasswordStrength
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.usecase.auth.LocalAuthUseCase
import com.atlaspeak.domain.usecase.planning.NotificationSettingsUseCase
import com.atlaspeak.domain.usecase.onboarding.PasswordStrengthEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val localAuthUseCase: LocalAuthUseCase,
    private val onboardingRepository: OnboardingRepository,
    private val profileRepository: ProfileRepository,
    private val passwordStrengthEvaluator: PasswordStrengthEvaluator,
    private val notificationSettingsUseCase: NotificationSettingsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun goToStep(step: OnboardingStep) {
        mutableState.update { it.copy(currentStep = step, message = null) }
    }

    fun primaryAction() {
        val snapshot = mutableState.value
        if (snapshot.isSubmitting) return
        when (snapshot.currentStep) {
            OnboardingStep.Password -> submitPassword(snapshot)
            OnboardingStep.Done -> finish(snapshot)
            else -> nextStep()
        }
    }

    fun skipOptionalStep() {
        val snapshot = mutableState.value
        if (snapshot.currentStep == OnboardingStep.Password) return
        nextStep()
    }

    fun onPasswordChanged(password: String) {
        mutableState.update {
            it.copy(
                password = password,
                passwordStrength = passwordStrengthEvaluator.evaluate(password),
                message = null,
            )
        }
    }

    fun onConfirmPasswordChanged(password: String) {
        mutableState.update { it.copy(confirmPassword = password, message = null) }
    }

    fun onDisplayNameChanged(value: String) {
        mutableState.update { it.copy(displayName = value) }
    }

    fun onAgeChanged(value: String) {
        mutableState.update { it.copy(age = value.filter(Char::isDigit)) }
    }

    fun onHeightChanged(value: String) {
        mutableState.update { it.copy(heightCm = value.filter { it.isDigit() || it == '.' || it == ',' }) }
    }

    fun onGenderChanged(value: String) {
        mutableState.update { it.copy(gender = value) }
    }

    fun onGoalChanged(value: String) {
        mutableState.update { it.copy(goalType = value) }
    }

    fun onBiometricsEnabledChanged(enabled: Boolean) {
        mutableState.update { it.copy(biometricsEnabled = enabled) }
    }

    fun markPermissionHandled() {
        nextStep()
    }

    fun markNotificationPermissionHandled(granted: Boolean) {
        viewModelScope.launch {
            val settings = notificationSettingsUseCase.settings()
            notificationSettingsUseCase.update(settings.copy(notificationsEnabled = granted))
        }
        nextStep()
    }

    fun markHealthConnectUnavailable() {
        mutableState.update { it.copy(message = OnboardingMessage.HealthConnectUnavailable) }
        nextStep()
    }

    private fun submitPassword(snapshot: OnboardingUiState) {
        if (snapshot.passwordStrength == PasswordStrength.Weak) {
            mutableState.update { it.copy(message = OnboardingMessage.WeakPassword) }
            return
        }
        if (snapshot.password != snapshot.confirmPassword) {
            mutableState.update { it.copy(message = OnboardingMessage.PasswordMismatch) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, message = null) }
            when (localAuthUseCase.setPassword(snapshot.password.toCharArray())) {
                LocalAuthResult.Success -> {
                    localAuthUseCase.setBiometricUnlockEnabled(snapshot.biometricsEnabled)
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            password = "",
                            confirmPassword = "",
                            currentStep = OnboardingStep.Profile,
                        )
                    }
                }
                LocalAuthResult.WeakPassword -> mutableState.update {
                    it.copy(isSubmitting = false, message = OnboardingMessage.WeakPassword)
                }
                else -> mutableState.update {
                    it.copy(isSubmitting = false, message = OnboardingMessage.GenericError)
                }
            }
        }
    }

    private fun finish(snapshot: OnboardingUiState) {
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, message = null) }
            val profile = snapshot.toProfile()
            if (profile != null) profileRepository.saveProfile(profile)
            localAuthUseCase.setBiometricUnlockEnabled(snapshot.biometricsEnabled)
            onboardingRepository.setOnboardingCompleted(true)
            mutableState.update { it.copy(isSubmitting = false, completed = true) }
        }
    }

    private fun nextStep() {
        mutableState.update {
            it.copy(currentStep = it.currentStep.next(), message = null)
        }
    }

    private fun OnboardingStep.next(): OnboardingStep {
        val steps = OnboardingStep.entries
        val nextIndex = (ordinal + 1).coerceAtMost(steps.lastIndex)
        return steps[nextIndex]
    }

    private fun OnboardingUiState.toProfile(): UserProfile? {
        val name = displayName.trim().ifBlank { null }
        val parsedAge = age.toIntOrNull()
        val parsedHeight = heightCm.replace(',', '.').toDoubleOrNull()
        val parsedGender = gender.trim().ifBlank { null }
        val parsedGoal = goalType.trim().ifBlank { null }
        if (name == null && parsedAge == null && parsedHeight == null && parsedGender == null && parsedGoal == null) {
            return null
        }
        return UserProfile(
            displayName = name,
            age = parsedAge,
            heightCm = parsedHeight,
            gender = parsedGender,
            goalType = parsedGoal,
        )
    }
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.Welcome,
    val isSubmitting: Boolean = false,
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.Weak,
    val displayName: String = "",
    val age: String = "",
    val heightCm: String = "",
    val gender: String = "",
    val goalType: String = "",
    val biometricsEnabled: Boolean = false,
    val completed: Boolean = false,
    val message: OnboardingMessage? = null,
)

enum class OnboardingMessage {
    WeakPassword,
    PasswordMismatch,
    GenericError,
    HealthConnectUnavailable,
}
