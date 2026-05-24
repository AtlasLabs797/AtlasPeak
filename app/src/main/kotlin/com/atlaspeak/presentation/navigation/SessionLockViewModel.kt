package com.atlaspeak.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.usecase.auth.LocalAuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SessionLockViewModel @Inject constructor(
    private val localAuthUseCase: LocalAuthUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionLockState())
    val state: StateFlow<SessionLockState> = mutableState.asStateFlow()

    fun evaluate(route: String?) {
        if (!isSensitiveRoute(route) || route == AppRoute.Login.route || route == AppRoute.Onboarding.route) {
            mutableState.update { it.copy(lockRequired = false) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(lockRequired = localAuthUseCase.shouldRequireSessionUnlock()) }
        }
    }

    fun onLockHandled() {
        mutableState.update { it.copy(lockRequired = false) }
    }
}

data class SessionLockState(
    val lockRequired: Boolean = false,
)
