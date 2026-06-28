package com.atlaspeak.presentation.planning

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.planning.WeeklyPlanUpdate
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.model.planning.WeeklyPlanSessionUpdate
import com.atlaspeak.domain.model.workout.Routine
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.domain.usecase.workout.RoutineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
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
                        isRestDay = day.isRestDay,
                        completedThisWeek = day.completedThisWeek,
                        sessions = day.sessions.map { session ->
                            WeeklyPlanSessionDraft(
                                id = session.id,
                                type = session.type,
                                routineId = session.routineId,
                                routineName = session.routineName,
                                cardioTypeId = session.cardioTypeId,
                                cardioTypeName = session.cardioTypeName,
                                cardioTargetMinutes = ((session.cardioTargetDurationSec ?: WeeklyPlanUseCase.DEFAULT_CARDIO_TARGET_SECONDS) / 60).toString(),
                                notificationEnabled = session.notificationEnabled,
                                notificationTime = session.notificationTime ?: WeeklyPlanUseCase.DEFAULT_REMINDER_TIME,
                                completedThisWeek = session.completedThisWeek,
                            )
                        },
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

    fun addSession(dayOfWeek: Int, type: WeeklyPlanDayType) {
        updateDay(dayOfWeek) { draft ->
            draft.copy(
                isRestDay = false,
                sessions = draft.sessions + WeeklyPlanSessionDraft(
                    id = "draft_${UUID.randomUUID()}",
                    type = type,
                    routineId = null,
                    routineName = null,
                    cardioTypeId = null,
                    cardioTypeName = null,
                    cardioTargetMinutes = (WeeklyPlanUseCase.DEFAULT_CARDIO_TARGET_SECONDS / 60).toString(),
                    notificationEnabled = false,
                    notificationTime = WeeklyPlanUseCase.DEFAULT_REMINDER_TIME,
                    completedThisWeek = false,
                ),
            )
        }
    }

    fun removeSession(dayOfWeek: Int, sessionId: String) {
        updateDay(dayOfWeek) { draft ->
            val sessions = draft.sessions.filterNot { it.id == sessionId }
            draft.copy(isRestDay = sessions.isEmpty(), sessions = sessions)
        }
    }

    fun selectRoutine(dayOfWeek: Int, sessionId: String, routineId: String?) {
        updateSession(dayOfWeek, sessionId) { draft ->
            val routine = mutableState.value.routines.firstOrNull { it.id == routineId }
            draft.copy(
                routineId = routineId,
                routineName = routine?.name,
                cardioTypeId = null,
                cardioTypeName = null,
                type = WeeklyPlanDayType.Strength,
                notificationEnabled = routineId != null && draft.notificationEnabled,
            )
        }
    }

    fun selectCardioType(dayOfWeek: Int, sessionId: String, cardioTypeId: String?) {
        updateSession(dayOfWeek, sessionId) { draft ->
            val cardioType = mutableState.value.cardioTypes.firstOrNull { it.id == cardioTypeId }
            draft.copy(
                type = WeeklyPlanDayType.Cardio,
                routineId = null,
                routineName = null,
                cardioTypeId = cardioTypeId,
                cardioTypeName = cardioType?.name,
                notificationEnabled = cardioTypeId != null && draft.notificationEnabled,
            )
        }
    }

    fun setSessionType(dayOfWeek: Int, sessionId: String, type: WeeklyPlanDayType) {
        updateSession(dayOfWeek, sessionId) { draft ->
            if (type == WeeklyPlanDayType.Cardio) {
                draft.copy(type = WeeklyPlanDayType.Cardio, routineId = null, routineName = null)
            } else {
                draft.copy(type = WeeklyPlanDayType.Strength, cardioTypeId = null, cardioTypeName = null)
            }
        }
    }

    fun setRestDay(dayOfWeek: Int, restDay: Boolean) {
        updateDay(dayOfWeek) { draft ->
            if (restDay) {
                draft.copy(
                    isRestDay = true,
                    sessions = emptyList(),
                )
            } else {
                draft.copy(isRestDay = false)
            }
        }
    }

    fun setNotificationEnabled(dayOfWeek: Int, sessionId: String, enabled: Boolean) {
        updateSession(dayOfWeek, sessionId) { it.copy(notificationEnabled = enabled) }
    }

    fun setNotificationTime(dayOfWeek: Int, sessionId: String, value: String) {
        updateSession(dayOfWeek, sessionId) { it.copy(notificationTime = value.filter { char -> char.isDigit() || char == ':' }.take(MAX_TIME_LENGTH)) }
    }

    fun setCardioTargetMinutes(dayOfWeek: Int, sessionId: String, value: String) {
        updateSession(dayOfWeek, sessionId) { it.copy(cardioTargetMinutes = value.onlyDigits().take(MAX_CARDIO_MINUTES_LENGTH)) }
    }

    fun saveDay(dayOfWeek: Int) {
        val draft = mutableState.value.days.firstOrNull { it.dayOfWeek == dayOfWeek } ?: return
        viewModelScope.launch {
            val saved = weeklyPlanUseCase.updateDay(
                WeeklyPlanUpdate(
                    dayOfWeek = draft.dayOfWeek,
                    isRestDay = draft.isRestDay,
                    sessions = draft.sessions.map { session ->
                        WeeklyPlanSessionUpdate(
                            id = session.id.takeUnless { it.startsWith("draft_") },
                            type = session.type,
                            routineId = session.routineId,
                            cardioTypeId = session.cardioTypeId,
                            cardioTargetDurationSec = session.cardioTargetSeconds,
                            notificationEnabled = session.notificationEnabled,
                            notificationTime = session.notificationTime,
                        )
                    },
                    notificationEnabled = false,
                    notificationTime = null,
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

    private fun updateSession(
        dayOfWeek: Int,
        sessionId: String,
        transform: (WeeklyPlanSessionDraft) -> WeeklyPlanSessionDraft,
    ) {
        updateDay(dayOfWeek) { day ->
            day.copy(
                sessions = day.sessions.map { session ->
                    if (session.id == sessionId) transform(session) else session
                },
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
    val isRestDay: Boolean,
    val completedThisWeek: Boolean,
    val sessions: List<WeeklyPlanSessionDraft>,
) {
    val plannedName: String? = sessions.joinToString(separator = " + ") { it.plannedName.orEmpty() }.takeIf { it.isNotBlank() }
}

data class WeeklyPlanSessionDraft(
    val id: String,
    val type: WeeklyPlanDayType,
    val routineId: String?,
    val routineName: String?,
    val cardioTypeId: String?,
    val cardioTypeName: String?,
    val cardioTargetMinutes: String,
    val notificationEnabled: Boolean,
    val notificationTime: String,
    val completedThisWeek: Boolean,
) {
    val cardioTargetSeconds: Int? = cardioTargetMinutes.toIntOrNull()?.coerceAtLeast(1)?.times(60)
    val plannedName: String? = if (type == WeeklyPlanDayType.Cardio) cardioTypeName else routineName
    val hasPlannedSession: Boolean = if (type == WeeklyPlanDayType.Cardio) cardioTypeId != null else routineId != null
}

private fun String.onlyDigits(): String = filter { it.isDigit() }
