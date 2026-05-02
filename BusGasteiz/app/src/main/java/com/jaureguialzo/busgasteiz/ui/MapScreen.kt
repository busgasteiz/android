package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.LocationRepository
import com.jaureguialzo.busgasteiz.data.NearbyStop
import com.jaureguialzo.busgasteiz.data.computeStopsInBounds
import com.jaureguialzo.busgasteiz.settings.AppSettings
import com.jaureguialzo.busgasteiz.ui.components.StopIcon
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.PI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// Convierte un radio en metros al nivel de zoom de Google Maps adecuado para visualizarlo
private fun radiusToZoom(radius: Float, lat: Double): Float {
    val cosLat = cos(lat * PI / 180.0)
    return log2(156543.03392 * cosLat * 360.0 / (radius * 1.5)).toFloat()
}

// Inversa de radiusToZoom: convierte un nivel de zoom al radio equivalente en metros
private fun zoomToRadius(zoom: Float, lat: Double): Float {
    val cosLat = cos(lat * PI / 180.0)
    return (156543.03392 * cosLat * 360.0 / (1.5 * 2.0.pow(zoom.toDouble()))).toFloat()
}

// Redondea un radio continuo al valor predefinido más cercano
private fun snapRadiusToPreset(radius: Float): Float {
    val presets = listOf(100f, 200f, 300f, 500f, 1000f)
    return presets.minByOrNull { kotlin.math.abs(it - radius) } ?: 200f
}

// MARK: - Mapa de paradas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    dataRepository: DataRepository,
    locationRepository: LocationRepository,
    favoritesRepository: FavoritesRepository,
    appSettings: AppSettings,
    navController: NavController
) {
    val version by dataRepository.version.collectAsState()
    val locationVersion by locationRepository.locationVersion.collectAsState()
    val location by locationRepository.location.collectAsState()
    val activePosition by locationRepository.activePosition.collectAsState()
    val loadState by dataRepository.loadState.collectAsState()
    val isRefreshing by dataRepository.isRefreshing.collectAsState()
    val searchRadius by appSettings.searchRadiusFlow.collectAsState()

    val mapStops = remember { mutableStateListOf<NearbyStop>() }
    val scope = rememberCoroutineScope()
    var recomputeJob by remember { mutableStateOf<Job?>(null) }
    var showRadiusMenu by remember { mutableStateOf(false) }
    var isResolvingLocation by remember { mutableStateOf(false) }

    var selectedStop by remember { mutableStateOf<NearbyStop?>(null) }
    var showStopSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // Evita que una actualización del radio iniciada desde el zoom del mapa dispare
    // la animación de cámara en LaunchedEffect(searchRadius).
    var syncingRadiusFromCamera by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        val initialPos = locationRepository.activePosition.value
        // Lee el radio almacenado para calcular el zoom inicial correcto.
        // runBlocking es intencionado: DataStore entrega el valor de forma síncrona
        // desde caché (o casi instantáneamente si aún no se ha leído).
        val storedRadius = runBlocking { appSettings.searchRadiusFlow.first() }
        val initialZoom = radiusToZoom(storedRadius, initialPos.latitude)
        position = CameraPosition.fromLatLngZoom(LatLng(initialPos.latitude, initialPos.longitude), initialZoom)
    }

    fun recompute() {
        val gtfs = dataRepository.gtfsData.value ?: return
        val proj = cameraPositionState.projection ?: return
        val bounds = proj.visibleRegion.latLngBounds
        val refLat = location?.latitude ?: LocationRepository.VITORIA_LAT
        val refLon = location?.longitude ?: LocationRepository.VITORIA_LON
        val activeIds = dataRepository.activeStopIds.value
        val alerts = dataRepository.serviceAlerts.value
        recomputeJob?.cancel()
        recomputeJob = scope.launch {
            val stops = withContext(Dispatchers.Default) {
                computeStopsInBounds(
                    minLat = bounds.southwest.latitude,
                    maxLat = bounds.northeast.latitude,
                    minLon = bounds.southwest.longitude,
                    maxLon = bounds.northeast.longitude,
                    refLat = refLat,
                    refLon = refLon,
                    gtfsData = gtfs,
                    activeStopIds = activeIds,
                    alerts = alerts
                )
            }
            mapStops.clear()
            mapStops.addAll(stops)
        }
    }

    // Recompute cuando cambien los datos
    LaunchedEffect(version) { recompute() }

    // Actualiza la posición activa cuando el usuario desplaza el mapa manualmente.
    // Si el zoom cambió, actualiza también el selector de radio.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            locationRepository.setActivePositionToMapCenter(target.latitude, target.longitude)
            recompute()
            val inferredRadius = snapRadiusToPreset(
                zoomToRadius(cameraPositionState.position.zoom, target.latitude)
            )
            if (inferredRadius != searchRadius) {
                syncingRadiusFromCamera = true
                appSettings.setSearchRadius(inferredRadius)
            }
        }
    }

    // Centra el mapa cuando se resuelve la posición (inicio o botón de localización)
    var isFirstLocationVersion by remember { mutableStateOf(true) }
    LaunchedEffect(locationVersion) {
        if (isFirstLocationVersion) {
            isFirstLocationVersion = false
            return@LaunchedEffect
        }
        val pos = locationRepository.activePosition.value
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(pos.latitude, pos.longitude), cameraPositionState.position.zoom)
        )
        recompute()
    }

    // Ajusta el zoom del mapa cuando cambia el radio de búsqueda desde el selector.
    // Si el cambio viene del propio zoom (syncingRadiusFromCamera), no anima la cámara.
    var isFirstSearchRadius by remember { mutableStateOf(true) }
    LaunchedEffect(searchRadius) {
        if (isFirstSearchRadius) {
            isFirstSearchRadius = false
            return@LaunchedEffect
        }
        if (syncingRadiusFromCamera) {
            syncingRadiusFromCamera = false
            return@LaunchedEffect
        }
        val center = cameraPositionState.position.target
        val zoom = radiusToZoom(searchRadius, center.latitude)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(center, zoom)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_map)) },
                actions = {
                    // Botón de localización
                    IconButton(
                        onClick = {
                            scope.launch {
                                isResolvingLocation = true
                                locationRepository.resolveActivePosition()
                                kotlinx.coroutines.delay(1000)
                                isResolvingLocation = false
                            }
                        },
                        enabled = !isResolvingLocation
                    ) {
                        if (isResolvingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NearMe, contentDescription = stringResource(R.string.my_location))
                        }
                    }
                    Box {
                        TextButton(onClick = { showRadiusMenu = true }) {
                            Text("${searchRadius.toInt()} m", color = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = showRadiusMenu,
                            onDismissRequest = { showRadiusMenu = false }
                        ) {
                            listOf(100f, 200f, 300f, 500f, 1000f).forEach { r ->
                                DropdownMenuItem(
                                    text = { Text("${r.toInt()} m") },
                                    onClick = {
                                        scope.launch { appSettings.setSearchRadius(r) }
                                        showRadiusMenu = false
                                    }
                                )
                            }
                        }
                    }
                    // Botón de recarga de datos
                    IconButton(
                        onClick = { dataRepository.forceRefresh() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = location != null),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false),
                onMapLoaded = { recompute() }
            ) {
                mapStops.forEach { nearby ->
                    MarkerComposable(
                        keys = arrayOf<Any>(nearby.stop.id, nearby.hasArrivals, nearby.hasAlert),
                        state = MarkerState(position = LatLng(nearby.stop.lat, nearby.stop.lon)),
                        title = nearby.stop.localizedName,
                        snippet = distanceLabel(nearby.distance),
                        anchor = Offset(0.5f, 0.5f),
                        onClick = { _ ->
                            selectedStop = nearby
                            showStopSheet = true
                            true
                        }
                    ) {
                        StopIcon(
                            size = 27.dp,
                            isTram = nearby.stop.isTram,
                            hasArrivals = nearby.hasArrivals,
                            hasAlert = nearby.hasAlert,
                            iconSize = 14.dp
                        )
                    }
                }
            }

            // Overlay de carga/error encima del mapa
            when (val state = loadState) {
                is DataRepository.LoadState.Idle,
                is DataRepository.LoadState.Loading -> {
                    val msg = if (state is DataRepository.LoadState.Loading) state.message
                              else stringResource(R.string.starting_up)
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
                is DataRepository.LoadState.Failed -> {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(stringResource(R.string.error_loading), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { dataRepository.forceRefresh() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                is DataRepository.LoadState.Ready -> {}
            }
        }
    }

    // Bottom sheet de detalle de parada al pulsar marcador
    if (showStopSheet) {
        selectedStop?.let { nearby ->
            ModalBottomSheet(
                onDismissRequest = { showStopSheet = false },
                sheetState = bottomSheetState
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    StopDetailContent(
                        stop = nearby.stop,
                        distance = nearby.distance,
                        dataRepository = dataRepository,
                        favoritesRepository = favoritesRepository,
                        navController = navController,
                        onClose = { showStopSheet = false }
                    )
                }
            }
        }
    }
}
