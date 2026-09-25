package com.atlaspeak.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.onboarding.OnboardingStep
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.model.profile.Gender
import com.atlaspeak.domain.model.profile.Goal
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.usecase.planning.NotificationSettingsUseCase
import com.atlaspeak.domain.usecase.profile.ProfileValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val profileRepository: ProfileRepository,
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
            OnboardingStep.Done -> finish(snapshot)
            OnboardingStep.Profile -> {
                // BUG-099 (Fase 10 P2): antes este paso no validaba rangos y
                // `toProfile()` persistia cualquier valor numerico (ej. edad
                // 999). Reutilizamos `ProfileValidation` para bloquear el
                // avance con valores fuera de rango, igual que en
                // EditProfileViewModel.
                val ageInvalid = snapshot.age.isNotBlank() && !ProfileValidation.ageIsValid(snapshot.age)
                val heightInvalid = snapshot.heightCm.isNotBlank() &&
                    !ProfileValidation.heightIsValid(snapshot.heightCm.replace(',', '.'))
                if (ageInvalid || heightInvalid) {
                    mutableState.update { it.copy(ageInvalid = ageInvalid, heightInvalid = heightInvalid) }
                    return
                }
                nextStep()
            }
            else -> nextStep()
        }
    }

    fun skipOptionalStep() {
        nextStep()
    }

    fun onDisplayNameChanged(value: String) {
        mutableState.update { it.copy(displayName = value) }
    }

    fun onAgeChanged(value: String) {
        mutableState.update { it.copy(age = value.filter(Char::isDigit), ageInvalid = false) }
    }

    fun onHeightChanged(value: String) {
        mutableState.update {
            it.copy(
                heightCm = value.filter { char -> char.isDigit() || char == '.' || char == ',' },
                heightInvalid = false,
            )
        }
    }

    fun onGenderChanged(value: String) {
        mutableState.update { it.copy(gender = value) }
    }

    fun onGoalChanged(value: String) {
        mutableState.update { it.copy(goalType = value) }
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

    private fun finish(snapshot: OnboardingUiState) {
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, message = null) }
            try {
                val profile = snapshot.toProfile()
                if (profile != null) profileRepository.saveProfile(profile)
                onboardingRepository.setOnboardingCompleted(true)
                mutableState.update { it.copy(isSubmitting = false, completed = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isSubmitting = false, message = OnboardingMessage.GenericError) }
            }
        }
    }

    fun previousStep() {
        mutableState.update {
            it.copy(currentStep = it.currentStep.previous(), message = null)
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

    private fun OnboardingStep.previous(): OnboardingStep {
        val steps = OnboardingStep.entries
        val previousIndex = (ordinal - 1).coerceAtLeast(0)
        return steps[previousIndex]
    }

    private fun OnboardingUiState.toProfile(): UserProfile? {
        val name = displayName.trim().ifBlank { null }
        // BUG-099: valores fuera de rango (o no numericos) nunca deben
        // persistirse, sin importar como se llegue aqui.
        val parsedAge = age.takeIf { ProfileValidation.ageIsValid(it) }?.toIntOrNull()
        val parsedHeight = heightCm.replace(',', '.')
            .takeIf { ProfileValidation.heightIsValid(it) }
            ?.toDoubleOrNull()
        val parsedGender = Gender.fromStorageValue(gender.trim().ifBlank { null })
        val parsedGoal = Goal.fromStorageValue(goalType.trim().ifBlank { null })
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
    val displayName: String = "",
    val age: String = "",
    val heightCm: String = "",
    val gender: String = "",
    val goalType: String = "",
    val completed: Boolean = false,
    val message: OnboardingMessage? = null,
    val ageInvalid: Boolean = false,
    val heightInvalid: Boolean = false,
)

enum class OnboardingMessage {
    GenericError,
    HealthConnectUnavailable,
}
