package com.atlaspeak.presentation.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.profile.Gender
import com.atlaspeak.domain.model.profile.Goal
import com.atlaspeak.presentation.component.AtlasChip
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasStatusMessage
import com.atlaspeak.presentation.component.AtlasStatusTone
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, R.string.edit_profile_saved, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    EditProfileScreen(
        state = state,
        onBack = onBack,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onAgeChanged = viewModel::onAgeChanged,
        onHeightChanged = viewModel::onHeightChanged,
        onGenderChanged = viewModel::onGenderChanged,
        onGoalChanged = viewModel::onGoalChanged,
        onSave = viewModel::save,
    )
}

@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    onBack: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onGenderChanged: (Gender) -> Unit,
    onGoalChanged: (Goal) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@PremiumBackground
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            EditProfileHeader(onBack = onBack)
            if (state.loadFailed || state.saveFailed) {
                AtlasStatusMessage(
                    message = stringResource(R.string.error_generic),
                    tone = AtlasStatusTone.Error,
                )
            }
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
                            isError = state.ageInvalid,
                            supportingText = if (state.ageInvalid) {
                                stringResource(R.string.edit_profile_age_invalid)
                            } else {
                                null
                            },
                        )
                        AtlasTextField(
                            modifier = Modifier.weight(1f),
                            value = state.heightCm,
                            onValueChange = onHeightChanged,
                            label = stringResource(R.string.onboarding_profile_height),
                            keyboardType = KeyboardType.Decimal,
                            isError = state.heightInvalid,
                            supportingText = if (state.heightInvalid) {
                                stringResource(R.string.edit_profile_height_invalid)
                            } else {
                                null
                            },
                        )
                    }
                    EditProfileChoiceSelector(
                        label = stringResource(R.string.onboarding_profile_gender),
                        options = Gender.entries.map { it to stringResource(it.labelRes()) },
                        selected = state.gender,
                        onSelected = onGenderChanged,
                    )
                    EditProfileChoiceSelector(
                        label = stringResource(R.string.onboarding_profile_goal),
                        options = Goal.entries.map { it to stringResource(it.labelRes()) },
                        selected = state.goalType,
                        onSelected = onGoalChanged,
                    )
                }
            }
            AtlasPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
                onClick = onSave,
                text = stringResource(R.string.action_save),
            )
        }
    }
}

@Composable
private fun EditProfileHeader(onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.edit_profile_title),
                style = MaterialTheme.typography.headlineMedium,
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(R.string.edit_profile_body),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink2,
            )
        }
    }
}

@Composable
private fun <T : Any> EditProfileChoiceSelector(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
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
            items(options, key = { it.second }) { (option, optionLabel) ->
                AtlasChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    text = optionLabel,
                )
            }
        }
    }
}
