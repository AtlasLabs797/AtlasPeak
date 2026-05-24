package com.atlaspeak.presentation.cardio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun CardioRouteMap(
    route: List<LocationPoint>,
    modifier: Modifier = Modifier,
) {
    val points = route.map { LatLng(it.latitude, it.longitude) }
    if (points.isEmpty()) return
    val description = stringResource(
        R.string.cardio_route_map_summary,
        points.size,
        route.distanceKm(),
    )
    val routeColor = MaterialTheme.colorScheme.primary
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 15f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description },
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
        ) {
            Polyline(
                points = points,
                color = routeColor,
                width = 8f,
            )
        }
    }
}

private fun List<LocationPoint>.distanceKm(): Double {
    return zipWithNext().sumOf { (start, end) ->
        val result = FloatArray(1)
        android.location.Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            result,
        )
        result[0].toDouble()
    } / 1000.0
}
