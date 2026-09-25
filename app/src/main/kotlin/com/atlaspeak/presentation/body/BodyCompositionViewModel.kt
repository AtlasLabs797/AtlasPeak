package com.atlaspeak.presentation.body

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.body.BodyCompositionInput
import com.atlaspeak.domain.model.body.BodyCompositionPeriod
import com.atlaspeak.domain.model.body.BodyCompositionSnapshot
import com.atlaspeak.domain.model.body.BodyMetric
import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.usecase.body.BodyCompositionUseCase
import com.atlaspeak.domain.usecase.body.BodyCompositionValidation
import com.atlaspeak.domain.usecase.healthconnect.SyncHealthConnectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BodyCompositionViewModel @Inject constructor(
    private val bodyCompositionUseCase: BodyCompositionUseCase,
    private val syncHealthConnectUseCase: SyncHealthConnectUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        BodyCompositionUiState(
            healthConnectPermissions = syncHealthConnectUseCase.requiredPermissions(),
        ),
    )
    val state: StateFlow<BodyCompositionUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun selectPeriod(period: BodyCompositionPeriod) {
        mutableState.update { it.copy(selectedPeriod = period) }
        refresh()
    }

    fun selectMetric(metric: BodyMetric) {
        mutableState.update { it.copy(selectedMetric = metric) }
    }

    fun toggleEntryForm() {
        mutableState.update {
            it.copy(
                isEntryFormVisible = !it.isEntryFormVisible,
                messageRes = null,
            )
        }
    }

    fun updateDraft(metric: BodyMetric, value: String) {
        mutableState.update {
            it.copy(
                draft = it.draft.withMetric(metric, value),
                messageRes = null,
            )
        }
    }

    fun saveDraft() {
        viewModelScope.launch {
            val draft = mutableState.value.draft
            if (!draft.isValidRaw()) {
                mutableState.update { it.copy(messageRes = R.string.body_entry_invalid) }
                return@launch
            }
            val saved = bodyCompositionUseCase.record(draft.toInput(System.currentTimeMillis()))
            if (saved) {
                mutableState.update {
                    it.copy(
                        draft = BodyCompositionDraft(),
                        isEntryFormVisible = false,
                        messageRes = R.string.body_entry_saved,
                    )
                }
                refresh()
            } else {
                mutableState.update { it.copy(messageRes = R.string.body_entry_invalid) }
            }
        }
    }

    fun syncHealthConnect() {
        if (mutableState.value.isHealthConnectSyncing) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isHealthConnectSyncing = true,
                    messageRes = R.string.body_health_syncing,
                )
            }
            val result = syncHealthConnectUseCase()
            val messageRes = when {
                result.availability == HealthConnectAvailability.Unavailable -> R.string.body_health_unavailable
                result.availability == HealthConnectAvailability.UpdateRequired -> R.string.body_health_update_required
                result.missingPermissions -> R.string.body_health_permissions_needed
                result.failed -> R.string.body_health_sync_failed
                else -> R.string.body_health_sync_success
            }
            mutableState.update {
                it.copy(
                    isHealthConnectSyncing = false,
                    messageRes = messageRes,
                )
            }
            if (result.successful || result.partiallySuccessful) refresh()
        }
    }

    fun markHealthConnectUnavailable() {
        mutableState.update { it.copy(messageRes = R.string.body_health_unavailable) }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = it.snapshot == null) }
            try {
                val period = mutableState.value.selectedPeriod
                val snapshot = bodyCompositionUseCase.snapshot(period)
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        snapshot = snapshot,
                        messageRes = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        messageRes = R.string.error_generic,
                    )
                }
            }
        }
    }
}

data class BodyCompositionUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: BodyCompositionPeriod = BodyCompositionPeriod.Month,
    val selectedMetric: BodyMetric = BodyMetric.Weight,
    val snapshot: BodyCompositionSnapshot? = null,
    val isEntryFormVisible: Boolean = false,
    val draft: BodyCompositionDraft = BodyCompositionDraft(),
    val isHealthConnectSyncing: Boolean = false,
    val healthConnectPermissions: Set<String> = emptySet(),
    @StringRes val messageRes: Int? = null,
)

data class BodyCompositionDraft(
    val weightKg: String = "",
    val bodyFatPercent: String = "",
    val muscleMassKg: String = "",
    val waterPercent: String = "",
    val bodyWaterMassKg: String = "",
    val visceralFatLevel: String = "",
    val proteinPercent: String = "",
    val boneMassKg: String = "",
    val bodyAge: String = "",
) {
    fun withMetric(metric: BodyMetric, value: String): BodyCompositionDraft {
        return when (metric) {
            BodyMetric.Weight -> copy(weightKg = value)
            BodyMetric.BodyFat -> copy(bodyFatPercent = value)
            BodyMetric.MuscleMass -> copy(muscleMassKg = value)
            BodyMetric.Water -> copy(waterPercent = value)
            BodyMetric.BodyWaterMass -> copy(bodyWaterMassKg = value)
            BodyMetric.VisceralFat -> copy(visceralFatLevel = value)
            BodyMetric.Protein -> copy(proteinPercent = value)
            BodyMetric.BoneMass -> copy(boneMassKg = value)
            BodyMetric.BodyAge -> copy(bodyAge = value)
        }
    }

    fun toInput(now: Long): BodyCompositionInput {
        return BodyCompositionInput(
            measuredAt = now,
            weightKg = weightKg.toDoubleOrNullFlexible(),
            bodyFatPercent = bodyFatPercent.toDoubleOrNullFlexible(),
            muscleMassKg = muscleMassKg.toDoubleOrNullFlexible(),
            waterPercent = waterPercent.toDoubleOrNullFlexible(),
            bodyWaterMassKg = bodyWaterMassKg.toDoubleOrNullFlexible(),
            visceralFatLevel = visceralFatLevel.toIntOrNullFlexible(),
            proteinPercent = proteinPercent.toDoubleOrNullFlexible(),
            boneMassKg = boneMassKg.toDoubleOrNullFlexible(),
            bodyAge = bodyAge.toIntOrNullFlexible(),
        )
    }

    fun value(metric: BodyMetric): String {
        return when (metric) {
            BodyMetric.Weight -> weightKg
            BodyMetric.BodyFat -> bodyFatPercent
            BodyMetric.MuscleMass -> muscleMassKg
            BodyMetric.Water -> waterPercent
            BodyMetric.BodyWaterMass -> bodyWaterMassKg
            BodyMetric.VisceralFat -> visceralFatLevel
            BodyMetric.Protein -> proteinPercent
            BodyMetric.BoneMass -> boneMassKg
            BodyMetric.BodyAge -> bodyAge
        }
    }

    fun isValidRaw(): Boolean {
        // BUG-099 (Fase 10 P2): estos rangos vivian duplicados aqui (bodyAge
        // hasta 130, muscle/water/bone mass hasta 500) y divergian de los
        // que usa `BodyCompositionUseCase`. Ahora ambos pasan por
        // `BodyCompositionValidation`, la fuente de verdad unica.
        val rawValues = BodyMetric.entries.map { value(it).trim() }
        if (rawValues.none { it.isNotBlank() }) return false
        return BodyMetric.entries.all { metric ->
            val value = value(metric).trim()
            value.isBlank() || when (metric) {
                BodyMetric.VisceralFat ->
                    BodyCompositionValidation.visceralFatIsValid(value.toIntOrNullFlexible())
                BodyMetric.BodyAge ->
                    BodyCompositionValidation.bodyAgeIsValid(value.toIntOrNullFlexible())
                BodyMetric.Weight ->
                    BodyCompositionValidation.weightIsValid(value.toDoubleOrNullFlexible())
                BodyMetric.BodyFat,
                BodyMetric.Water,
                BodyMetric.Protein ->
                    BodyCompositionValidation.percentIsValid(value.toDoubleOrNullFlexible())
                BodyMetric.MuscleMass ->
                    BodyCompositionValidation.muscleMassIsValid(value.toDoubleOrNullFlexible())
                BodyMetric.BodyWaterMass ->
                    BodyCompositionValidation.bodyWaterMassIsValid(value.toDoubleOrNullFlexible())
                BodyMetric.BoneMass ->
                    BodyCompositionValidation.boneMassIsValid(value.toDoubleOrNullFlexible())
            }
        }
    }
}

private fun String.toDoubleOrNullFlexible(): Double? {
    return trim().replace(',', '.').takeIf { it.isNotBlank() }?.toDoubleOrNull()
}

private fun String.toIntOrNullFlexible(): Int? {
    return trim().takeIf { it.isNotBlank() }?.toIntOrNull()
}
