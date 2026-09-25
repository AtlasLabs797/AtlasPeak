package com.atlaspeak.presentation.cardio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.atlaspeak.R
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
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
    val routeColor = LocalAtlasColors.current.ink
    val cameraPositionState = rememberCameraPositionState()
    val initialPosition = remember(route) {
        CameraPosition.fromLatLngZoom(points.first(), 15f)
    }
    // BUG-100 (Fase 11 P2): antes el mapa centraba siempre en `points.first()`
    // con zoom fijo 15f, dejando la ruta visible solo si el usuario no se
    // habia movido mucho. Ahora calculamos bounds para 2+ puntos (caso
    // normal de cardio GPS) y solo usamos el zoom fijo para 1 punto.
    LaunchedEffect(route.size) {
        val cameraUpdate = when {
            points.size == 1 -> CameraUpdateFactory.newLatLngZoom(points.first(), 15f)
            else -> {
                val builder = LatLngBounds.builder()
                points.forEach { builder.include(it) }
                CameraUpdateFactory.newLatLngBounds(builder.build(), BOUNDS_PADDING_PX)
            }
        }
        try {
            cameraPositionState.animate(cameraUpdate)
        } catch (_: Throwable) {
            // Fallback al primer punto si el calculo falla (raro, pero
            // garantiza que la camara no se queda en una posicion invalida).
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(points.first(), 15f),
            )
        }
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
            // Marcadores inicio/fin para sesiones GPS largas donde la
            // polilinea sola no dice donde empezo/termino el recorrido.
            val start = points.first()
            Marker(
                state = remember(start) { MarkerState(position = start) },
                title = stringResource(R.string.cardio_route_start),
            )
            if (points.size > 1) {
                val end = points.last()
                Marker(
                    state = remember(end) { MarkerState(position = end) },
                    title = stringResource(R.string.cardio_route_end),
                )
            }
            Polyline(
                points = points,
                color = routeColor,
                width = 8f,
            )
        }
        // `initialPosition` se calcula pero ya no se aplica al `rememberCameraPositionState`;
        // el LaunchedEffect es el responsable del encuadre real.
        @Suppress("UNUSED_VARIABLE") val unused = initialPosition
    }
}

private const val BOUNDS_PADDING_PX = 96

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
