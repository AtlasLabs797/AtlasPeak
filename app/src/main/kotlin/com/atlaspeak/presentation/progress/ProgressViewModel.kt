package com.atlaspeak.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.progress.ExerciseProgress
import com.atlaspeak.domain.model.progress.MuscleGroupProgress
import com.atlaspeak.domain.model.progress.ProgressHistoryFilter
import com.atlaspeak.domain.model.progress.ProgressHistoryItem
import com.atlaspeak.domain.model.progress.ProgressHistoryType
import com.atlaspeak.domain.model.progress.ProgressPeriod
import com.atlaspeak.domain.usecase.progress.ProgressUseCase
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
class ProgressViewModel @Inject constructor(
    private val progressUseCase: ProgressUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProgressUiState())
    private var allRefreshJob: Job? = null
    private var historyRefreshJob: Job? = null
    private var historySearchJob: Job? = null
    val state: StateFlow<ProgressUiState> = mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun selectTab(tab: ProgressTab) {
        mutableState.update { it.copy(selectedTab = tab) }
    }

    fun selectPeriod(period: ProgressPeriod) {
        if (mutableState.value.selectedPeriod == period) return
        mutableState.update {
            it.copy(
                selectedPeriod = period,
                selectedHistoryId = null,
                errorMessageRes = null,
            )
        }
        refreshAll()
    }

    fun selectHistoryType(type: ProgressHistoryType) {
        if (mutableState.value.selectedHistoryType == type) return
        mutableState.update {
            it.copy(
                selectedHistoryType = type,
                selectedHistoryId = null,
                errorMessageRes = null,
            )
        }
        refreshHistory()
    }

    fun onHistorySearchChanged(query: String) {
        if (mutableState.value.historyQuery == query) return
        mutableState.update {
            it.copy(
                historyQuery = query,
                selectedHistoryId = null,
                errorMessageRes = null,
            )
        }
        // BUG-101 (Fase 12 P2): antes `refreshHistory()` se lanzaba en cada
        // pulsacion de tecla, generando flicker y consultas Room innecesarias.
        // Ahora el `historySearchJob` espera 300 ms de inactividad antes de
        // llamar al use case; un cambio rapido cancela el job anterior y solo
        // el ultimo query llega a la query.
        historySearchJob?.cancel()
        historySearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            refreshHistory()
        }
    }

    fun selectHistoryItem(id: String) {
        mutableState.update { it.copy(selectedHistoryId = id) }
    }

    private fun refreshAll() {
        allRefreshJob?.cancel()
        historyRefreshJob?.cancel()
        allRefreshJob = viewModelScope.launch {
            val snapshot = mutableState.value
            mutableState.update {
                it.copy(
                    isLoading = it.hasNoLoadedContent(),
                    errorMessageRes = null,
                )
            }
            try {
                val history = loadHistory(snapshot)
                val exercises = progressUseCase.exerciseProgress(snapshot.selectedPeriod)
                val muscleGroups = progressUseCase.muscleGroupProgress(snapshot.selectedPeriod)
                mutableState.update {
                    if (it.selectedPeriod != snapshot.selectedPeriod) return@update it
                    val shouldApplyHistory = it.matches(snapshot)
                    val appliedHistory = if (shouldApplyHistory) history else it.history
                    val selectedHistoryId = it.selectedHistoryId?.takeIf { id -> appliedHistory.any { item -> item.id == id } }
                    it.copy(
                        isLoading = false,
                        history = appliedHistory,
                        exerciseProgress = exercises,
                        muscleGroupProgress = muscleGroups,
                        selectedHistoryId = selectedHistoryId,
                        errorMessageRes = if (shouldApplyHistory) null else it.errorMessageRes,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    if (!it.matches(snapshot)) return@update it
                    it.copy(
                        isLoading = false,
                        errorMessageRes = R.string.error_generic,
                    )
                }
            }
        }
    }

    private fun refreshHistory() {
        historyRefreshJob?.cancel()
        historyRefreshJob = viewModelScope.launch {
            val snapshot = mutableState.value
            try {
                val history = loadHistory(snapshot)
                mutableState.update {
                    if (!it.matches(snapshot)) return@update it
                    val selectedHistoryId = it.selectedHistoryId?.takeIf { id -> history.any { item -> item.id == id } }
                    it.copy(
                        isLoading = false,
                        history = history,
                        selectedHistoryId = selectedHistoryId,
                        errorMessageRes = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    if (!it.matches(snapshot)) return@update it
                    it.copy(
                        isLoading = false,
                        errorMessageRes = R.string.error_generic,
                    )
                }
            }
        }
    }

    private fun ProgressUiState.matches(snapshot: ProgressUiState): Boolean {
        return selectedPeriod == snapshot.selectedPeriod &&
            selectedHistoryType == snapshot.selectedHistoryType &&
            historyQuery == snapshot.historyQuery
    }

    private fun ProgressUiState.hasNoLoadedContent(): Boolean {
        return history.isEmpty() && exerciseProgress.isEmpty() && muscleGroupProgress.isEmpty()
    }

    private suspend fun loadHistory(snapshot: ProgressUiState): List<ProgressHistoryItem> {
        return progressUseCase.history(
            query = snapshot.historyQuery,
            filter = ProgressHistoryFilter(
                type = snapshot.selectedHistoryType,
                period = snapshot.selectedPeriod,
            ),
        )
    }
}

data class ProgressUiState(
    val isLoading: Boolean = true,
    val selectedTab: ProgressTab = ProgressTab.History,
    val selectedPeriod: ProgressPeriod = ProgressPeriod.Month,
    val selectedHistoryType: ProgressHistoryType = ProgressHistoryType.All,
    val historyQuery: String = "",
    val history: List<ProgressHistoryItem> = emptyList(),
    val exerciseProgress: List<ExerciseProgress> = emptyList(),
    val muscleGroupProgress: List<MuscleGroupProgress> = emptyList(),
    val selectedHistoryId: String? = null,
    @androidx.annotation.StringRes val errorMessageRes: Int? = null,
) {
    val selectedHistoryItem: ProgressHistoryItem? = history.firstOrNull { it.id == selectedHistoryId }
}

enum class ProgressTab {
    History,
    Exercises,
    MuscleGroups,
}

// BUG-101 (Fase 12 P2): debounce de la busqueda en historial. 300 ms es
// suficiente para evitar flicker en busquedas tipadas rapidamente sin
// hacer sentir al usuario que hay lag.
private const val SEARCH_DEBOUNCE_MS: Long = 300L
