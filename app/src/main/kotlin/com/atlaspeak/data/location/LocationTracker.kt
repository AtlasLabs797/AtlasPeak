package com.atlaspeak.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(
    context: Context,
) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locations(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations
                    .filter { it.isUsableForCardioTracking() }
                    .forEach { trySend(it) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 5_000L
        const val LOCATION_FASTEST_INTERVAL_MS = 2_000L
    }
}

internal fun Location.isUsableForCardioTracking(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return isUsableForCardioTracking(
        hasAccuracy = hasAccuracy(),
        accuracyMeters = accuracy,
        locationTimeMillis = time,
        nowMillis = nowMillis,
    )
}

internal fun isUsableForCardioTracking(
    hasAccuracy: Boolean,
    accuracyMeters: Float,
    locationTimeMillis: Long,
    nowMillis: Long,
): Boolean {
    if (!hasAccuracy || accuracyMeters > MAX_TRACKING_ACCURACY_METERS) return false
    val ageMillis = nowMillis - locationTimeMillis
    return ageMillis in 0..MAX_TRACKING_LOCATION_AGE_MS
}

private const val MAX_TRACKING_ACCURACY_METERS = 30f
private const val MAX_TRACKING_LOCATION_AGE_MS = 10_000L
