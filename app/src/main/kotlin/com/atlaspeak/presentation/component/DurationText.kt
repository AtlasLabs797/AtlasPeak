package com.atlaspeak.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.atlaspeak.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Duración legible para humanos: "1 h 24 min", "24 min" o "45 s".
 * Los segundos sueltos solo se muestran por debajo del minuto: en sesiones
 * largas no aportan y ensucian la lectura.
 */
@Composable
fun formatDurationSeconds(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_seconds, seconds)
    }
}

/** Fecha a nivel de día en zona horaria local, p. ej. "12 may". */
fun formatDayDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
