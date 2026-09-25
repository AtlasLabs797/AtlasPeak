package com.atlaspeak.presentation.cardio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.service.CardioForegroundService

/**
 * Seam entre [ActiveCardioViewModel] y el [CardioForegroundService] real.
 *
 * BUG-093/BUG-105: la capa de presentacion no puede depender directamente de
 * `Context.checkSelfPermission`/`ContextCompat.startForegroundService`/
 * `Intent(context, Class)` porque el android.jar mockable que usa
 * `./gradlew test` (sin `isReturnDefaultValues`) lanza en tiempo de ejecucion
 * en esas llamadas (`Process.myPid()` y el constructor de `Intent` son stubs
 * "not mocked"). Esta interfaz cubre exactamente lo que el ViewModel necesita
 * de Android para poder sustituirla por un fake en los tests JVM.
 */
interface CardioTrackerServiceController {
    fun hasActivityRecognition(): Boolean
    fun start(sessionId: String, startedAt: Long, gpsEnabled: Boolean, mode: CardioMode)
    fun pause(sessionId: String)
    fun resume(sessionId: String)
    fun stop()
}

/** Implementacion de produccion: contiene las llamadas Android exactas que antes vivian en el ViewModel. */
class ContextCardioTrackerServiceController(
    private val context: Context,
) : CardioTrackerServiceController {

    override fun hasActivityRecognition(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun start(sessionId: String, startedAt: Long, gpsEnabled: Boolean, mode: CardioMode) {
        ContextCompat.startForegroundService(
            context,
            CardioForegroundService.startIntent(context, sessionId, startedAt, gpsEnabled, mode),
        )
    }

    override fun pause(sessionId: String) {
        context.startService(CardioForegroundService.pauseIntent(context, sessionId))
    }

    override fun resume(sessionId: String) {
        context.startService(CardioForegroundService.resumeIntent(context, sessionId))
    }

    override fun stop() {
        context.stopService(CardioForegroundService.stopIntent(context))
    }
}
