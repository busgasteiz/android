package com.jaureguialzo.busgasteiz.data

import java.util.Locale
import java.util.UUID

// MARK: - GTFS estático

data class StopInfo(
    val id: String,
    val name: String,           // Nombre base del GTFS (castellano en Tuvisa, euskera en Euskotren)
    val nameEu: String? = null, // Nombre en euskera de translations.txt (solo Tuvisa)
    val nameEs: String? = null, // Nombre en castellano de translations.txt (solo Tuvisa)
    val lat: Double,
    val lon: Double,
    val isTram: Boolean = false
) {

    /// Nombre adaptado al idioma del sistema.
    /// - Euskera: usa nameEu si está disponible (Tuvisa), o name (Euskotren ya está en euskera).
    ///   Si el nombre en euskera no contiene número pero el castellano termina en número de portal,
    ///   se añade al final para mantener consistencia.
    /// - Castellano: usa nameEs si está disponible, o name.
    /// - Otros: usa name como fallback.
    val localizedName: String
        get() {
            val lang = Locale.getDefault().language
            return when (lang) {
                "eu" -> {
                    val eu = nameEu ?: return name
                    if (!eu.any { it.isDigit() }) {
                        val ref = nameEs ?: name
                        val match = Regex("""\s+\d+$""").find(ref)
                        if (match != null) return eu + match.value
                    }
                    eu
                }
                "es" -> nameEs ?: name
                else -> name
            }
        }
}

data class TripInfo(
    val id: String,
    val routeId: String,
    val headsign: String,
    val serviceId: String
)

data class RouteInfo(
    val id: String,
    val shortName: String,
    val longName: String,
    val color: String
)

data class StopTimeEntry(
    val tripId: String,
    val stopSequence: Int,
    val arrivalSecs: Int
)

// MARK: - Datos GTFS agregados

data class GtfsData(
    val stops: MutableMap<String, StopInfo> = mutableMapOf(),
    val trips: MutableMap<String, TripInfo> = mutableMapOf(),
    val routes: MutableMap<String, RouteInfo> = mutableMapOf(),
    /// stop_id → lista de horarios
    val stopArrivals: MutableMap<String, MutableList<StopTimeEntry>> = mutableMapOf(),
    /// date (yyyyMMdd) → Set<service_id>
    val activeDates: MutableMap<String, MutableSet<String>> = mutableMapOf()
)

// MARK: - Tiempo real

class TripDelayInfo {
    var generalDelay: Int = 0
    var stopDelays: MutableMap<String, Int> = mutableMapOf()
    var vehicleLabel: String = ""
}

// MARK: - Alertas de servicio (GTFS-RT Alerts)

data class ServiceAlert(
    val id: String = UUID.randomUUID().toString(),
    val headerText: String,
    val descriptionText: String
)

class ServiceAlerts {
    val stopAlerts: MutableMap<String, MutableList<ServiceAlert>> = mutableMapOf()
    val routeAlerts: MutableMap<String, MutableList<ServiceAlert>> = mutableMapOf()
    val stopIds: MutableSet<String> = mutableSetOf()
    val routeIds: MutableSet<String> = mutableSetOf()
    val isEmpty: Boolean get() = stopAlerts.isEmpty() && routeAlerts.isEmpty()
}

// MARK: - Resultados de consulta

data class UpcomingArrival(
    val id: String = UUID.randomUUID().toString(),
    val stopId: String,
    val stopName: String,
    val distanceMeters: Double,
    val routeShortName: String,
    val routeLongName: String,
    val routeColor: String,
    val headsign: String,
    val scheduledTimeEpoch: Long,   // milisegundos epoch
    val predictedTimeEpoch: Long,   // milisegundos epoch
    val delaySecs: Int,
    val vehicleLabel: String,
    val isRealTime: Boolean,
    val hasAlert: Boolean = false
)

/// Línea resumida para mostrar en listas de paradas
data class RouteTag(
    val shortName: String,
    val color: String,
    val hasAlert: Boolean = false
)

data class NearbyStop(
    val stop: StopInfo,
    val distance: Double,
    /// Indica si la parada tiene al menos un horario en los datos GTFS
    val hasArrivals: Boolean,
    /// Líneas que pasan por esta parada, ordenadas
    val routes: List<RouteTag>,
    val hasAlert: Boolean = false
) {
    val id: String get() = stop.id
}
