package com.atlaspeak.presentation.planning

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.planning.WeeklyPlanUpdate
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.domain.usecase.workout.RoutineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WeeklyPlanViewModel @Inject constructor(
    private val weeklyPlanUseCase: WeeklyPlanUseCase,
    private val routineUseCase: RoutineUseCase,
    private val cardioUseCase: CardioUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WeeklyPlanUiState())
    val state: StateFlow<WeeklyPlanUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, messageRes = null) }
            try {
                val routines = routineUseCase.routines().map { it.toOption() }
                val cardioTypes = cardioUseCase.cardioTypes().map { it.toOption() }
                val days = weeklyPlanUseCase.plan().map { day ->
                    WeeklyPlanDayDraft(
                        dayOfWeek = day.dayOfWeek,
                        type = day.type,
                        routineId = day.routineId,
                        routineName = day.routineName,
                        cardioTypeId = day.cardioTypeId,
                        cardioTypeName = day.cardioTypeName,
                        cardioTargetMinutes = ((day.cardioTargetDurationSec ?: WeeklyPlanUseCase.DEFAULT_CARDIO_TARGET_SECONDS) / 60).toString(),
                        isRestDay = day.isRestDay,
                        notificationEnabled = day.notificationEnabled,
                        notificationTime = day.notificationTime ?: WeeklyPlanUseCase.DEFAULT_REMINDER_TIME,
                        completedThisWeek = day.completedThisWeek,
                    )
                }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        routines = routines,
                        cardioTypes = cardioTypes,
                        days = days,
                        messageRes = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoading = false, messageRes = R.string.error_generic) }
            }
        }
    }

    fun selectRoutine(dayOfWeek: Int, routineId: String?) {
        updateDay(dayOfWeek) { draft ->
            val routine = mutableState.value.routines.firstOrNull { it.id == routineId }
            draft.copy(
                routineId = routineId,
                routineName = routine?.name,
                cardioTypeId = null,
                cardioTypeName = null,
                type = WeeklyPlanDayType.Strength,
                isRestDay = false,
                notificationEnabled = routineId != null && draft.notificationEnabled,
            )
        }
    }

    fun selectCardioType(dayOfWeek: Int, cardioTypeId: String?) {
        updateDay(dayOfWeek) { draft ->
            val cardioType = mutableState.value.cardioTypes.firstOrNull { it.id == cardioTypeId }
            draft.copy(
                type = WeeklyPlanDayType.Cardio,
                routineId = null,
                routineName = null,
                cardioTypeId = cardioTypeId,
                cardioTypeName = cardioType?.name,
                isRestDay = false,
                notificationEnabled = cardioTypeId != null && draft.notificationEnabled,
            )
        }
    }

    fun setDayType(dayOfWeek: Int, type: WeeklyPlanDayType) {
        updateDay(dayOfWeek) { draft ->
            if (type == WeeklyPlanDayType.Cardio) {
                draft.copy(type = WeeklyPlanDayType.Cardio, routineId = null, routineName = null, isRestDay = false)
            } else {
                draft.copy(type = WeeklyPlanDayType.Strength, cardioTypeId = null, cardioTypeName = null, isRestDay = false)
            }
        }
    }

    fun setRestDay(dayOfWeek: Int, restDay: Boolean) {
        updateDay(dayOfWeek) { draft ->
            if (restDay) {
                draft.copy(
                    isRestDay = true,
                    routineId = null,
                    routineName = null,
                    cardioTypeId = null,
                    cardioTypeName = null,
                    notificationEnabled = false,
                )
            } else {
                draft.copy(isRestDay = false)
            }
        }
    }

    fun setNotificationEnabled(dayOfWeek: Int, enabled: Boolean) {
        updateDay(dayOfWeek) { it.copy(notificationEnabled = enabled) }
    }

    fun setNotificationTime(dayOfWeek: Int, value: String) {
        updateDay(dayOfWeek) { it.copy(notificationTime = value.filter { char -> char.isDigit() || char == ':' }.take(MAX_TIME_LENGTH)) }
    }

    fun setCardioTargetMinutes(dayOfWeek: Int, value: String) {
        updateDay(dayOfWeek) { it.copy(cardioTargetMinutes = value.onlyDigits().take(MAX_CARDIO_MINUTES_LENGTH)) }
    }

    fun saveDay(dayOfWeek: Int) {
        val draft = mutableState.value.days.firstOrNull { it.dayOfWeek == dayOfWeek } ?: return
        viewModelScope.launch {
            val saved = weeklyPlanUseCase.updateDay(
                WeeklyPlanUpdate(
                    dayOfWeek = draft.dayOfWeek,
                    type = draft.type,
                    routineId = draft.routineId,
                    cardioTypeId = draft.cardioTypeId,
                    cardioTargetDurationSec = draft.cardioTargetSeconds,
                    isRestDay = draft.isRestDay,
                    notificationEnabled = draft.notificationEnabled,
                    notificationTime = draft.notificationTime,
                ),
            )
            if (saved) {
                refresh()
                mutableState.update { it.copy(messageRes = R.string.weekly_plan_saved) }
            } else {
                mutableState.update { it.copy(messageRes = R.string.weekly_plan_invalid_time) }
            }
        }
    }

    private fun updateDay(dayOfWeek: Int, transform: (WeeklyPlanDayDraft) -> WeeklyPlanDayDraft) {
        mutableState.update { state ->
            state.copy(
                days = state.days.map { day ->
                    if (day.dayOfWeek == dayOfWeek) transform(day) else day
                },
                messageRes = null,
            )
        }
    }

    private fun Routine.toOption() = RoutineOption(id = id, name = name)
    private fun CardioType.toOption() = CardioTypeOption(id = id, name = name)

    private companion object {
        const val MAX_TIME_LENGTH = 5
        const val MAX_CARDIO_MINUTES_LENGTH = 3
    }
}

data class WeeklyPlanUiState(
    val isLoading: Boolean = true,
    val routines: List<RoutineOption> = emptyList(),
    val cardioTypes: List<CardioTypeOption> = emptyList(),
    val days: List<WeeklyPlanDayDraft> = emptyList(),
    @StringRes val messageRes: Int? = null,
)

data class RoutineOption(
    val id: String,
    val name: String,
)

data class CardioTypeOption(
    val id: String,
    val name: String,
)

data class WeeklyPlanDayDraft(
    val dayOfWeek: Int,
    val type: WeeklyPlanDayType,
    val routineId: String?,
    val routineName: String?,
    val cardioTypeId: String?,
    val cardioTypeName: String?,
    val cardioTargetMinutes: String,
    val isRestDay: Boolean,
    val notificationEnabled: Boolean,
    val notificationTime: String,
    val completedThisWeek: Boolean,
) {
    val cardioTargetSeconds: Int? = cardioTargetMinutes.toIntOrNull()?.coerceAtLeast(1)?.times(60)
    val plannedName: String? = if (type == WeeklyPlanDayType.Cardio) cardioTypeName else routineName
    val hasPlannedSession: Boolean = if (type == WeeklyPlanDayType.Cardio) cardioTypeId != null else routineId != null
}

private fun String.onlyDigits(): String = filter { it.isDigit() }
