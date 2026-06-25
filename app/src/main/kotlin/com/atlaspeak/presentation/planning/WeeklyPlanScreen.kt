package com.atlaspeak.presentation.planning

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.presentation.component.AtlasDropdown
import com.atlaspeak.presentation.component.AtlasDropdownItem
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSwitchRow
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun WeeklyPlanRoute(
    onBack: () -> Unit,
    viewModel: WeeklyPlanViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    WeeklyPlanScreen(
        state = state,
        onBack = onBack,
        onDayTypeSelected = viewModel::setDayType,
        onRoutineSelected = viewModel::selectRoutine,
        onCardioTypeSelected = viewModel::selectCardioType,
        onCardioTargetMinutesChanged = viewModel::setCardioTargetMinutes,
        onRestDayChanged = viewModel::setRestDay,
        onNotificationEnabledChanged = viewModel::setNotificationEnabled,
        onNotificationTimeChanged = viewModel::setNotificationTime,
        onSaveDay = viewModel::saveDay,
    )
}

@Composable
fun WeeklyPlanScreen(
    state: WeeklyPlanUiState,
    onBack: () -> Unit,
    onDayTypeSelected: (Int, WeeklyPlanDayType) -> Unit,
    onRoutineSelected: (Int, String?) -> Unit,
    onCardioTypeSelected: (Int, String?) -> Unit,
    onCardioTargetMinutesChanged: (Int, String) -> Unit,
    onRestDayChanged: (Int, Boolean) -> Unit,
    onNotificationEnabledChanged: (Int, Boolean) -> Unit,
    onNotificationTimeChanged: (Int, String) -> Unit,
    onSaveDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        if (state.isLoading && state.days.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.screen),
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            ) {
                item {
                    ScreenHeader(
                        titleRes = R.string.weekly_plan_title,
                        bodyRes = R.string.weekly_plan_body,
                        onBack = onBack,
                    )
                }
                if (state.messageRes != null) {
                    item {
                        val atlasColors = LocalAtlasColors.current
                        Text(
                            text = stringResource(state.messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = atlasColors.ink2,
                        )
                    }
                }
                items(
                    count = state.days.size,
                    key = { index -> state.days[index].dayOfWeek },
                ) { index ->
                    WeeklyPlanDayCard(
                        day = state.days[index],
                        routines = state.routines,
                        cardioTypes = state.cardioTypes,
                        onDayTypeSelected = { type -> onDayTypeSelected(state.days[index].dayOfWeek, type) },
                        onRoutineSelected = { routineId -> onRoutineSelected(state.days[index].dayOfWeek, routineId) },
                        onCardioTypeSelected = { cardioTypeId -> onCardioTypeSelected(state.days[index].dayOfWeek, cardioTypeId) },
                        onCardioTargetMinutesChanged = { minutes -> onCardioTargetMinutesChanged(state.days[index].dayOfWeek, minutes) },
                        onRestDayChanged = { onRestDayChanged(state.days[index].dayOfWeek, it) },
                        onNotificationEnabledChanged = { onNotificationEnabledChanged(state.days[index].dayOfWeek, it) },
                        onNotificationTimeChanged = { onNotificationTimeChanged(state.days[index].dayOfWeek, it) },
                        onSave = { onSaveDay(state.days[index].dayOfWeek) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onBack: () -> Unit,
) {
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
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink2,
            )
        }
    }
}

@Composable
private fun WeeklyPlanDayCard(
    day: WeeklyPlanDayDraft,
    routines: List<RoutineOption>,
    cardioTypes: List<CardioTypeOption>,
    onDayTypeSelected: (WeeklyPlanDayType) -> Unit,
    onRoutineSelected: (String?) -> Unit,
    onCardioTypeSelected: (String?) -> Unit,
    onCardioTargetMinutesChanged: (String) -> Unit,
    onRestDayChanged: (Boolean) -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onNotificationTimeChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = LocalSpacing.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (day.completedThisWeek) {
                        LocalAtlasColors.current.ink
                    } else {
                        LocalAtlasColors.current.ink3
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(day.dayOfWeek.dayLabelRes()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = day.plannedName ?: stringResource(if (day.isRestDay) R.string.weekly_plan_rest_day else R.string.weekly_plan_no_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAtlasColors.current.ink3,
                    )
                }
            }
            PlanTypeDropdown(
                selectedType = day.type,
                enabled = !day.isRestDay,
                onTypeSelected = onDayTypeSelected,
            )
            if (day.type == WeeklyPlanDayType.Cardio) {
                CardioDropdown(
                    selectedCardioTypeId = day.cardioTypeId,
                    selectedCardioTypeName = day.cardioTypeName,
                    cardioTypes = cardioTypes,
                    enabled = !day.isRestDay,
                    onCardioTypeSelected = onCardioTypeSelected,
                )
                AtlasTextField(
                    value = day.cardioTargetMinutes,
                    onValueChange = onCardioTargetMinutesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !day.isRestDay && day.cardioTypeId != null,
                    label = stringResource(R.string.weekly_plan_cardio_minutes),
                    keyboardType = KeyboardType.Number,
                )
            } else {
                RoutineDropdown(
                    selectedRoutineId = day.routineId,
                    selectedRoutineName = day.routineName,
                    routines = routines,
                    enabled = !day.isRestDay,
                    onRoutineSelected = onRoutineSelected,
                )
            }
            SettingSwitchRow(
                labelRes = R.string.weekly_plan_rest_day,
                checked = day.isRestDay,
                onCheckedChange = onRestDayChanged,
            )
            SettingSwitchRow(
                labelRes = R.string.weekly_plan_reminder_enabled,
                checked = day.notificationEnabled,
                enabled = !day.isRestDay && day.hasPlannedSession,
                onCheckedChange = onNotificationEnabledChanged,
            )
            AtlasTextField(
                value = day.notificationTime,
                onValueChange = onNotificationTimeChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !day.isRestDay && day.hasPlannedSession && day.notificationEnabled,
                label = stringResource(R.string.weekly_plan_reminder_time),
                supportingText = stringResource(R.string.weekly_plan_reminder_best_effort),
                keyboardType = KeyboardType.Text,
            )
            AtlasPrimaryButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_save),
            )
        }
    }
}

@Composable
private fun PlanTypeDropdown(
    selectedType: WeeklyPlanDayType,
    enabled: Boolean,
    onTypeSelected: (WeeklyPlanDayType) -> Unit,
) {
    AtlasDropdown(
        label = stringResource(R.string.weekly_plan_type_label),
        selectedLabel = stringResource(selectedType.labelRes()),
        items = listOf(
            AtlasDropdownItem(WeeklyPlanDayType.Strength, stringResource(R.string.weekly_plan_type_strength)),
            AtlasDropdownItem(WeeklyPlanDayType.Cardio, stringResource(R.string.weekly_plan_type_cardio)),
        ),
        onSelected = onTypeSelected,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RoutineDropdown(
    selectedRoutineId: String?,
    selectedRoutineName: String?,
    routines: List<RoutineOption>,
    enabled: Boolean,
    onRoutineSelected: (String?) -> Unit,
) {
    AtlasDropdown(
        label = stringResource(R.string.weekly_plan_select_routine),
        selectedLabel = selectedRoutineName ?: stringResource(R.string.weekly_plan_no_session),
        items = listOf(
            AtlasDropdownItem<String?>(null, stringResource(R.string.weekly_plan_no_session)),
        ) + routines.map { routine -> AtlasDropdownItem<String?>(routine.id, routine.name) },
        onSelected = onRoutineSelected,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CardioDropdown(
    selectedCardioTypeId: String?,
    selectedCardioTypeName: String?,
    cardioTypes: List<CardioTypeOption>,
    enabled: Boolean,
    onCardioTypeSelected: (String?) -> Unit,
) {
    AtlasDropdown(
        label = stringResource(R.string.weekly_plan_select_cardio),
        selectedLabel = selectedCardioTypeName ?: stringResource(R.string.weekly_plan_no_session),
        items = listOf(
            AtlasDropdownItem<String?>(null, stringResource(R.string.weekly_plan_no_session)),
        ) + cardioTypes.map { cardioType -> AtlasDropdownItem<String?>(cardioType.id, cardioType.name) },
        onSelected = onCardioTypeSelected,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingSwitchRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    AtlasSwitchRow(
        title = stringResource(labelRes),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

@StringRes
private fun WeeklyPlanDayType.labelRes(): Int = when (this) {
    WeeklyPlanDayType.Strength -> R.string.weekly_plan_type_strength
    WeeklyPlanDayType.Cardio -> R.string.weekly_plan_type_cardio
}

@StringRes
private fun Int.dayLabelRes(): Int {
    return when (this) {
        1 -> R.string.weekday_monday
        2 -> R.string.weekday_tuesday
        3 -> R.string.weekday_wednesday
        4 -> R.string.weekday_thursday
        5 -> R.string.weekday_friday
        6 -> R.string.weekday_saturday
        else -> R.string.weekday_sunday
    }
}
