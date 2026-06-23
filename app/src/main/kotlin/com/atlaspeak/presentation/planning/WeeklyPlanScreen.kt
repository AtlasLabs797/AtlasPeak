package com.atlaspeak.presentation.planning

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
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
                        Text(
                            text = stringResource(state.messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = day.completedThisWeek,
                    onCheckedChange = null,
                    enabled = false,
                )
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
                OutlinedTextField(
                    value = day.cardioTargetMinutes,
                    onValueChange = onCardioTargetMinutesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !day.isRestDay && day.cardioTypeId != null,
                    singleLine = true,
                    label = { Text(stringResource(R.string.weekly_plan_cardio_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            OutlinedTextField(
                value = day.notificationTime,
                onValueChange = onNotificationTimeChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !day.isRestDay && day.hasPlannedSession && day.notificationEnabled,
                singleLine = true,
                label = { Text(stringResource(R.string.weekly_plan_reminder_time)) },
                supportingText = { Text(stringResource(R.string.weekly_plan_reminder_best_effort)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(selectedType.labelRes()),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            listOf(WeeklyPlanDayType.Strength, WeeklyPlanDayType.Cardio).forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes())) },
                    onClick = {
                        expanded = false
                        onTypeSelected(type)
                    },
                    enabled = type != selectedType,
                )
            }
        }
    }
}

@Composable
private fun RoutineDropdown(
    selectedRoutineId: String?,
    selectedRoutineName: String?,
    routines: List<RoutineOption>,
    enabled: Boolean,
    onRoutineSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = selectedRoutineName ?: stringResource(R.string.weekly_plan_select_routine),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.weekly_plan_no_session)) },
                onClick = {
                    expanded = false
                    onRoutineSelected(null)
                },
            )
            routines.forEach { routine ->
                DropdownMenuItem(
                    text = { Text(routine.name) },
                    onClick = {
                        expanded = false
                        onRoutineSelected(routine.id)
                    },
                    enabled = routine.id != selectedRoutineId,
                )
            }
        }
    }
}

@Composable
private fun CardioDropdown(
    selectedCardioTypeId: String?,
    selectedCardioTypeName: String?,
    cardioTypes: List<CardioTypeOption>,
    enabled: Boolean,
    onCardioTypeSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = selectedCardioTypeName ?: stringResource(R.string.weekly_plan_select_cardio),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.weekly_plan_no_session)) },
                onClick = {
                    expanded = false
                    onCardioTypeSelected(null)
                },
            )
            cardioTypes.forEach { cardioType ->
                DropdownMenuItem(
                    text = { Text(cardioType.name) },
                    onClick = {
                        expanded = false
                        onCardioTypeSelected(cardioType.id)
                    },
                    enabled = cardioType.id != selectedCardioTypeId,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .semantics(mergeDescendants = true) { role = Role.Switch },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
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
