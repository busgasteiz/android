package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.LocationRepository
import com.jaureguialzo.busgasteiz.ui.components.RouteBadge
import com.jaureguialzo.busgasteiz.ui.components.StopIcon

// MARK: - Pantalla de favoritos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    dataRepository: DataRepository,
    locationRepository: LocationRepository,
    favoritesRepository: FavoritesRepository,
    navController: NavController
) {
    val gtfsData by dataRepository.gtfsData.collectAsState()
    val isRefreshing by dataRepository.isRefreshing.collectAsState()
    val favoriteStopIds by favoritesRepository.favoriteStopIds.collectAsState()
    val favoriteRouteKeys by favoritesRepository.favoriteRouteKeys.collectAsState()
    val location by locationRepository.location.collectAsState()
    val activeStopIds by dataRepository.activeStopIds.collectAsState()
    val alerts by dataRepository.serviceAlerts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_favorites)) },
                actions = {
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
        val gtfs = gtfsData
        when {
            favoriteStopIds.isEmpty() && favoriteRouteKeys.isEmpty() -> {
                // Vista vacía
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_favorites),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.no_favorites_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            gtfs == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_data))
                    }
                }
            }
            else -> {
                // Resolver paradas y rutas favoritas contra el GTFS
                val stopRows = favoriteStopIds
                    .mapNotNull { id -> gtfs.stops[id]?.let { stop ->
                        val dist = location?.let { loc ->
                            val dLat = stop.lat - loc.latitude
                            val dLon = stop.lon - loc.longitude
                            Math.sqrt(dLat * dLat + dLon * dLon) * 111_000
                        } ?: 0.0
                        Triple(stop, dist, activeStopIds.contains(id))
                    }}
                    .sortedBy { it.first.localizedName }

                val routeRows = favoritesRepository.parsedRouteKeys
                    .mapNotNull { key ->
                        val stop = gtfs.stops[key.stopId] ?: return@mapNotNull null
                        val route = gtfs.routes.values.firstOrNull { it.shortName == key.routeShortName }
                            ?: gtfs.routes.values.firstOrNull {
                                it.shortName == key.routeShortName.takeWhile { c -> c.isDigit() }
                            }
                        val color = route?.color ?: ""
                        val hasAlert = route?.let { alerts.routeIds.contains(it.id) } ?: false
                        Triple(key, stop, color to hasAlert)
                    }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { dataRepository.forceRefresh() },
                    modifier = Modifier.padding(padding)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Sección de paradas favoritas
                        if (stopRows.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.stops),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(stopRows, key = { it.first.id }) { (stop, dist, hasArrivals) ->
                                ListItem(
                                    headlineContent = { Text(stop.localizedName) },
                                    supportingContent = {
                                        Text(
                                            distanceLabel(dist),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    },
                                    leadingContent = {
                                        StopIcon(
                                            size = 40.dp,
                                            isTram = stop.isTram,
                                            hasArrivals = hasArrivals,
                                            hasAlert = alerts.stopIds.contains(stop.id)
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        navController.navigate("stop_detail/${stop.id}/$dist")
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                            }
                        }

                        // Sección de líneas favoritas
                        if (routeRows.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.lines),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(routeRows, key = { it.first.id }) { (key, stop, colorAndAlert) ->
                                val (color, hasAlert) = colorAndAlert
                                val dist = location?.let { loc ->
                                    val dLat = stop.lat - loc.latitude
                                    val dLon = stop.lon - loc.longitude
                                    Math.sqrt(dLat * dLat + dLon * dLon) * 111_000
                                } ?: 0.0
                                ListItem(
                                    headlineContent = { Text(stop.localizedName) },
                                    supportingContent = {
                                        Text(if (stop.isTram) stringResource(R.string.tram) else stringResource(R.string.bus))
                                    },
                                    leadingContent = {
                                        RouteBadge(
                                            shortName = key.routeShortName,
                                            colorHex = color,
                                            hasAlert = hasAlert,
                                            outerSize = 48.dp
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        navController.navigate(
                                            "route_arrivals/${stop.id}/$dist/${key.routeShortName}/${color.ifEmpty { "000000" }}"
                                        )
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}
