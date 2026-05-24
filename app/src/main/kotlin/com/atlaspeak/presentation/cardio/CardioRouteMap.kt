package com.atlaspeak.presentation.cardio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val routeColor = MaterialTheme.colorScheme.primary
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 15f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
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
