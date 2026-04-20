package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.StopInfo
import com.jaureguialzo.busgasteiz.data.UpcomingArrival
import com.jaureguialzo.busgasteiz.data.computeArrivals
import com.jaureguialzo.busgasteiz.data.computeNextArrivals
import com.jaureguialzo.busgasteiz.data.formatTime
import com.jaureguialzo.busgasteiz.data.minutesUntil
import com.jaureguialzo.busgasteiz.ui.components.AlertRow
import com.jaureguialzo.busgasteiz.ui.components.RouteBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Contenido del detalle de parada (usado tanto en pantalla push como en bottom sheet)

@Composable
fun StopDetailContent(
    stop: StopInfo,
    distance: Double,
    dataRepository: DataRepository,
    favoritesRepository: FavoritesRepository,
    navController: NavController,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val version by dataRepository.version.collectAsState()
    val favoriteStopIds by favoritesRepository.favoriteStopIds.collectAsState()
    val isFavorite = favoriteStopIds.contains(stop.id)

    val arrivals = remember { mutableStateListOf<UpcomingArrival>() }
    val nextArrivals = remember { mutableStateListOf<UpcomingArrival>() }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Recompute al cambiar versión de datos
    fun recompute() {
        val gtfs = dataRepository.gtfsData.value ?: return
        val delays = dataRepository.tripDelays.value
        val alerts = dataRepository.serviceAlerts.value
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                computeArrivals(stop.id, distance, gtfs, delays, alerts)
            }
            val next = if (result.isEmpty()) withContext(Dispatchers.Default) {
                computeNextArrivals(stop.id, distance, gtfs, delays, alerts)
            } else emptyList()
            arrivals.clear()
            arrivals.addAll(result)
            nextArrivals.clear()
            nextArrivals.addAll(next)
        }
    }

    LaunchedEffect(version) { recompute() }

    // Actualizar reloj cada 30 segundos
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
            recompute()
        }
    }

    val stopAlerts = dataRepository.serviceAlerts.value.stopAlerts[stop.id] ?: emptyList()

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        // Header con nombre, distancia y botón favorito (solo en bottom sheet)
        if (onClose != null) item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stop.localizedName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        distanceLabel(distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                // Botón favorito
                IconButton(onClick = { favoritesRepository.toggleFavoriteStop(stop.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                // Botón cerrar (solo en bottom sheet)
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }
            HorizontalDivider()
        }

        // Alertas de la parada
        if (stopAlerts.isNotEmpty()) {
            items(stopAlerts) { alert ->
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
                        stringResource(R.string.no_arrivals),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            // "Next scheduled services"
            if (nextArrivals.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.next_scheduled),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(nextArrivals) { arrival ->
                    ArrivalRow(
                        arrival = arrival,
                        now = now,
                        onClick = {
                            navController.navigate(
                                "route_arrivals/${stop.id}/${distance}/${arrival.routeShortName}/${arrival.routeColor.ifEmpty { "000000" }}"
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        } else {
            items(arrivals) { arrival ->
                ArrivalRow(
                    arrival = arrival,
                    now = now,
                    onClick = {
                        navController.navigate(
                            "route_arrivals/${stop.id}/${distance}/${arrival.routeShortName}/${arrival.routeColor.ifEmpty { "000000" }}"
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// MARK: - Fila de llegada

@Composable
fun ArrivalRow(
    arrival: UpcomingArrival,
    now: Long = System.currentTimeMillis(),
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteBadge(
            shortName = arrival.routeShortName,
            colorHex = arrival.routeColor,
            hasAlert = arrival.hasAlert,
            outerSize = 48.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(arrival.headsign, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (arrival.isRealTime) {
                if (arrival.delaySecs != 0) {
                    val sign = if (arrival.delaySecs > 0) "+" else "-"
                    val abs = Math.abs(arrival.delaySecs)
                    val delayStr = if (abs < 60) "$sign${abs}s" else "$sign${abs / 60}m${abs % 60}s"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (arrival.delaySecs > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${formatTime(arrival.scheduledTimeEpoch)} · $delayStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (arrival.delaySecs > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.live), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.scheduled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeLabel(arrival.predictedTimeEpoch, now, stringResource(R.string.now)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatTime(arrival.predictedTimeEpoch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

private fun timeLabel(epochMs: Long, now: Long, nowStr: String): String {
    val mins = minutesUntil(epochMs, now)
    return when {
        mins < 1 -> nowStr
        mins == 1 -> "1 min"
        mins < 60 -> "$mins min"
        else -> {
            val h = mins / 60
            val m = mins % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }
}
