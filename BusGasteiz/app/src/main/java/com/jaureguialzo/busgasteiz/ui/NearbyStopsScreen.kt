package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.LocationRepository
import com.jaureguialzo.busgasteiz.data.NearbyStop
import com.jaureguialzo.busgasteiz.data.computeNearbyStops
import com.jaureguialzo.busgasteiz.settings.AppSettings
import com.jaureguialzo.busgasteiz.ui.components.RouteBadge
import com.jaureguialzo.busgasteiz.ui.components.StopIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Lista de paradas cercanas

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NearbyStopsScreen(
    dataRepository: DataRepository,
    locationRepository: LocationRepository,
    favoritesRepository: FavoritesRepository,
    appSettings: AppSettings,
    navController: NavController,
    onAboutClick: () -> Unit
) {
    val loadState by dataRepository.loadState.collectAsState()
    val version by dataRepository.version.collectAsState()
    val locationVersion by locationRepository.locationVersion.collectAsState()
    val location by locationRepository.location.collectAsState()
    val activePosition by locationRepository.activePosition.collectAsState()
    val searchRadius by appSettings.searchRadiusFlow.collectAsState(initial = 200f)
    val isRefreshing by dataRepository.isRefreshing.collectAsState()
    val favoriteStopIds by favoritesRepository.favoriteStopIds.collectAsState()

    val nearbyStops = remember { mutableStateListOf<NearbyStop>() }
    val scope = rememberCoroutineScope()
    var recomputeJob by remember { mutableStateOf<Job?>(null) }
    var showRadiusMenu by remember { mutableStateOf(false) }
    var isResolvingLocation by remember { mutableStateOf(false) }

    fun recompute() {
        val gtfs = dataRepository.gtfsData.value ?: return
        val lat = activePosition.latitude
        val lon = activePosition.longitude
        val activeIds = dataRepository.activeStopIds.value
        val alerts = dataRepository.serviceAlerts.value
        recomputeJob?.cancel()
        recomputeJob = scope.launch {
            val stops = withContext(Dispatchers.Default) {
                computeNearbyStops(lat, lon, searchRadius.toDouble(), gtfs, activeIds, alerts)
            }
            nearbyStops.clear()
            nearbyStops.addAll(stops)
        }
    }

    // Recompute al cambiar versión de datos, localización o radio
    LaunchedEffect(version, locationVersion, searchRadius) { recompute() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nearby_stops)) },
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
                    // Menú de radio de búsqueda
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
                    // Botón de recarga
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
        when (val state = loadState) {
            is DataRepository.LoadState.Idle,
            is DataRepository.LoadState.Loading -> {
                val msg = if (state is DataRepository.LoadState.Loading) state.message else stringResource(R.string.starting_up)
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(msg, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            is DataRepository.LoadState.Failed -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
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

            is DataRepository.LoadState.Ready -> {
                if (nearbyStops.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.no_nearby_stops, searchRadius.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { dataRepository.forceRefresh() },
                        modifier = Modifier.padding(padding)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(nearbyStops, key = { it.id }) { nearby ->
                                StopRow(
                                    nearby = nearby,
                                    isFavorite = favoriteStopIds.contains(nearby.stop.id),
                                    onFavoriteClick = { favoritesRepository.toggleFavoriteStop(nearby.stop.id) },
                                    onClick = {
                                        navController.navigate(
                                            "stop_detail/${nearby.stop.id}/${nearby.distance}"
                                        )
                                    }
                                )
                            }
                            // Timestamp de última actualización
                            item {
                                dataRepository.lastRefresh?.let { last ->
                                    Text(
                                        text = stringResource(
                                            R.string.updated_at,
                                            java.text.DateFormat.getDateTimeInstance(
                                                java.text.DateFormat.SHORT,
                                                java.text.DateFormat.SHORT
                                            ).format(last)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                            // Botón About al pie de la lista
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAboutClick() }
                                        .padding(vertical = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = stringResource(R.string.about_app),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.about_app),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Fila de parada

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopRow(
    nearby: NearbyStop,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StopIcon(
            size = 40.dp,
            isTram = nearby.stop.isTram,
            hasArrivals = nearby.hasArrivals,
            hasAlert = nearby.hasAlert
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(nearby.stop.localizedName, style = MaterialTheme.typography.bodyLarge)
            if (nearby.routes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    nearby.routes.forEach { route ->
                        RouteBadge(
                            shortName = route.shortName,
                            colorHex = route.color,
                            hasAlert = route.hasAlert,
                            outerSize = 32.dp
                        )
                    }
                }
            }
            Text(
                text = distanceLabel(nearby.distance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

internal fun distanceLabel(d: Double): String =
    if (d < 1000) "${d.toLong()} m" else "%.1f km".format(d / 1000)
