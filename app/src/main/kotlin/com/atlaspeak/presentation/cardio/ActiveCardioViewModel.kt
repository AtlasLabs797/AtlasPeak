package com.atlaspeak.presentation.cardio

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.presentation.navigation.AppRoute
import com.atlaspeak.service.CardioForegroundService
import com.atlaspeak.service.CardioTrackerRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ActiveCardioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardioUseCase: CardioUseCase,
    private val cardioRepository: CardioRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val cardioTypeId: String = requireNotNull(savedStateHandle[AppRoute.ActiveCardio.CARDIO_TYPE_ID])
    private val mode: CardioMode = when (savedStateHandle.get<String>(AppRoute.ActiveCardio.MODE)) {
        AppRoute.ActiveCardio.MODE_COUNTDOWN -> CardioMode.Countdown(
            requireNotNull(savedStateHandle[AppRoute.ActiveCardio.TARGET_SECONDS]),
        )
        else -> CardioMode.Timer
    }
    private val mutableState = MutableStateFlow(ActiveCardioUiState(mode = mode))
    val state: StateFlow<ActiveCardioUiState> = mutableState.asStateFlow()
    private var localTimerJob: Job? = null

    init {
        startCardio()
        viewModelScope.launch {
            CardioTrackerRegistry.state.collect { tracker ->
                mutableState.update { current ->
                    val sessionId = current.session?.id
                    when {
                        tracker.failed && tracker.sessionId == sessionId -> {
                            current.copy(message = ActiveCardioMessage.TrackerUnavailable)
                        }
                        tracker.sessionId == sessionId -> current.copy(
                            elapsedSeconds = tracker.elapsedSeconds,
                            distanceKm = tracker.distanceKm,
                            currentSpeedKmh = tracker.currentSpeedKmh,
                            route = tracker.route,
                        )
                        else -> current
                    }
                }
                if (shouldAutoComplete(tracker.remainingSeconds)) {
                    completeCardio()
                }
                if (tracker.failed && tracker.sessionId == mutableState.value.session?.id) {
                    startLocalTimerIfNeeded()
                }
            }
        }
    }

    fun startTrackingService(locationAllowed: Boolean) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.trackerServiceStartHandled) return
        val gpsEnabled = session.hasGps && locationAllowed
        if (!gpsEnabled) {
            mutableState.update {
                it.copy(
                    trackerServiceStartHandled = true,
                    message = if (session.hasGps && !locationAllowed) {
                        ActiveCardioMessage.LocationPermissionDenied
                    } else {
                        it.message
                    },
                )
            }
            startLocalTimerIfNeeded()
            return
        }
        mutableState.update {
            it.copy(
                trackerServiceStartHandled = true,
                message = null,
            )
        }
        try {
            ContextCompat.startForegroundService(
                context,
                CardioForegroundService.startIntent(context, session.id, session.startTime, gpsEnabled, session.mode),
            )
        } catch (_: RuntimeException) {
            mutableState.update { it.copy(message = ActiveCardioMessage.TrackerUnavailable) }
            startLocalTimerIfNeeded()
        }
    }

    fun onManualDistanceChanged(value: String) {
        mutableState.update { it.copy(manualDistanceKm = value.decimalInput(), message = null) }
    }

    fun onManualSpeedChanged(value: String) {
        mutableState.update { it.copy(manualAvgSpeedKmh = value.decimalInput(), message = null) }
    }

    fun completeCardio() {
        val snapshot = mutableState.value
        val session = snapshot.session ?: return
        if (snapshot.completionInProgress || snapshot.completedSessionId != null) return
        if (snapshot.requiresManualMetrics && !snapshot.hasValidManualMetrics) {
            mutableState.update { it.copy(message = ActiveCardioMessage.ManualMetricsRequired) }
            return
        }
        mutableState.update { it.copy(completionInProgress = true) }
        viewModelScope.launch {
            val manualDistance = snapshot.manualDistanceKm.toDoubleOrNull()
            val manualSpeed = snapshot.manualAvgSpeedKmh.toDoubleOrNull()
            val completed = cardioUseCase.completeSession(
                sessionId = session.id,
                manualDistanceKm = manualDistance,
                manualAvgSpeedKmh = manualSpeed,
                route = snapshot.route,
            )
            context.stopService(CardioForegroundService.stopIntent(context))
            stopLocalTimer()
            mutableState.update {
                it.copy(
                    completionInProgress = false,
                    completedSessionId = completed?.id,
                    message = if (completed == null) ActiveCardioMessage.ManualMetricsRequired else it.message,
                )
            }
        }
    }

    fun cancelCardio() {
        val sessionId = mutableState.value.session?.id
        context.stopService(CardioForegroundService.stopIntent(context))
        stopLocalTimer()
        viewModelScope.launch {
            if (sessionId != null) {
                cardioRepository.deleteSession(sessionId)
            }
            mutableState.update { it.copy(cancelled = true) }
        }
    }

    private fun startCardio() {
        viewModelScope.launch {
            val sessionId = cardioUseCase.startSession(cardioTypeId, mode)
            if (sessionId == null) {
                mutableState.update { it.copy(isLoading = false, message = ActiveCardioMessage.SessionMissing) }
                return@launch
            }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    session = cardioRepository.session(sessionId),
                )
            }
        }
    }

    private fun shouldAutoComplete(remainingSeconds: Long?): Boolean {
        val snapshot = mutableState.value
        return snapshot.mode is CardioMode.Countdown &&
            remainingSeconds == 0L &&
            snapshot.elapsedSeconds > 0L &&
            !snapshot.completionInProgress &&
            snapshot.completedSessionId == null
    }

    private fun startLocalTimerIfNeeded() {
        val session = mutableState.value.session ?: return
        if (localTimerJob?.isActive == true) return
        localTimerJob = viewModelScope.launch {
            while (true) {
                val elapsed = ((System.currentTimeMillis() - session.startTime) / 1000).coerceAtLeast(0)
                mutableState.update { it.copy(elapsedSeconds = elapsed) }
                if (shouldAutoComplete(mutableState.value.remainingSeconds)) {
                    completeCardio()
                }
                delay(1000)
            }
        }
    }

    private fun stopLocalTimer() {
        localTimerJob?.cancel()
        localTimerJob = null
    }

    override fun onCleared() {
        stopLocalTimer()
        super.onCleared()
    }
}

data class ActiveCardioUiState(
    val isLoading: Boolean = true,
    val session: CardioSession? = null,
    val mode: CardioMode,
    val elapsedSeconds: Long = 0L,
    val distanceKm: Double = 0.0,
    val currentSpeedKmh: Double? = null,
    val route: List<LocationPoint> = emptyList(),
    val manualDistanceKm: String = "",
    val manualAvgSpeedKmh: String = "",
    val trackerServiceStartHandled: Boolean = false,
    val completionInProgress: Boolean = false,
    val completedSessionId: String? = null,
    val cancelled: Boolean = false,
    val message: ActiveCardioMessage? = null,
) {
    val requiresManualMetrics: Boolean = session?.hasGps != true || route.isEmpty()
    val hasValidManualMetrics: Boolean =
        (manualDistanceKm.toDoubleOrNull()?.let { it > 0.0 } == true) &&
            (manualAvgSpeedKmh.toDoubleOrNull()?.let { it > 0.0 } == true)
    val canComplete: Boolean = !requiresManualMetrics || hasValidManualMetrics

    val averageSpeedKmh: Double? = if (elapsedSeconds > 0 && distanceKm > 0.0) {
        distanceKm / (elapsedSeconds / 3600.0)
    } else {
        null
    }

    val remainingSeconds: Long? = (mode as? CardioMode.Countdown)
        ?.targetDurationSeconds
        ?.let { (it - elapsedSeconds).coerceAtLeast(0) }
}

enum class ActiveCardioMessage {
    SessionMissing,
    LocationPermissionDenied,
    TrackerUnavailable,
    ManualMetricsRequired,
}

private fun String.decimalInput(): String {
    val builder = StringBuilder()
    var dotSeen = false
    for (char in this) {
        when {
            char.isDigit() -> builder.append(char)
            (char == '.' || char == ',') && !dotSeen -> {
                builder.append('.')
                dotSeen = true
            }
        }
    }
    return builder.toString().take(6)
}
