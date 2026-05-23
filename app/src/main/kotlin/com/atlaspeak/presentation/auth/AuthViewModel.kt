package com.atlaspeak.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.auth.GoogleSignInResult
import com.atlaspeak.domain.model.auth.LocalAuthPolicy
import com.atlaspeak.domain.model.auth.LocalAuthResult
import com.atlaspeak.domain.usecase.auth.LocalAuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val localAuthUseCase: LocalAuthUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val passwordConfigured = localAuthUseCase.isPasswordConfigured()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isPasswordConfigured = passwordConfigured,
                    biometricsEnabled = passwordConfigured && localAuthUseCase.isBiometricUnlockEnabled(),
                )
            }
        }
    }

    fun onPasswordChanged(password: String) {
        mutableState.update { it.copy(password = password, message = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        mutableState.update { it.copy(confirmPassword = password, message = null) }
    }

    fun onBiometricsEnabledChanged(enabled: Boolean) {
        mutableState.update { it.copy(biometricsEnabled = enabled, message = null) }
    }

    fun submitLocalPassword() {
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isSubmitting) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, message = null) }
            val result = if (snapshot.isSetupMode) {
                if (snapshot.password != snapshot.confirmPassword) {
                    LocalAuthResult.InvalidCredentials(attemptsRemaining = LocalAuthPolicy.MAX_FAILED_ATTEMPTS)
                } else {
                    val setupResult = localAuthUseCase.setPassword(snapshot.password.toCharArray())
                    if (setupResult == LocalAuthResult.Success) {
                        localAuthUseCase.setBiometricUnlockEnabled(snapshot.biometricsEnabled)
                    }
                    setupResult
                }
            } else {
                localAuthUseCase.authenticate(snapshot.password.toCharArray())
            }
            handleLocalAuthResult(result)
        }
    }

    fun beginGoogleSignIn(): Boolean {
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isSubmitting) return false
        mutableState.update { it.copy(isSubmitting = true, message = null) }
        return true
    }

    fun onGoogleSignInResult(result: GoogleSignInResult) {
        val message = when (result) {
            is GoogleSignInResult.Success -> AuthUiMessage.GoogleRequiresLocalPassword
            GoogleSignInResult.Cancelled -> AuthUiMessage.GoogleCancelled
            GoogleSignInResult.NotConfigured -> AuthUiMessage.GoogleNotConfigured
            GoogleSignInResult.Failed -> AuthUiMessage.GoogleFailed
        }
        mutableState.update { it.copy(isSubmitting = false, isAuthenticated = false, message = message) }
    }

    fun onBiometricUnlockSucceeded() {
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isSubmitting || !snapshot.canUseBiometric) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, message = null) }
            handleLocalAuthResult(localAuthUseCase.recordBiometricUnlock())
        }
    }

    fun onBiometricUnlockFailed() {
        mutableState.update { it.copy(message = AuthUiMessage.BiometricFailed) }
    }

    private fun handleLocalAuthResult(result: LocalAuthResult) {
        val message = when (result) {
            LocalAuthResult.Success -> null
            LocalAuthResult.PasswordNotConfigured -> AuthUiMessage.LocalPasswordRequired
            LocalAuthResult.WeakPassword -> AuthUiMessage.WeakPassword
            is LocalAuthResult.InvalidCredentials -> {
                if (state.value.isSetupMode) AuthUiMessage.PasswordsDoNotMatch else AuthUiMessage.InvalidCredentials
            }
            is LocalAuthResult.Locked -> AuthUiMessage.Locked
        }
        mutableState.update {
            it.copy(
                isSubmitting = false,
                isAuthenticated = result == LocalAuthResult.Success,
                isPasswordConfigured = if (result == LocalAuthResult.Success) true else it.isPasswordConfigured,
                password = if (result == LocalAuthResult.Success) "" else it.password,
                confirmPassword = if (result == LocalAuthResult.Success) "" else it.confirmPassword,
                message = message,
            )
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isPasswordConfigured: Boolean? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val biometricsEnabled: Boolean = false,
    val isAuthenticated: Boolean = false,
    val message: AuthUiMessage? = null,
) {
    val isSetupMode: Boolean = isPasswordConfigured == false
    val canUseBiometric: Boolean = isPasswordConfigured == true && biometricsEnabled
}

enum class AuthUiMessage {
    InvalidCredentials,
    Locked,
    PasswordsDoNotMatch,
    WeakPassword,
    LocalPasswordRequired,
    GoogleRequiresLocalPassword,
    GoogleNotConfigured,
    GoogleCancelled,
    GoogleFailed,
    BiometricFailed,
}
