package com.atlaspeak.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.atlaspeak.R
import com.atlaspeak.data.location.LocationTracker
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.usecase.cardio.CardioUseCase.Companion.distanceKm
import com.atlaspeak.domain.usecase.cardio.CardioUseCase.Companion.maxSegmentSpeedKmh
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
            current.copy(elapsedSeconds = ((now - startedAt) / 1000).coerceAtLeast(0))
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

class CardioForegroundService : LifecycleService() {
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
        }
        return START_STICKY
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
        val locationTracking = hasGps && hasFineLocationPermission()
        CardioTrackerRegistry.update(
            CardioTrackerState(
                sessionId = sessionId,
                startedAt = startedAt,
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0),
                targetDurationSeconds = targetDurationSeconds,
                running = true,
            ),
        )
        if (!locationTracking) {
            CardioTrackerRegistry.update(CardioTrackerRegistry.state.value.copy(running = false, failed = true))
            stopSelf()
            return
        }
        try {
            startForegroundCompat(buildNotification(CardioTrackerRegistry.state.value))
        } catch (_: RuntimeException) {
            CardioTrackerRegistry.update(CardioTrackerRegistry.state.value.copy(running = false, failed = true))
            stopSelf()
            return
        }
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
        if (locationTracking) {
            locationJob?.cancel()
            locationJob = lifecycleScope.launch {
                LocationTracker(this@CardioForegroundService).locations().collect { location ->
                    CardioTrackerRegistry.addPoint(
                        LocationPoint(location.latitude, location.longitude, location.time),
                    )
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
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

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
            .build()
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
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_HAS_GPS = "has_gps"
        private const val EXTRA_TARGET_DURATION_SECONDS = "target_duration_seconds"

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
    }
}
