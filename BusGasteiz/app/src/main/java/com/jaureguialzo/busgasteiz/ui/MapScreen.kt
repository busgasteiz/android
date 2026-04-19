package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
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
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.LocationRepository
import com.jaureguialzo.busgasteiz.data.NearbyStop
import com.jaureguialzo.busgasteiz.data.computeStopsInBounds
import com.jaureguialzo.busgasteiz.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isRefreshing by dataRepository.isRefreshing.collectAsState()
    val searchRadius by appSettings.searchRadiusFlow.collectAsState(initial = 200f)

    val mapStops = remember { mutableStateListOf<NearbyStop>() }
    val scope = rememberCoroutineScope()
    var recomputeJob by remember { mutableStateOf<Job?>(null) }
    var showRadiusMenu by remember { mutableStateOf(false) }

    var selectedStop by remember { mutableStateOf<NearbyStop?>(null) }
    var showStopSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Centro predeterminado: Vitoria-Gasteiz
    val defaultLatLng = LatLng(42.846718, -2.671622)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }

    fun recompute() {
        val gtfs = dataRepository.gtfsData.value ?: return
        val proj = cameraPositionState.projection ?: return
        val bounds = proj.visibleRegion.latLngBounds
        val refLat = location?.latitude ?: defaultLatLng.latitude
        val refLon = location?.longitude ?: defaultLatLng.longitude
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_map)) },
                actions = {
                    Box {
                        TextButton(onClick = { showRadiusMenu = true }) {
                            Text("${searchRadius.toInt()} m")
                        }
                        DropdownMenu(
                            expanded = showRadiusMenu,
                            onDismissRequest = { showRadiusMenu = false }
                        ) {
                            listOf(100f, 200f, 500f, 1000f, 2000f).forEach { r ->
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val lat = location?.latitude ?: defaultLatLng.latitude
                val lon = location?.longitude ?: defaultLatLng.longitude
                scope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15f)
                    )
                }
            }) {
                Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.my_location))
            }
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
                    Marker(
                        state = MarkerState(position = LatLng(nearby.stop.lat, nearby.stop.lon)),
                        title = nearby.stop.localizedName,
                        snippet = distanceLabel(nearby.distance),
                        onClick = { _ ->
                            selectedStop = nearby
                            showStopSheet = true
                            true
                        }
                    )
                }
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
