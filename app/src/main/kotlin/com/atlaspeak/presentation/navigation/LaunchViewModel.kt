package com.atlaspeak.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
            onboardingRepository.onboardingCompleted.collect { completed ->
                mutableState.value = if (completed) LaunchState.Home else LaunchState.Onboarding
            }
        }
    }
}

enum class LaunchState {
    Loading,
    Onboarding,
    Home,
}
