package com.atlaspeak.presentation.planning

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.planning.WeeklyPlanUpdate
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
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
                val days = weeklyPlanUseCase.plan().map { day ->
                    WeeklyPlanDayDraft(
                        dayOfWeek = day.dayOfWeek,
                        routineId = day.routineId,
                        routineName = day.routineName,
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
                isRestDay = false,
                notificationEnabled = routineId != null && draft.notificationEnabled,
            )
        }
    }

    fun setRestDay(dayOfWeek: Int, restDay: Boolean) {
        updateDay(dayOfWeek) { draft ->
            if (restDay) {
                draft.copy(
                    isRestDay = true,
                    routineId = null,
                    routineName = null,
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

    fun saveDay(dayOfWeek: Int) {
        val draft = mutableState.value.days.firstOrNull { it.dayOfWeek == dayOfWeek } ?: return
        viewModelScope.launch {
            val saved = weeklyPlanUseCase.updateDay(
                WeeklyPlanUpdate(
                    dayOfWeek = draft.dayOfWeek,
                    routineId = draft.routineId,
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

    private companion object {
        const val MAX_TIME_LENGTH = 5
    }
}

data class WeeklyPlanUiState(
    val isLoading: Boolean = true,
    val routines: List<RoutineOption> = emptyList(),
    val days: List<WeeklyPlanDayDraft> = emptyList(),
    @StringRes val messageRes: Int? = null,
)

data class RoutineOption(
    val id: String,
    val name: String,
)

data class WeeklyPlanDayDraft(
    val dayOfWeek: Int,
    val routineId: String?,
    val routineName: String?,
    val isRestDay: Boolean,
    val notificationEnabled: Boolean,
    val notificationTime: String,
    val completedThisWeek: Boolean,
)
