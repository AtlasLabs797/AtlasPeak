package com.atlaspeak.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.atlaspeak.MainActivity
import com.atlaspeak.R
import com.atlaspeak.data.location.LocationTracker
import com.atlaspeak.domain.model.cardio.CardioFgsMode
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.model.cardio.effectiveElapsedSeconds
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.domain.usecase.cardio.CardioUseCase.Companion.distanceKm
import com.atlaspeak.domain.usecase.cardio.CardioUseCase.Companion.maxSegmentSpeedKmh
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardioTrackerState(
    val sessionId: String? = null,
    val startedAt: Long? = null,
    val elapsedSeconds: Long = 0L,
    val targetDurationSeconds: Int? = null,
    val pausedAtMillis: Long? = null,
    val totalPausedDurationMillis: Long = 0L,
    val locationTracking: Boolean = false,
    val distanceKm: Double = 0.0,
    val currentSpeedKmh: Double? = null,
    val avgSpeedKmh: Double? = null,
    val route: List<LocationPoint> = emptyList(),
    val running: Boolean = false,
    val failed: Boolean = false,
) {
    val remainingSeconds: Long? = targetDurationSeconds?.let { (it - elapsedSeconds).coerceAtLeast(0) }
}

object CardioTrackerRegistry {
    private val mutableState = MutableStateFlow(CardioTrackerState())
    val state: StateFlow<CardioTrackerState> = mutableState.asStateFlow()

    fun update(state: CardioTrackerState) {
        mutableState.value = state
    }

    fun tick(now: Long) {
        mutableState.update { current ->
            val startedAt = current.startedAt ?: return@update current
            current.copy(
                elapsedSeconds = effectiveElapsedSeconds(
                    startTime = startedAt,
                    totalPausedDurationMillis = current.totalPausedDurationMillis,
                    pausedAtMillis = current.pausedAtMillis,
                    now = now,
                ),
                currentSpeedKmh = current.currentSpeedKmh.takeIf {
                    current.route.lastOrNull()?.let { point -> now - point.timestamp <= CURRENT_SPEED_MAX_AGE_MS } == true
                },
            )
        }
    }

    fun addPoint(point: LocationPoint) {
        mutableState.update { current ->
            val currentSpeed = current.route.lastOrNull()
                ?.let { previous -> listOf(previous, point).maxSegmentSpeedKmh() }
            val route = current.route + point
            val distance = route.distanceKm()
            val avgSpeed = if (distance > 0.0 && current.elapsedSeconds > 0) {
                distance / (current.elapsedSeconds / 3600.0)
            } else {
                null
            }
            current.copy(
                route = route,
                distanceKm = distance,
                currentSpeedKmh = currentSpeed,
                avgSpeedKmh = avgSpeed,
            )
        }
    }
}

private const val CURRENT_SPEED_MAX_AGE_MS = 5_000L

@AndroidEntryPoint
class CardioForegroundService : LifecycleService() {
    @Inject lateinit var cardioUseCase: CardioUseCase

    private var timerJob: Job? = null
    private var locationJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startTracking(
                sessionId = requireNotNull(intent.getStringExtra(EXTRA_SESSION_ID)),
                startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis()),
                hasGps = intent.getBooleanExtra(EXTRA_HAS_GPS, false),
                targetDurationSeconds = intent.getIntExtra(EXTRA_TARGET_DURATION_SECONDS, 0).takeIf { it > 0 },
            )
            ACTION_STOP -> stopTracking(clearState = true)
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
        }
        // BUG-058: START_STICKY provocaba servicio zombi tras presión de memoria.
        // Con START_NOT_STICKY, si Android mata el servicio, el cardio se reanuda
        // desde la UI al volver a abrir la app.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTracking(clearState = !CardioTrackerRegistry.state.value.failed)
        super.onDestroy()
    }

    private fun startTracking(
        sessionId: String,
        startedAt: Long,
        hasGps: Boolean,
        targetDurationSeconds: Int?,
    ) {
        ensureNotificationChannel()
        // BUG-093 (Fase 4 P0): preflightFgsType decide, en funcion de los
        // permisos reales del dispositivo, que tipo de foreground service es
        // legal reclamar ahora. Android 14+ aplica una politica estricta: si
        // pedimos FOREGROUND_SERVICE_TYPE_LOCATION sin ACCESS_FINE_LOCATION
        // concedida, o FOREGROUND_SERVICE_TYPE_HEALTH sin un uso real de datos
        // de salud (ACTIVITY_RECOGNITION, Health Connect write), el sistema
        // lanza `SecurityException` y la app puede quedar sin notificacion
        // persistente. Si no hay un tipo legal, dejamos que el cronometro local
        // siga (no reclamamos FGS): documentado en la UI como "modo local".
        val fgsMode = preflightFgsType(
            hasGps = hasGps,
            hasFineLocation = hasFineLocationPermission(),
            hasActivityRecognition = hasActivityRecognitionPermission(),
        )
        val locationTracking = fgsMode == CardioFgsMode.Location
        // BUG-091: preservamos ruta/distancia/etc. si el VM ya rehidrato el
        // registro antes de arrancar el FGS (process recreation). El FGS solo
        // se responsabiliza de los campos de cronometro/notificacion.
        val snapshot = CardioTrackerRegistry.state.value
        val current = snapshot.takeIf { it.sessionId == sessionId }
            ?: CardioTrackerState(sessionId = sessionId, startedAt = startedAt)

        // El ViewModel evita iniciar el servicio cuando el preflight devuelve
        // None. Repetimos la defensa aqui porque los permisos pueden cambiar
        // entre ambos checks. Si ocurre esa carrera, detenemos inmediatamente
        // el servicio iniciado con startForegroundService() y dejamos una
        // senal de fallo para que el ViewModel use su temporizador local.
        if (fgsMode == CardioFgsMode.None) {
            CardioTrackerRegistry.update(
                current.copy(
                    sessionId = sessionId,
                    startedAt = startedAt,
                    elapsedSeconds = effectiveElapsedSeconds(
                        startTime = startedAt,
                        totalPausedDurationMillis = current.totalPausedDurationMillis,
                        pausedAtMillis = current.pausedAtMillis,
                        now = System.currentTimeMillis(),
                    ),
                    targetDurationSeconds = targetDurationSeconds,
                    locationTracking = false,
                    running = false,
                    failed = true,
                ),
            )
            stopSelf()
            return
        }

        CardioTrackerRegistry.update(
            current.copy(
                sessionId = sessionId,
                startedAt = startedAt,
                elapsedSeconds = effectiveElapsedSeconds(
                        startTime = startedAt,
                        totalPausedDurationMillis = current.totalPausedDurationMillis,
                        pausedAtMillis = current.pausedAtMillis,
                        now = System.currentTimeMillis(),
                    ),
                targetDurationSeconds = targetDurationSeconds,
                locationTracking = locationTracking,
                running = current.pausedAtMillis == null,
                failed = false,
            ),
        )
        try {
            startForegroundCompat(buildNotification(CardioTrackerRegistry.state.value), fgsMode)
        } catch (_: SecurityException) {
            // BUG-093: SecurityException explicito (subclase de
            // RuntimeException, pero mas claro para el lector). Android 14+
            // puede lanzar esto si la politica de tipos FGS no se cumple
            // pese a que preflight pensaba que si.
            CardioTrackerRegistry.update(CardioTrackerRegistry.state.value.copy(running = false, failed = true))
            stopSelf()
            return
        } catch (_: RuntimeException) {
            CardioTrackerRegistry.update(CardioTrackerRegistry.state.value.copy(running = false, failed = true))
            stopSelf()
            return
        }
        if (current.pausedAtMillis == null) {
            startTimerJob()
            if (locationTracking) startLocationJob(sessionId)
        }
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                CardioTrackerRegistry.tick(System.currentTimeMillis())
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    buildNotification(CardioTrackerRegistry.state.value),
                )
                delay(1000)
            }
        }
    }

    private fun startLocationJob(sessionId: String) {
        locationJob?.cancel()
        locationJob = lifecycleScope.launch {
            var lastAcceptedPoint = CardioTrackerRegistry.state.value.route.lastOrNull()
            LocationTracker(this@CardioForegroundService).locations().collect { location ->
                val candidate = LocationPoint(location.latitude, location.longitude, location.time)
                val persisted = cardioUseCase.appendRoutePoint(
                    sessionId = sessionId,
                    point = candidate,
                    accuracyMeters = location.accuracy,
                    previousAcceptedPoint = lastAcceptedPoint,
                )
                if (persisted != null) {
                    val accepted = persisted.toLocationPoint()
                    lastAcceptedPoint = accepted
                    CardioTrackerRegistry.addPoint(accepted)
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification, fgsMode: CardioFgsMode) {
        // BUG-093: el caller (startTracking) ya filtra CardioFgsMode.None antes
        // de llamar aqui. Dejamos el require como red de seguridad: si alguien
        // intenta reclamar un FGS con None, fallamos ruidosamente en vez de
        // reclamar un tipo incorrecto.
        require(fgsMode != CardioFgsMode.None) {
            "startForegroundCompat called with CardioFgsMode.None; skip startForeground instead."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = when (fgsMode) {
                CardioFgsMode.Location -> android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                CardioFgsMode.Health -> android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                CardioFgsMode.None -> error("precondition violated: CardioFgsMode.None")
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopTracking(clearState: Boolean) {
        timerJob?.cancel()
        locationJob?.cancel()
        timerJob = null
        locationJob = null
        if (clearState) {
            CardioTrackerRegistry.update(CardioTrackerState())
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * BUG-094 (Fase 5 P1): pausa los jobs del FGS (cronometro, persistencia de
     * ruta, captura GPS) sin tocar el registro: el VM ya ha guardado el
     * `pausedAtMillis` en Room, asi que `effectiveElapsedSeconds` congelara el
     * contador mientras tanto. No reclamamos foreground nuevo: la notificacion
     * existente sigue visible y se actualizara al reanudar.
     */
    private fun pauseTracking() {
        timerJob?.cancel()
        locationJob?.cancel()
        timerJob = null
        locationJob = null
        CardioTrackerRegistry.update(CardioTrackerRegistry.state.value.copy(running = false))
    }

    /**
     * BUG-094 (Fase 5 P1): reanuda los jobs del FGS. Re-deriva el `startedAt`
     * y `elapsedSeconds` desde Room (vía el VM, que ya actualizo
     * `totalPausedDurationMillis`). Si la sesion no tenia GPS, no arranca
     * `locationJob` (sin captura de ubicacion durante la pausa).
     */
    private fun resumeTracking() {
        val current = CardioTrackerRegistry.state.value
        val sessionId = current.sessionId ?: return
        if (current.running) return
        CardioTrackerRegistry.update(current.copy(running = true))
        startTimerJob()
        if (current.locationTracking) startLocationJob(sessionId)
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        // BUG-093 (Fase 4 P0): en Android 10+ ACTIVITY_RECOGNITION es permiso
        // runtime; en API < 29 es normal (concedido por declararse en el
        // manifest). checkSelfPermission cubre ambos casos correctamente.
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(state: CardioTrackerState): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.cardio_notification_title))
            .setContentText(getString(R.string.cardio_notification_text, formatElapsed(state.elapsedSeconds), state.distanceKm))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun contentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, REQUEST_CONTENT, intent, flags)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.cardio_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun formatElapsed(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }

    companion object {
        private const val CHANNEL_ID = "active_cardio"
        private const val NOTIFICATION_ID = 1301
        private const val ACTION_START = "com.atlaspeak.action.START_CARDIO"
        private const val ACTION_STOP = "com.atlaspeak.action.STOP_CARDIO"
        private const val ACTION_PAUSE = "com.atlaspeak.action.PAUSE_CARDIO"
        private const val ACTION_RESUME = "com.atlaspeak.action.RESUME_CARDIO"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_HAS_GPS = "has_gps"
        private const val EXTRA_TARGET_DURATION_SECONDS = "target_duration_seconds"
        private const val REQUEST_CONTENT = 1302

        fun startIntent(context: Context, sessionId: String, startedAt: Long, hasGps: Boolean, mode: CardioMode): Intent {
            return Intent(context, CardioForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_STARTED_AT, startedAt)
                .putExtra(EXTRA_HAS_GPS, hasGps)
                .putExtra(EXTRA_TARGET_DURATION_SECONDS, (mode as? CardioMode.Countdown)?.targetDurationSeconds ?: 0)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, CardioForegroundService::class.java).setAction(ACTION_STOP)
        }

        fun pauseIntent(context: Context, sessionId: String): Intent {
            return Intent(context, CardioForegroundService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_SESSION_ID, sessionId)
        }

        fun resumeIntent(context: Context, sessionId: String): Intent {
            return Intent(context, CardioForegroundService::class.java)
                .setAction(ACTION_RESUME)
                .putExtra(EXTRA_SESSION_ID, sessionId)
        }

        /**
         * Decide que tipo de foreground service cabe en el escenario actual de
         * cardio en funcion de los permisos reales del dispositivo. BUG-093
         * (Fase 4 P0): Android 14+ (API 34+) aplica una politica estricta de
         * tipos de FGS -- reclamar un tipo sin los permisos/uso real que lo
         * justifican dispara `SecurityException` y deja la sesion sin
         * notificacion persistente. Si ningun tipo es legal, devolvemos
         * [CardioFgsMode.None] y el caller debe dejar el cronometro local
         * correr sin reclamar foreground.
         *
         * Pure function: sin dependencias de Android, facil de testear.
         */
        fun preflightFgsType(
            hasGps: Boolean,
            hasFineLocation: Boolean,
            hasActivityRecognition: Boolean,
        ): CardioFgsMode {
            return when {
                hasGps && hasFineLocation -> CardioFgsMode.Location
                hasGps && !hasFineLocation -> CardioFgsMode.None
                !hasGps && hasActivityRecognition -> CardioFgsMode.Health
                else -> CardioFgsMode.None
            }
        }
    }
}
