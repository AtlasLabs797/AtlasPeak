package com.atlaspeak.core.time

import java.util.Locale

/**
 * Formato mm:ss compartido por los cronómetros en vivo (pantallas activas y
 * notificaciones de los foreground services). Antes había cuatro copias idénticas.
 */
object ElapsedClock {
    fun format(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds)
    }
}
