package com.atlaspeak.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val sessionId: String = requireNotNull(savedStateHandle[AppRoute.WorkoutComplete.SESSION_ID])
    private val mutableState = MutableStateFlow(WorkoutCompleteUiState())
    val state: StateFlow<WorkoutCompleteUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = false, session = workoutRepository.session(sessionId)) }
        }
    }
}

data class WorkoutCompleteUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
)
