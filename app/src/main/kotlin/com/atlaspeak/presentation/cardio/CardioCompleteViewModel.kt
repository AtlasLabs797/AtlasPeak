package com.atlaspeak.presentation.cardio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CardioCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardioRepository: CardioRepository,
) : ViewModel() {
    private val sessionId: String = requireNotNull(savedStateHandle[AppRoute.CardioComplete.SESSION_ID])
    private val mutableState = MutableStateFlow(CardioCompleteUiState())
    val state: StateFlow<CardioCompleteUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    session = cardioRepository.session(sessionId),
                )
            }
        }
    }
}

data class CardioCompleteUiState(
    val isLoading: Boolean = true,
    val session: CardioSession? = null,
)
