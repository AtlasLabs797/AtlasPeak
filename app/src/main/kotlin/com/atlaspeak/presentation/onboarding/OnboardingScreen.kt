package com.atlaspeak.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.onboarding.OnboardingStep
import com.atlaspeak.domain.model.onboarding.PasswordStrength
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun OnboardingRoute(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.markPermissionHandled() }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.markPermissionHandled() }
    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.markPermissionHandled() }

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    OnboardingScreen(
        state = state,
        onPrimaryAction = {
            when (state.currentStep) {
                OnboardingStep.Notifications -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.markPermissionHandled()
                    }
                }
                OnboardingStep.HealthConnect -> {
                    if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                        healthConnectLauncher.launch(healthConnectPermissions)
                    } else {
                        viewModel.markHealthConnectUnavailable()
                    }
                }
                OnboardingStep.Location -> locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                else -> viewModel.primaryAction()
            }
        },
        onSkip = viewModel::skipOptionalStep,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onAgeChanged = viewModel::onAgeChanged,
        onHeightChanged = viewModel::onHeightChanged,
        onGenderChanged = viewModel::onGenderChanged,
        onGoalChanged = viewModel::onGoalChanged,
        onBiometricsEnabledChanged = viewModel::onBiometricsEnabledChanged,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onPrimaryAction: () -> Unit,
    onSkip: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onGenderChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onBiometricsEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            LinearProgressIndicator(
                progress = { (state.currentStep.ordinal + 1) / OnboardingStep.entries.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            StepHeader(state.currentStep)
            StepBody(
                state = state,
                onPasswordChanged = onPasswordChanged,
                onConfirmPasswordChanged = onConfirmPasswordChanged,
                onDisplayNameChanged = onDisplayNameChanged,
                onAgeChanged = onAgeChanged,
                onHeightChanged = onHeightChanged,
                onGenderChanged = onGenderChanged,
                onGoalChanged = onGoalChanged,
                onBiometricsEnabledChanged = onBiometricsEnabledChanged,
            )
            OnboardingMessageText(state.message)
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                enabled = !state.isSubmitting,
                onClick = onPrimaryAction,
            ) {
                Text(stringResource(primaryActionRes(state.currentStep)))
            }
            if (state.currentStep != OnboardingStep.Password && state.currentStep != OnboardingStep.Done) {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget),
                    enabled = !state.isSubmitting,
                    onClick = onSkip,
                ) {
                    Text(stringResource(R.string.action_skip))
                }
            }
        }
    }
}

@Composable
private fun StepHeader(step: OnboardingStep) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = step.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(step.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(step.bodyRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepBody(
    state: OnboardingUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onGenderChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onBiometricsEnabledChanged: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    when (state.currentStep) {
        OnboardingStep.Password -> {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.auth_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChanged,
                label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Text(
                text = stringResource(state.passwordStrength.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = when (state.passwordStrength) {
                    PasswordStrength.Weak -> MaterialTheme.colorScheme.error
                    PasswordStrength.Medium -> MaterialTheme.colorScheme.onSurfaceVariant
                    PasswordStrength.Strong -> MaterialTheme.colorScheme.primary
                },
            )
        }
        OnboardingStep.Google -> {
            Text(
                text = stringResource(R.string.onboarding_google_status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OnboardingStep.Profile -> {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.displayName,
                onValueChange = onDisplayNameChanged,
                label = { Text(stringResource(R.string.onboarding_profile_name)) },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.age,
                    onValueChange = onAgeChanged,
                    label = { Text(stringResource(R.string.onboarding_profile_age)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.heightCm,
                    onValueChange = onHeightChanged,
                    label = { Text(stringResource(R.string.onboarding_profile_height)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.gender,
                onValueChange = onGenderChanged,
                label = { Text(stringResource(R.string.onboarding_profile_gender)) },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.goalType,
                onValueChange = onGoalChanged,
                label = { Text(stringResource(R.string.onboarding_profile_goal)) },
                singleLine = true,
            )
        }
        OnboardingStep.Biometrics -> {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Checkbox(
                    checked = state.biometricsEnabled,
                    onCheckedChange = onBiometricsEnabledChanged,
                )
                Text(
                    text = stringResource(R.string.auth_enable_biometrics),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun OnboardingMessageText(message: OnboardingMessage?) {
    if (message == null) return
    val messageRes = when (message) {
        OnboardingMessage.WeakPassword -> R.string.auth_error_weak_password
        OnboardingMessage.PasswordMismatch -> R.string.auth_error_passwords_mismatch
        OnboardingMessage.GenericError -> R.string.error_generic
        OnboardingMessage.HealthConnectUnavailable -> R.string.onboarding_health_unavailable
    }
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

private fun OnboardingStep.icon(): ImageVector = when (this) {
    OnboardingStep.Welcome -> Icons.Filled.RocketLaunch
    OnboardingStep.Google -> Icons.Filled.Cloud
    OnboardingStep.Password -> Icons.Filled.Password
    OnboardingStep.Profile -> Icons.Filled.Person
    OnboardingStep.Notifications -> Icons.Filled.Notifications
    OnboardingStep.HealthConnect -> Icons.Filled.Favorite
    OnboardingStep.Location -> Icons.Filled.LocationOn
    OnboardingStep.Biometrics -> Icons.Filled.Fingerprint
    OnboardingStep.Done -> Icons.Filled.CheckCircle
}

private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.Welcome -> R.string.onboarding_welcome_title
    OnboardingStep.Google -> R.string.onboarding_google_title
    OnboardingStep.Password -> R.string.onboarding_password_title
    OnboardingStep.Profile -> R.string.onboarding_profile_title
    OnboardingStep.Notifications -> R.string.onboarding_notifications_title
    OnboardingStep.HealthConnect -> R.string.onboarding_health_title
    OnboardingStep.Location -> R.string.onboarding_location_title
    OnboardingStep.Biometrics -> R.string.onboarding_biometrics_title
    OnboardingStep.Done -> R.string.onboarding_done_title
}

private fun OnboardingStep.bodyRes(): Int = when (this) {
    OnboardingStep.Welcome -> R.string.onboarding_welcome_body
    OnboardingStep.Google -> R.string.onboarding_google_body
    OnboardingStep.Password -> R.string.onboarding_password_warning
    OnboardingStep.Profile -> R.string.onboarding_profile_body
    OnboardingStep.Notifications -> R.string.onboarding_notifications_body
    OnboardingStep.HealthConnect -> R.string.onboarding_health_body
    OnboardingStep.Location -> R.string.onboarding_location_body
    OnboardingStep.Biometrics -> R.string.onboarding_biometrics_body
    OnboardingStep.Done -> R.string.onboarding_done_body
}

private fun PasswordStrength.labelRes(): Int = when (this) {
    PasswordStrength.Weak -> R.string.onboarding_password_strength_weak
    PasswordStrength.Medium -> R.string.onboarding_password_strength_medium
    PasswordStrength.Strong -> R.string.onboarding_password_strength_strong
}

private fun primaryActionRes(step: OnboardingStep): Int = when (step) {
    OnboardingStep.Welcome -> R.string.onboarding_get_started
    OnboardingStep.Done -> R.string.action_start
    OnboardingStep.Google -> R.string.action_continue
    OnboardingStep.Notifications,
    OnboardingStep.Location,
    OnboardingStep.HealthConnect,
    -> R.string.onboarding_permission_action
    else -> R.string.action_continue
}

private val healthConnectPermissions = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    HealthPermission.getWritePermission(WeightRecord::class),
    HealthPermission.getWritePermission(BodyFatRecord::class),
    HealthPermission.getWritePermission(LeanBodyMassRecord::class),
    HealthPermission.getWritePermission(BodyWaterMassRecord::class),
)
