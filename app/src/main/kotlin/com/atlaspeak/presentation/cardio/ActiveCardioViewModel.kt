package com.atlaspeak.presentation.cardio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.cardio.CardioFgsMode
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.model.cardio.effectiveElapsedSeconds
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.domain.usecase.cardio.ResumeCardioSessionUseCase
import com.atlaspeak.domain.usecase.workout.ActiveSessionStartResult
import com.atlaspeak.presentation.navigation.AppRoute
import com.atlaspeak.service.CardioForegroundService
import com.atlaspeak.service.CardioTrackerRegistry
import com.atlaspeak.service.CardioTrackerState
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
class ActiveCardioViewModel(
    savedStateHandle: SavedStateHandle,
    private val cardioUseCase: CardioUseCase,
    private val resumeCardioSessionUseCase: ResumeCardioSessionUseCase,
    private val cardioRepository: CardioRepository,
    @ApplicationContext private val context: Context,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        cardioUseCase: CardioUseCase,
        resumeCardioSessionUseCase: ResumeCardioSessionUseCase,
        cardioRepository: CardioRepository,
        @ApplicationContext context: Context,
    ) : this(
        savedStateHandle = savedStateHandle,
        cardioUseCase = cardioUseCase,
        resumeCardioSessionUseCase = resumeCardioSessionUseCase,
        cardioRepository = cardioRepository,
        context = context,
        now = { System.currentTimeMillis() },
    )

    private val cardioTypeId: String = requireNotNull(savedStateHandle[AppRoute.ActiveCardio.CARDIO_TYPE_ID])
    private val weeklyPlanSessionId: String? = savedStateHandle[AppRoute.ActiveCardio.WEEKLY_PLAN_SESSION_ID]
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
                            // BUG-093 (Fase 4 P0): si el FGS falla para nuestra
                            // sesion, el modo efectivo pasa a None: la UI debe
                            // mostrar la nota de "modo local" si estaba esperando
                            // Location/Health.
                            current.copy(
                                message = ActiveCardioMessage.TrackerUnavailable,
                                fgsMode = CardioFgsMode.None,
                            )
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
                if (!tracker.failed && tracker.running && tracker.sessionId == mutableState.value.session?.id) {
                    stopLocalTimer()
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
        // BUG-093 (Fase 4 P0): calculamos el modo FGS pedido antes de llamar
        // a startForegroundService. La UI usa este valor para etiquetar el
        // escenario; si el startForegroundService falla despues, lo rebajamos
        // a None en el catch.
        val hasActivityRecognition = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
        val requestedFgsMode = CardioForegroundService.preflightFgsType(
            hasGps = session.hasGps,
            hasFineLocation = locationAllowed,
            hasActivityRecognition = hasActivityRecognition,
        )
        mutableState.update {
            it.copy(
                trackerServiceStartHandled = true,
                fgsMode = requestedFgsMode,
                message = if (session.hasGps && !locationAllowed) {
                    ActiveCardioMessage.LocationPermissionDenied
                } else {
                    null
                },
            )
        }

        // Si el preflight ya sabe que no hay un tipo de FGS legal, no se debe
        // llamar a startForegroundService(): Android exige promocionar ese
        // servicio con startForeground() en pocos segundos. En modo None el
        // cronometro queda en el ViewModel y evitamos crear un servicio que el
        // sistema terminaria por no entrar en foreground.
        if (requestedFgsMode == CardioFgsMode.None) {
            startLocalTimerIfNeeded()
            return
        }

        try {
            ContextCompat.startForegroundService(
                context,
                CardioForegroundService.startIntent(context, session.id, session.startTime, gpsEnabled, session.mode),
            )
        } catch (_: SecurityException) {
            // BUG-093: SecurityException explicito (subclase de
            // RuntimeException, pero mas claro para el lector). Android 14+
            // puede lanzar esto al reclamar un tipo de FGS que la politica
            // del sistema no admite.
            mutableState.update {
                it.copy(
                    fgsMode = CardioFgsMode.None,
                    message = ActiveCardioMessage.TrackerUnavailable,
                )
            }
            startLocalTimerIfNeeded()
        } catch (_: RuntimeException) {
            mutableState.update {
                it.copy(
                    fgsMode = CardioFgsMode.None,
                    message = ActiveCardioMessage.TrackerUnavailable,
                )
            }
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
            context.stopService(CardioForegroundService.stopIntent(context))
            stopLocalTimer()
            val completed = cardioUseCase.completeSession(
                sessionId = session.id,
                manualDistanceKm = manualDistance,
                manualAvgSpeedKmh = manualSpeed,
            )
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

    /**
     * Continua con la sesion de cardio activa ignorando el tipo solicitado.
     * Usado por el boton "Continuar entrenamiento" del dialogo de conflicto.
     */
    fun resumeActiveSession() {
        viewModelScope.launch {
            val sessionId = resumeCardioSessionUseCase() ?: run {
                startCardio()
                return@launch
            }
            loadSession(sessionId)
        }
    }

    /**
     * Borra la sesion de cardio activa y arranca una nueva para el tipo solicitado.
     */
    fun discardActiveSessionAndStartNew() {
        if (mutableState.value.discardInProgress) return
        mutableState.update { it.copy(discardInProgress = true) }
        viewModelScope.launch {
            val activeId = cardioRepository.findActiveSession()?.id
            if (activeId != null) {
                cardioRepository.deleteSession(activeId)
                // El servicio tracker de la sesion anterior esta en primer plano;
                // hay que pararlo para no dejar una notificacion zombi.
                runCatching {
                    context.stopService(CardioForegroundService.stopIntent(context))
                }
            }
            // Esperamos a que la nueva sesion se haya cargado para que el boton siga
            // deshabilitado durante todo el ciclo y no se pueda re-disparar en una carrera.
            startCardio().join()
            mutableState.update { it.copy(discardInProgress = false) }
        }
    }

    /** Cierra el dialogo de conflicto sin tocar nada. */
    fun dismissActiveSessionConflict() {
        mutableState.update { it.copy(conflict = null) }
    }

    /**
     * Pausa la sesion activa: congela el cronometro guardando `pausedAtMillis`
     * (no falsificamos `startTime`) y detiene los jobs del FGS. BUG-105.
     *
     * Idempotente: si la sesion ya esta pausada, sale sin tocar Room.
     */
    fun pauseCardio() {
        val session = mutableState.value.session ?: return
        if (session.pausedAtMillis != null) return
        val pausedAt = now()
        val updated = session.copy(pausedAtMillis = pausedAt)
        mutableState.update { it.copy(session = updated) }
        CardioTrackerRegistry.update(
            CardioTrackerRegistry.state.value.copy(
                pausedAtMillis = pausedAt,
                totalPausedDurationMillis = updated.totalPausedDurationMillis,
                running = false,
            ),
        )
        viewModelScope.launch {
            cardioRepository.updateSession(updated)
        }
        // BUG-105: solo reenviamos la accion al FGS si realmente hay uno en marcha
        // (fgsMode != None). En modo local (sin FGS legal) no se arranco ningun
        // servicio; llamar aqui a startService() lanzaria un servicio ordinario
        // que nunca promociona a foreground y que el sistema puede matar,
        // dejando el registro marcado como running sin que el cronometro local
        // (el unico que realmente cuenta el tiempo en este modo) se entere.
        if (mutableState.value.fgsMode != CardioFgsMode.None) {
            runCatching {
                context.startService(CardioForegroundService.pauseIntent(context, session.id))
            }
        }
        stopLocalTimer()
    }

    /**
     * Reanuda la sesion pausada: acumula el tiempo en pausa en
     * `totalPausedDurationMillis`, vacia `pausedAtMillis` y reactiva los jobs
     * del FGS. BUG-105.
     *
     * Idempotente: si la sesion no estaba pausada, sale sin tocar Room.
     */
    fun resumeCardio() {
        val session = mutableState.value.session ?: return
        val pausedAt = session.pausedAtMillis ?: return
        val added = (now() - pausedAt).coerceAtLeast(0L)
        val updated = session.copy(
            pausedAtMillis = null,
            totalPausedDurationMillis = session.totalPausedDurationMillis + added,
        )
        val effectiveSeconds = effectiveElapsedSeconds(updated, now())
        mutableState.update {
            it.copy(
                session = updated,
                elapsedSeconds = effectiveSeconds,
            )
        }
        CardioTrackerRegistry.update(
            CardioTrackerRegistry.state.value.copy(
                elapsedSeconds = effectiveSeconds,
                pausedAtMillis = null,
                totalPausedDurationMillis = updated.totalPausedDurationMillis,
            ),
        )
        viewModelScope.launch {
            cardioRepository.updateSession(updated)
        }
        // BUG-105: idem pauseCardio(). Sin FGS en marcha no hay nada que
        // reanudar en el servicio; seguimos con el cronometro local.
        if (mutableState.value.fgsMode != CardioFgsMode.None) {
            runCatching {
                context.startService(CardioForegroundService.resumeIntent(context, session.id))
            }
        }
        startLocalTimerIfNeeded()
    }

    private fun startCardio(): Job {
        return viewModelScope.launch {
            when (val result = cardioUseCase.startSession(cardioTypeId, mode, weeklyPlanSessionId)) {
                is ActiveSessionStartResult.Started, is ActiveSessionStartResult.Resumed -> {
                    loadSession(result.sessionId)
                }
                is ActiveSessionStartResult.Conflict -> {
                    val active = cardioRepository.session(result.sessionId)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            conflict = active?.let { session ->
                                ActiveCardioConflictUi(
                                    activeSessionId = session.id,
                                    activeCardioTypeName = session.cardioTypeName,
                                )
                            },
                        )
                    }
                }
                ActiveSessionStartResult.NotFound -> {
                    mutableState.update {
                        it.copy(isLoading = false, message = ActiveCardioMessage.SessionMissing)
                    }
                }
            }
        }
    }

    private suspend fun loadSession(sessionId: String) {
        val session = cardioRepository.session(sessionId) ?: run {
            mutableState.update { it.copy(isLoading = false, conflict = null) }
            return
        }
        // BUG-091 / Fase 2 P0: rehidratamos la ruta persistida en Room y la volcamos
        // al CardioTrackerRegistry ANTES de que el FGS arranque su persistJob, para
        // que la sesion continue justo donde se quedo tras una muerte de proceso.
        val restore = cardioUseCase.restoreRoute(sessionId)
        // BUG-105 (Fase 5 P1): si la sesion quedo pausada antes de la muerte
        // del proceso, `state.startedAt` sigue siendo `session.startTime` (no
        // lo falsificamos) y el contador efectivo se calcula con el helper
        // comun (`effectiveElapsedSeconds`) que ya excluye el tiempo en pausa.
        // Si esta pausada ahora mismo, ademas congelamos `running=false` para
        // que el FGS, si arranca, no intente reanudar por su cuenta.
        val effectiveSeconds = effectiveElapsedSeconds(session, now())
        CardioTrackerRegistry.update(
            CardioTrackerState(
                sessionId = session.id,
                startedAt = session.startTime,
                elapsedSeconds = effectiveSeconds,
                targetDurationSeconds = (session.mode as? CardioMode.Countdown)?.targetDurationSeconds,
                pausedAtMillis = session.pausedAtMillis,
                totalPausedDurationMillis = session.totalPausedDurationMillis,
                distanceKm = restore.distanceKm,
                route = restore.points,
                running = false,
                failed = false,
            ),
        )
        mutableState.update {
            it.copy(
                isLoading = false,
                conflict = null,
                session = session,
                mode = session.mode,
                elapsedSeconds = effectiveSeconds,
                distanceKm = restore.distanceKm,
                currentSpeedKmh = null,
                route = restore.points,
            )
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
        // BUG-105 (Fase 5 P1): no arrancamos cronometro local mientras la
        // sesion este pausada. Si arrancase, contariamos tiempo en pausa
        // como tiempo activo por culpa de un tick que ignora el flag.
        if (session.pausedAtMillis != null) return
        if (localTimerJob?.isActive == true) return
        localTimerJob = viewModelScope.launch {
            while (true) {
                val snapshot = mutableState.value
                val current = snapshot.session ?: break
                // BUG-105: si el usuario pulsa Pausar mientras el job ya
                // estaba corriendo, salimos del bucle para no introducir
                // ticks espurios. La salida se reconcilia al reanudar, que
                // vuelve a llamar a este metodo.
                if (current.pausedAtMillis != null) break
                val effective = effectiveElapsedSeconds(current, now())
                mutableState.update { it.copy(elapsedSeconds = effective) }
                if (shouldAutoComplete(mutableState.value.remainingSeconds)) {
                    completeCardio()
                    break
                }
                delay(1000)
            }
            localTimerJob = null
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
    val conflict: ActiveCardioConflictUi? = null,
    val discardInProgress: Boolean = false,
    val message: ActiveCardioMessage? = null,
    // BUG-093 (Fase 4 P0): modo de foreground service que aplica esta sesion.
    // Location = FGS con permiso GPS; Health = FGS con ACTIVITY_RECOGNITION sin
    // GPS; None = sin FGS legal (permiso denegado o escenario manual sin AR).
    // La UI lo usa para mostrar la nota de "modo local" cuando no hay
    // notificacion persistente en la bandeja del sistema.
    val fgsMode: CardioFgsMode = CardioFgsMode.None,
) {
    val requiresManualMetrics: Boolean = session?.hasGps != true || route.isEmpty()
    val hasValidManualMetrics: Boolean =
        (manualDistanceKm.toDoubleOrNull()?.let { it > 0.0 } == true) &&
            (manualAvgSpeedKmh.isBlank() || manualAvgSpeedKmh.toDoubleOrNull()?.let { it > 0.0 } == true)
    val canComplete: Boolean = !completionInProgress &&
        completedSessionId == null &&
        (!requiresManualMetrics || hasValidManualMetrics)
    val isPaused: Boolean = session?.pausedAtMillis != null
    // BUG-104: un GPS sin fix aceptado todavia (route vacia) ya exige metricas
    // manuales via `requiresManualMetrics`, pero antes solo se revelaba el
    // formulario cuando ya habia un `message` de error. Con boton "Finalizar"
    // habilitado (ver `canComplete`) y formulario oculto, la sesion quedaba
    // sin forma de completarse. Mostramos el formulario en cuanto la ruta GPS
    // sigue vacia, sin esperar a un mensaje de error.
    val shouldShowManualMetrics: Boolean = session?.hasGps != true ||
        route.isEmpty() ||
        message == ActiveCardioMessage.LocationPermissionDenied ||
        message == ActiveCardioMessage.TrackerUnavailable ||
        message == ActiveCardioMessage.ManualMetricsRequired

    val averageSpeedKmh: Double? = if (elapsedSeconds > 0 && distanceKm > 0.0) {
        distanceKm / (elapsedSeconds / 3600.0)
    } else {
        null
    }

    /**
     * Pace en minutos por kilometro. BUG-102 (Fase 13 P3). Solo se muestra
     * cuando hay velocidad real (distance > 0 y elapsed > 0); un ritmo de
     * infinito se evita para no dividir entre cero.
     */
    val paceMinPerKm: Double? = averageSpeedKmh?.takeIf { it > 0.0 }?.let { 60.0 / it }

    /**
     * Estado explicito del GPS. BUG-102. La UI ya no muestra `0.0 km/h`
     * como si fuera una medicion real cuando todavia no hay fix:
     * - Searching: esperando primer fix o sin puntos aceptados.
     * - Weak: ultimo fix tiene accuracy pobre.
     * - Active: hay fix reciente y route.size >= 2.
     * - Denied: el usuario rechazo el permiso.
     * - Unavailable: el dispositivo no provee location.
     */
    val gpsState: GpsState = when {
        message == ActiveCardioMessage.LocationPermissionDenied -> GpsState.Denied
        session?.hasGps != true -> GpsState.NotApplicable
        route.isEmpty() -> GpsState.Searching
        route.size == 1 -> GpsState.Searching
        else -> GpsState.Active
    }

    val remainingSeconds: Long? = (mode as? CardioMode.Countdown)
        ?.targetDurationSeconds
        ?.let { (it - elapsedSeconds).coerceAtLeast(0) }
}

enum class GpsState {
    NotApplicable,
    Searching,
    Active,
    Weak,
    Denied,
    Unavailable,
}

data class ActiveCardioConflictUi(
    val activeSessionId: String,
    val activeCardioTypeName: String,
)

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
