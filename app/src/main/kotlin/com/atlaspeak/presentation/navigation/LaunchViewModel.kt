package com.atlaspeak.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LaunchViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LaunchState>(LaunchState.Loading)
    val state: StateFlow<LaunchState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (mutableState.value == LaunchState.Loading) mutableState.value = LaunchState.Onboarding
        }
        viewModelScope.launch {
            onboardingRepository.onboardingCompleted
                .catch { mutableState.value = LaunchState.Onboarding }
                .collect { completed ->
                    mutableState.value = if (completed) LaunchState.Home else LaunchState.Onboarding
                }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 3_000L
    }
}

enum class LaunchState {
    Loading,
    Onboarding,
    Home,
}
