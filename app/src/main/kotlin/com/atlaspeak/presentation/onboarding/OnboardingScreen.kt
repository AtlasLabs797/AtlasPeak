package com.atlaspeak.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.onboarding.OnboardingStep
import com.atlaspeak.presentation.component.AtlasChip
import com.atlaspeak.presentation.component.AtlasGhostButton
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.component.StepDots
import com.atlaspeak.presentation.theme.AtlasBrushes
import com.atlaspeak.presentation.theme.AtlasMotion
import com.atlaspeak.presentation.theme.LocalAtlasColors
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
    ) { granted -> viewModel.markNotificationPermissionHandled(granted) }
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
                        viewModel.markNotificationPermissionHandled(true)
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
        onBack = viewModel::previousStep,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onAgeChanged = viewModel::onAgeChanged,
        onHeightChanged = viewModel::onHeightChanged,
        onGenderChanged = viewModel::onGenderChanged,
        onGoalChanged = viewModel::onGoalChanged,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onPrimaryAction: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onGenderChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    val totalSteps = OnboardingStep.entries.size
    // Permite corregir pasos anteriores; en el primer paso deja salir de la app (sin interceptar).
    BackHandler(enabled = state.currentStep != OnboardingStep.Welcome && !state.isSubmitting) {
        onBack()
    }
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            StepDots(current = state.currentStep.ordinal, total = totalSteps)
            Text(
                text = stringResource(
                    R.string.onboarding_step_label,
                    state.currentStep.ordinal + 1,
                    totalSteps,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = atlasColors.ink3,
            )
            OnboardingHero(step = state.currentStep)
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(AtlasMotion.DurationMedium)) togetherWith
                        fadeOut(animationSpec = tween(AtlasMotion.DurationFast))
                },
                label = "",
            ) { step ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(
                        text = stringResource(step.titleRes()),
                        style = MaterialTheme.typography.headlineMedium,
                        color = atlasColors.ink,
                    )
                    Text(
                        text = stringResource(step.bodyRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = atlasColors.ink2,
                    )
                    StepBody(
                        state = state,
                        onDisplayNameChanged = onDisplayNameChanged,
                        onAgeChanged = onAgeChanged,
                        onHeightChanged = onHeightChanged,
                        onGenderChanged = onGenderChanged,
                        onGoalChanged = onGoalChanged,
                    )
                }
            }
            OnboardingMessageText(state.message)
            Spacer(Modifier.height(spacing.xs))
            AtlasPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
                onClick = onPrimaryAction,
                text = stringResource(primaryActionRes(state.currentStep)),
            )
            val showBack = state.currentStep != OnboardingStep.Welcome
            val showSkip = state.currentStep != OnboardingStep.Done
            if (showBack || showSkip) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (showBack) {
                        AtlasGhostButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSubmitting,
                            onClick = onBack,
                            text = stringResource(R.string.action_back),
                        )
                    }
                    if (showSkip) {
                        AtlasGhostButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSubmitting,
                            onClick = onSkip,
                            text = stringResource(R.string.action_skip),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHero(step: OnboardingStep) {
    val colors = MaterialTheme.colorScheme
    val atlasColors = LocalAtlasColors.current
    val decorationCd = stringResource(R.string.onboarding_hero_decoration_cd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(AtlasBrushes.heroGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_onboarding_hero),
            contentDescription = decorationCd,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.icon(),
                contentDescription = null,
                tint = atlasColors.ink,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun StepBody(
    state: OnboardingUiState,
    onDisplayNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onGenderChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    when (state.currentStep) {
        OnboardingStep.Profile -> {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(spacing.card),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    AtlasTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.displayName,
                        onValueChange = onDisplayNameChanged,
                        label = stringResource(R.string.onboarding_profile_name),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        AtlasTextField(
                            modifier = Modifier.weight(1f),
                            value = state.age,
                            onValueChange = onAgeChanged,
                            label = stringResource(R.string.onboarding_profile_age),
                            keyboardType = KeyboardType.Number,
                        )
                        AtlasTextField(
                            modifier = Modifier.weight(1f),
                            value = state.heightCm,
                            onValueChange = onHeightChanged,
                            label = stringResource(R.string.onboarding_profile_height),
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    ChoiceSelector(
                        label = stringResource(R.string.onboarding_profile_gender),
                        options = listOf(
                            stringResource(R.string.onboarding_gender_male),
                            stringResource(R.string.onboarding_gender_female),
                        ),
                        selected = state.gender,
                        onSelected = onGenderChanged,
                    )
                    ChoiceSelector(
                        label = stringResource(R.string.onboarding_profile_goal),
                        options = listOf(
                            stringResource(R.string.onboarding_goal_fat_loss),
                            stringResource(R.string.onboarding_goal_muscle_gain),
                            stringResource(R.string.onboarding_goal_maintenance),
                            stringResource(R.string.onboarding_goal_endurance),
                        ),
                        selected = state.goalType,
                        onSelected = onGoalChanged,
                    )
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ChoiceSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = atlasColors.ink3,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            items(options, key = { it }) { option ->
                AtlasChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    text = option,
                )
            }
        }
    }
}

@Composable
private fun OnboardingMessageText(message: OnboardingMessage?) {
    if (message == null) return
    val messageRes = when (message) {
        OnboardingMessage.GenericError -> R.string.error_generic
        OnboardingMessage.HealthConnectUnavailable -> R.string.onboarding_health_unavailable
    }
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalAtlasColors.current.risk,
        textAlign = TextAlign.Start,
    )
}

private fun OnboardingStep.icon(): ImageVector = when (this) {
    OnboardingStep.Welcome -> Icons.Filled.RocketLaunch
    OnboardingStep.Profile -> Icons.Filled.Person
    OnboardingStep.Notifications -> Icons.Filled.Notifications
    OnboardingStep.HealthConnect -> Icons.Filled.Favorite
    OnboardingStep.Location -> Icons.Filled.LocationOn
    OnboardingStep.Done -> Icons.Filled.CheckCircle
}

private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.Welcome -> R.string.onboarding_welcome_title
    OnboardingStep.Profile -> R.string.onboarding_profile_title
    OnboardingStep.Notifications -> R.string.onboarding_notifications_title
    OnboardingStep.HealthConnect -> R.string.onboarding_health_title
    OnboardingStep.Location -> R.string.onboarding_location_title
    OnboardingStep.Done -> R.string.onboarding_done_title
}

private fun OnboardingStep.bodyRes(): Int = when (this) {
    OnboardingStep.Welcome -> R.string.onboarding_welcome_body
    OnboardingStep.Profile -> R.string.onboarding_profile_body
    OnboardingStep.Notifications -> R.string.onboarding_notifications_body
    OnboardingStep.HealthConnect -> R.string.onboarding_health_body
    OnboardingStep.Location -> R.string.onboarding_location_body
    OnboardingStep.Done -> R.string.onboarding_done_body
}

private fun primaryActionRes(step: OnboardingStep): Int = when (step) {
    OnboardingStep.Welcome -> R.string.onboarding_get_started
    OnboardingStep.Done -> R.string.action_start
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
