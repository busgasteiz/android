package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.jaureguialzo.busgasteiz.data.StopInfo
import com.jaureguialzo.busgasteiz.data.UpcomingArrival
import com.jaureguialzo.busgasteiz.data.computeArrivals
import com.jaureguialzo.busgasteiz.data.computeNextArrivals
import com.jaureguialzo.busgasteiz.ui.components.AlertRow
import com.jaureguialzo.busgasteiz.ui.components.RouteBadge
import com.jaureguialzo.busgasteiz.ui.components.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Pantalla de llegadas filtradas por línea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteArrivalsScreen(
    stop: StopInfo,
    distance: Double,
    routeShortName: String,
    routeColor: String,
    dataRepository: DataRepository,
    favoritesRepository: FavoritesRepository,
    navController: NavController
) {
    val version by dataRepository.version.collectAsState()
    val favoriteRouteKeys by favoritesRepository.favoriteRouteKeys.collectAsState()
    val isFavorite = favoriteRouteKeys.contains("${stop.id}::$routeShortName")

    val arrivals = remember { mutableStateListOf<UpcomingArrival>() }
    var nextArrival by remember { mutableStateOf<UpcomingArrival?>(null) }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    fun recompute() {
        val gtfs = dataRepository.gtfsData.value ?: return
        val delays = dataRepository.tripDelays.value
        val alerts = dataRepository.serviceAlerts.value
        scope.launch {
            val all = withContext(Dispatchers.Default) {
                computeArrivals(stop.id, distance, gtfs, delays, alerts)
            }
            val filtered = all.filter { it.routeShortName == routeShortName }
            val next = if (filtered.isEmpty()) withContext(Dispatchers.Default) {
                computeNextArrivals(stop.id, distance, gtfs, delays, alerts)
            }.firstOrNull { it.routeShortName == routeShortName } else null
            arrivals.clear()
            arrivals.addAll(filtered)
            nextArrival = next
        }
    }

    LaunchedEffect(version) { recompute() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
            recompute()
        }
    }

    val routeAlerts = run {
        val gtfs = dataRepository.gtfsData.value ?: return@run emptyList()
        val route = gtfs.routes.values.firstOrNull { it.shortName == routeShortName }
            ?: gtfs.routes.values.firstOrNull {
                it.shortName == routeShortName.takeWhile { c -> c.isDigit() }
            }
        route?.let { dataRepository.serviceAlerts.value.routeAlerts[it.id] } ?: emptyList()
    }

    val barColor = parseHexColor(routeColor)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RouteBadge(
                            shortName = routeShortName,
                            colorHex = routeColor,
                            outerSize = 36.dp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stop.localizedName, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        favoritesRepository.toggleFavoriteRoute(stop.id, routeShortName)
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // Alertas de la línea
            if (routeAlerts.isNotEmpty()) {
                items(routeAlerts) { alert ->
                    AlertRow(alert = alert, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            if (arrivals.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.no_more_arrivals, routeShortName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                nextArrival?.let { next ->
                    item {
                        Text(
                            stringResource(R.string.next_scheduled),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        ArrivalRow(arrival = next, now = now)
                    }
                }
            } else {
                items(arrivals) { arrival ->
                    ArrivalRow(arrival = arrival, now = now)
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
