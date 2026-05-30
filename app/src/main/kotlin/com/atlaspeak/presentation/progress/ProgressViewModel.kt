package com.atlaspeak.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.progress.ExerciseProgress
import com.atlaspeak.domain.model.progress.MuscleGroupProgress
import com.atlaspeak.domain.model.progress.ProgressHistoryFilter
import com.atlaspeak.domain.model.progress.ProgressHistoryItem
import com.atlaspeak.domain.model.progress.ProgressHistoryType
import com.atlaspeak.domain.model.progress.ProgressPeriod
import com.atlaspeak.domain.usecase.progress.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val state: StateFlow<ProgressUiState> = mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun selectTab(tab: ProgressTab) {
        mutableState.update { it.copy(selectedTab = tab) }
    }

    fun selectPeriod(period: ProgressPeriod) {
        mutableState.update { it.copy(selectedPeriod = period, selectedHistoryId = null) }
        refreshAll()
    }

    fun selectHistoryType(type: ProgressHistoryType) {
        mutableState.update { it.copy(selectedHistoryType = type, selectedHistoryId = null) }
        refreshHistory()
    }

    fun onHistorySearchChanged(query: String) {
        mutableState.update { it.copy(historyQuery = query, selectedHistoryId = null) }
        refreshHistory()
    }

    fun selectHistoryItem(id: String) {
        mutableState.update { it.copy(selectedHistoryId = id) }
    }

    private fun refreshAll() {
        viewModelScope.launch {
            val snapshot = mutableState.value
            val history = loadHistory(snapshot)
            val exercises = progressUseCase.exerciseProgress(snapshot.selectedPeriod)
            val muscleGroups = progressUseCase.muscleGroupProgress(snapshot.selectedPeriod)
            mutableState.update {
                val selectedHistoryId = it.selectedHistoryId?.takeIf { id -> history.any { item -> item.id == id } }
                it.copy(
                    isLoading = false,
                    history = history,
                    exerciseProgress = exercises,
                    muscleGroupProgress = muscleGroups,
                    selectedHistoryId = selectedHistoryId,
                )
            }
        }
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            val history = loadHistory(mutableState.value)
            mutableState.update {
                val selectedHistoryId = it.selectedHistoryId?.takeIf { id -> history.any { item -> item.id == id } }
                it.copy(
                    isLoading = false,
                    history = history,
                    selectedHistoryId = selectedHistoryId,
                )
            }
        }
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
) {
    val selectedHistoryItem: ProgressHistoryItem? = history.firstOrNull { it.id == selectedHistoryId }
}

enum class ProgressTab {
    History,
    Exercises,
    MuscleGroups,
}
