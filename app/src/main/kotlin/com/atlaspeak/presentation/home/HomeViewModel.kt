package com.atlaspeak.presentation.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.dashboard.DashboardWidget
import com.atlaspeak.domain.usecase.dashboard.DashboardUseCase
import com.atlaspeak.domain.usecase.healthconnect.SyncHealthConnectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val syncHealthConnectUseCase: SyncHealthConnectUseCase,
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

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val filters = mutableState.value.filters
            mutableState.update { it.copy(isLoading = it.snapshot == null, errorMessageRes = null) }
            try {
                val snapshot = dashboardUseCase.snapshot(filters)
                mutableState.update {
                    if (it.filters == filters) {
                        it.copy(
                            isLoading = false,
                            snapshot = snapshot,
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
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val filters: DashboardFilters = DashboardFilters(),
    val snapshot: DashboardSnapshot? = null,
    @StringRes val errorMessageRes: Int? = null,
)

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
