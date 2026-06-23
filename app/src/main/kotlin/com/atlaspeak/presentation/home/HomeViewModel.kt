package com.atlaspeak.presentation.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.dashboard.DashboardWidget
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.usecase.dashboard.DashboardUseCase
import com.atlaspeak.domain.usecase.healthconnect.SyncHealthConnectUseCase
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
import com.atlaspeak.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dashboardUseCase: DashboardUseCase,
    private val weeklyPlanUseCase: WeeklyPlanUseCase,
    private val syncHealthConnectUseCase: SyncHealthConnectUseCase,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    private var refreshJob: Job? = null
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        syncHealthConnect()
        refresh()
    }

    fun selectPeriod(widget: DashboardWidget, period: DashboardPeriod) {
        mutableState.update { it.copy(filters = it.filters.withPeriod(widget, period)) }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val filters = mutableState.value.filters
            mutableState.update { it.copy(isLoading = it.snapshot == null, errorMessageRes = null) }
            try {
                val snapshot = dashboardUseCase.snapshot(filters)
                val todayWorkout = todayWorkout()
                val greetingName = profileRepository.getProfile()?.displayName
                mutableState.update {
                    if (it.filters == filters) {
                        it.copy(
                            isLoading = false,
                            snapshot = snapshot,
                            todayWorkout = todayWorkout,
                            greetingName = greetingName,
                            errorMessageRes = null,
                        )
                    } else {
                        it
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    if (it.filters == filters) {
                        it.copy(isLoading = false, errorMessageRes = R.string.error_generic)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun syncHealthConnect() {
        viewModelScope.launch {
            val result = syncHealthConnectUseCase()
            if (result.successful && (result.importedRecords > 0 || result.exportedRecords > 0)) {
                refresh()
            }
        }
    }

    private suspend fun todayWorkout(): TodayWorkoutUiState? {
        val today = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .dayOfWeek
            .value
        val day = weeklyPlanUseCase.plan().firstOrNull { it.dayOfWeek == today } ?: return null
        if (day.isRestDay) return null
        if (day.type == WeeklyPlanDayType.Cardio) {
            val cardioTypeId = day.cardioTypeId ?: return null
            val cardioTypeName = day.cardioTypeName ?: return null
            return TodayWorkoutUiState(
                type = TodayWorkoutType.Cardio,
                routineId = null,
                routineName = null,
                cardioTypeId = cardioTypeId,
                cardioTypeName = cardioTypeName,
                cardioTargetDurationSec = day.cardioTargetDurationSec,
                completed = day.completedThisWeek,
            )
        }
        if (day.routineId == null || day.routineName == null) return null
        return TodayWorkoutUiState(
            type = TodayWorkoutType.Strength,
            routineId = day.routineId,
            routineName = day.routineName,
            cardioTypeId = null,
            cardioTypeName = null,
            cardioTargetDurationSec = null,
            completed = day.completedThisWeek,
        )
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val filters: DashboardFilters = DashboardFilters(),
    val snapshot: DashboardSnapshot? = null,
    val todayWorkout: TodayWorkoutUiState? = null,
    val greetingName: String? = null,
    @StringRes val errorMessageRes: Int? = null,
)

data class TodayWorkoutUiState(
    val type: TodayWorkoutType,
    val routineId: String?,
    val routineName: String?,
    val cardioTypeId: String?,
    val cardioTypeName: String?,
    val cardioTargetDurationSec: Int?,
    val completed: Boolean,
) {
    val title: String = routineName ?: cardioTypeName.orEmpty()
}

enum class TodayWorkoutType {
    Strength,
    Cardio,
}

private fun DashboardFilters.withPeriod(
    widget: DashboardWidget,
    period: DashboardPeriod,
): DashboardFilters {
    return when (widget) {
        DashboardWidget.TotalVolume -> copy(totalVolumePeriod = period)
        DashboardWidget.Consistency -> copy(consistencyPeriod = period)
        DashboardWidget.BodyWeight -> copy(bodyWeightPeriod = period)
        DashboardWidget.DailySteps -> copy(dailyStepsPeriod = period)
        DashboardWidget.HeartRate -> copy(heartRatePeriod = period)
        DashboardWidget.Sleep -> copy(sleepPeriod = period)
        DashboardWidget.TotalActivity -> copy(totalActivityPeriod = period)
    }
}
