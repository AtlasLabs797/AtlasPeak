package com.atlaspeak.presentation.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.model.dashboard.DashboardPeriod
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.dashboard.DashboardWidget
import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.model.planning.WeeklyPlanDayType
import com.atlaspeak.domain.usecase.dashboard.DashboardUseCase
import com.atlaspeak.domain.usecase.healthconnect.SyncHealthConnectUseCase
import com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCase
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.repository.WorkoutRepository
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
    private val workoutRepository: WorkoutRepository,
    private val cardioRepository: CardioRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    private var refreshJob: Job? = null
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        refresh(syncBefore = true)
        loadActiveSessionShortcut()
    }

    /**
     * BUG-103 (Fase 16 P3): quick action "Continuar sesion activa" cuando
     * hay una sesion de fuerza o cardio abierta. Se consulta al repositorio
     * de workout/cardio y se proyecta al UiState. El refresh posterior (en
     * `refresh()`) reescribe `activeSessionShortcut` para mantenerlo
     * sincronizado.
     */
    private fun loadActiveSessionShortcut() {
        viewModelScope.launch {
            val activeStrength = runCatching { workoutRepository.findActiveSession() }.getOrNull()
            val activeCardio = runCatching { cardioRepository.findActiveSession() }.getOrNull()
            val shortcut: ActiveSessionShortcut? = when {
                activeStrength != null -> ActiveSessionShortcut.Strength(
                    sessionId = activeStrength.id,
                    routineName = activeStrength.routineName.orEmpty(),
                )
                activeCardio != null -> ActiveSessionShortcut.Cardio(
                    sessionId = activeCardio.id,
                    cardioTypeName = activeCardio.cardioTypeName,
                )
                else -> null
            }
            mutableState.update { it.copy(activeSessionShortcut = shortcut) }
        }
    }

    fun selectPeriod(widget: DashboardWidget, period: DashboardPeriod) {
        mutableState.update { it.copy(filters = it.filters.withPeriod(widget, period)) }
        refresh()
    }

    fun refresh(syncBefore: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val filters = mutableState.value.filters
            mutableState.update {
                it.copy(
                    isLoading = it.snapshot == null,
                    errorMessageRes = null,
                    healthConnectSync = if (syncBefore) HomeHealthConnectSync.Syncing else it.healthConnectSync,
                )
            }
            try {
                if (syncBefore) {
                    val syncResult = runCatching { syncHealthConnectUseCase() }.getOrNull()
                    val mapped = syncResult.toHomeSyncStatus(now())
                    mutableState.update {
                        if (it.filters == filters) it.copy(healthConnectSync = mapped) else it
                    }
                }
                val snapshot = dashboardUseCase.snapshot(filters)
                val todayWorkouts = todayWorkouts()
                val greetingName = profileRepository.getProfile()?.displayName
                mutableState.update {
                    if (it.filters == filters) {
                        it.copy(
                            isLoading = false,
                            snapshot = snapshot,
                            todayWorkouts = todayWorkouts,
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

    fun dismissHealthConnectSyncStatus() {
        mutableState.update { it.copy(healthConnectSync = HomeHealthConnectSync.Idle) }
    }

    fun requiredHealthConnectPermissions(): Set<String> = syncHealthConnectUseCase.requiredPermissions()

    private suspend fun todayWorkouts(): List<TodayWorkoutUiState> {
        val today = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .dayOfWeek
            .value
        val day = weeklyPlanUseCase.plan().firstOrNull { it.dayOfWeek == today } ?: return emptyList()
        if (day.isRestDay) return emptyList()
        return day.sessions.mapNotNull { session ->
            if (session.type == WeeklyPlanDayType.Cardio) {
                val cardioTypeId = session.cardioTypeId ?: return@mapNotNull null
                val cardioTypeName = session.cardioTypeName ?: return@mapNotNull null
                val targetSeconds = session.cardioTargetDurationSec
                TodayWorkoutUiState(
                    weeklyPlanSessionId = session.id,
                    orderIndex = session.orderIndex,
                    type = TodayWorkoutType.Cardio,
                    routineId = null,
                    routineName = null,
                    cardioTypeId = cardioTypeId,
                    cardioTypeName = cardioTypeName,
                    cardioTargetDurationSec = targetSeconds,
                    completed = session.completedThisWeek,
                    canStart = targetSeconds != null && targetSeconds >= MIN_CARDIO_TARGET_SECONDS,
                    statusMessageRes = if (targetSeconds == null || targetSeconds < MIN_CARDIO_TARGET_SECONDS) {
                        R.string.home_today_cardio_incomplete
                    } else {
                        null
                    },
                )
            } else {
                if (session.routineId == null || session.routineName == null) return@mapNotNull null
                TodayWorkoutUiState(
                    weeklyPlanSessionId = session.id,
                    orderIndex = session.orderIndex,
                    type = TodayWorkoutType.Strength,
                    routineId = session.routineId,
                    routineName = session.routineName,
                    cardioTypeId = null,
                    cardioTypeName = null,
                    cardioTargetDurationSec = null,
                    completed = session.completedThisWeek,
                    canStart = true,
                    statusMessageRes = null,
                )
            }
        }
    }

    private companion object {
        const val MIN_CARDIO_TARGET_SECONDS = 60
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val filters: DashboardFilters = DashboardFilters(),
    val snapshot: DashboardSnapshot? = null,
    val todayWorkouts: List<TodayWorkoutUiState> = emptyList(),
    val greetingName: String? = null,
    @StringRes val errorMessageRes: Int? = null,
    /**
     * BUG-095 (Fase 6 P1): estado de sincronizacion de Health Connect para que
     * la UI pueda mostrar si los datos estan al dia, faltan
     * permisos, o el sistema rechazo la sincronizacion. Antes el resultado de
     * `syncHealthConnectUseCase()` se descartaba en `runCatching {}` y el
     * usuario podia ver metricas antiguas sin saber que la sincronizacion
     * fallo.
     */
    val healthConnectSync: HomeHealthConnectSync = HomeHealthConnectSync.Idle,
    /**
     * BUG-103 (Fase 16 P3): quick action "Continuar sesion activa" en Home
     * cuando hay una sesion de fuerza o cardio abierta. El VM expone el tipo
     * y el id para que la UI pueda navegar directamente.
     */
    val activeSessionShortcut: ActiveSessionShortcut? = null,
) {
    val todayWorkout: TodayWorkoutUiState? = todayWorkouts.firstOrNull()
}

sealed class ActiveSessionShortcut {
    data class Strength(val sessionId: String, val routineName: String) : ActiveSessionShortcut()
    data class Cardio(val sessionId: String, val cardioTypeName: String) : ActiveSessionShortcut()
}

/**
 * Estado de sincronizacion de Health Connect proyectado al Home. BUG-095.
 * Mapea el `HealthConnectSyncResult` del caso de uso a algo que la UI sabe
 * pintar: exito parcial, faltan permisos, requiere actualizacion, fallo.
 */
sealed class HomeHealthConnectSync {
    data object Idle : HomeHealthConnectSync()
    data object Syncing : HomeHealthConnectSync()
    data class Success(val timestampMillis: Long) : HomeHealthConnectSync()
    data class PartialSuccess(val timestampMillis: Long) : HomeHealthConnectSync()
    data object MissingPermissions : HomeHealthConnectSync()
    data object UpdateRequired : HomeHealthConnectSync()
    data object Unavailable : HomeHealthConnectSync()
    data class Failed(val timestampMillis: Long) : HomeHealthConnectSync()
}

private fun HealthConnectSyncResult?.toHomeSyncStatus(now: Long): HomeHealthConnectSync {
    if (this == null) return HomeHealthConnectSync.Failed(now)
    return when {
        availability == HealthConnectAvailability.Unavailable -> HomeHealthConnectSync.Unavailable
        availability == HealthConnectAvailability.UpdateRequired -> HomeHealthConnectSync.UpdateRequired
        missingPermissions -> HomeHealthConnectSync.MissingPermissions
        partiallySuccessful -> HomeHealthConnectSync.PartialSuccess(now)
        successful -> HomeHealthConnectSync.Success(now)
        failed -> HomeHealthConnectSync.Failed(now)
        else -> HomeHealthConnectSync.Idle
    }
}

data class TodayWorkoutUiState(
    val orderIndex: Int,
    val weeklyPlanSessionId: String? = null,
    val type: TodayWorkoutType,
    val routineId: String?,
    val routineName: String?,
    val cardioTypeId: String?,
    val cardioTypeName: String?,
    val cardioTargetDurationSec: Int?,
    val completed: Boolean,
    val canStart: Boolean,
    @StringRes val statusMessageRes: Int?,
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
