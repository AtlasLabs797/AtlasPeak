package com.atlaspeak.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.usecase.auth.BiometricAuthPolicy
import com.atlaspeak.domain.usecase.auth.GoogleSignInUseCase
import com.atlaspeak.presentation.theme.LocalSpacing
import kotlinx.coroutines.launch

@Composable
fun LoginRoute(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val activity = LocalContext.current as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val biometricPolicy = remember { BiometricAuthPolicy() }
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

    LoginScreen(
        state = state,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onBiometricsEnabledChanged = viewModel::onBiometricsEnabledChanged,
        onSubmit = viewModel::submitLocalPassword,
        onGoogleSignIn = {
            if (activity != null && viewModel.beginGoogleSignIn()) {
                coroutineScope.launch {
                    val result = GoogleSignInUseCase(CredentialManagerGoogleAuthClient(activity))()
                    viewModel.onGoogleSignInResult(result)
                }
            }
        },
        onBiometricUnlock = {
            if (activity != null) {
                BiometricPromptAuthenticator(activity, biometricPolicy).authenticate(
                    title = activity.getString(R.string.auth_biometric_prompt_title),
                    subtitle = activity.getString(R.string.auth_biometric_prompt_subtitle),
                    negativeButtonText = activity.getString(R.string.action_cancel),
                    onSuccess = viewModel::onBiometricUnlockSucceeded,
                    onError = viewModel::onBiometricUnlockFailed,
                )
            }
        },
    )
}

@Composable
fun LoginScreen(
    state: AuthUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onBiometricsEnabledChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onBiometricUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val titleRes = if (state.isSetupMode) R.string.auth_setup_title else R.string.auth_unlock
    val bodyRes = if (state.isSetupMode) R.string.auth_setup_body else R.string.auth_unlock_body

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                PasswordField(
                    value = state.password,
                    onValueChange = onPasswordChanged,
                    label = stringResource(R.string.auth_password_label),
                    imeAction = if (state.isSetupMode) ImeAction.Next else ImeAction.Done,
                    onDone = onSubmit,
                    enabled = !state.isSubmitting,
                )
                if (state.isSetupMode) {
                    PasswordField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChanged,
                        label = stringResource(R.string.auth_confirm_password_label),
                        imeAction = ImeAction.Done,
                        onDone = onSubmit,
                        enabled = !state.isSubmitting,
                    )
                    BiometricSetupRow(
                        checked = state.biometricsEnabled,
                        onCheckedChange = onBiometricsEnabledChanged,
                        enabled = !state.isSubmitting,
                    )
                }

                AuthMessageText(state.message)

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget),
                    onClick = onSubmit,
                    enabled = !state.isSubmitting && state.password.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = spacing.xs),
                        text = stringResource(if (state.isSetupMode) R.string.auth_setup_action else R.string.auth_unlock),
                    )
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget),
                    onClick = onGoogleSignIn,
                    enabled = !state.isSubmitting,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = spacing.xs),
                        text = stringResource(R.string.auth_google_action),
                    )
                }
                if (state.canUseBiometric) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.minTouchTarget),
                        onClick = onBiometricUnlock,
                        enabled = !state.isSubmitting,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                        )
                        Text(
                            modifier = Modifier.padding(start = spacing.xs),
                            text = stringResource(R.string.auth_biometric_action),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricSetupRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val spacing = LocalSpacing.current
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = stringResource(R.string.auth_enable_biometrics),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Password,
                contentDescription = null,
            )
        },
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun AuthMessageText(message: AuthUiMessage?) {
    if (message == null) return
    val messageRes = when (message) {
        AuthUiMessage.InvalidCredentials -> R.string.auth_error_invalid_credentials
        AuthUiMessage.Locked -> R.string.auth_locked_message
        AuthUiMessage.PasswordsDoNotMatch -> R.string.auth_error_passwords_mismatch
        AuthUiMessage.WeakPassword -> R.string.auth_error_weak_password
        AuthUiMessage.LocalPasswordRequired -> R.string.auth_error_local_password_required
        AuthUiMessage.GoogleRequiresLocalPassword -> R.string.auth_google_requires_local_password
        AuthUiMessage.GoogleNotConfigured -> R.string.auth_error_google_not_configured
        AuthUiMessage.GoogleCancelled -> R.string.auth_google_cancelled
        AuthUiMessage.GoogleFailed -> R.string.auth_error_google_failed
        AuthUiMessage.BiometricFailed -> R.string.auth_error_biometric_failed
    }
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
