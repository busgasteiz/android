package com.jaureguialzo.busgasteiz.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jaureguialzo.busgasteiz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Date

// MARK: - DataRepository: singleton ViewModel que gestiona la carga y caché de datos GTFS

class DataRepository(private val appContext: Context) : ViewModel() {

    // MARK: - Estado de carga

    sealed class LoadState {
        object Idle : LoadState()
        data class Loading(val message: String) : LoadState()
        object Ready : LoadState()
        data class Failed(val message: String) : LoadState()
    }

    // MARK: - StateFlows públicos

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState

    private val _gtfsData = MutableStateFlow<GtfsData?>(null)
    val gtfsData: StateFlow<GtfsData?> = _gtfsData

    private val _tripDelays = MutableStateFlow<Map<String, TripDelayInfo>>(emptyMap())
    val tripDelays: StateFlow<Map<String, TripDelayInfo>> = _tripDelays

    private val _serviceAlerts = MutableStateFlow(ServiceAlerts())
    val serviceAlerts: StateFlow<ServiceAlerts> = _serviceAlerts

    private val _activeStopIds = MutableStateFlow<Set<String>>(emptySet())
    val activeStopIds: StateFlow<Set<String>> = _activeStopIds

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    var lastRefresh: Date? = null

    private val maxAgeMs: Long = 10 * 60 * 1000L  // 10 minutos

    // MARK: - En modo DEBUG: simulación de alertas

    @Suppress("unused")
    var simulateAlerts: Boolean = false
        set(value) {
            field = value
            val base = baseServiceAlerts
            _serviceAlerts.value = if (value && base != null) injectSimulatedAlerts(base) else base ?: ServiceAlerts()
            _version.value++
        }

    private var baseServiceAlerts: ServiceAlerts? = null

    // MARK: - Rutas de caché

    private val cacheDir: File get() = File(appContext.filesDir, "GTFSCache")
    private val gtfsDir: File get() = File(cacheDir, "GTFS_Data")
    private val pbFile: File get() = File(cacheDir, "tripUpdates.pb")
    private val euskotrenGtfsDir: File get() = File(cacheDir, "Euskotren_Data")
    private val euskotrenPbFile: File get() = File(cacheDir, "euskotrenTripUpdates.pb")
    private val tuvisaAlertsPbFile: File get() = File(cacheDir, "tuvisaAlerts.pb")
    private val euskotrenAlertsPbFile: File get() = File(cacheDir, "euskotrenAlerts.pb")

    // MARK: - API pública

    val needsRefresh: Boolean
        get() {
            val last = lastRefresh ?: return true
            return System.currentTimeMillis() - last.time > maxAgeMs
        }

    fun refreshIfNeeded() {
        if (!needsRefresh && _gtfsData.value != null) return
        viewModelScope.launch { performRefresh() }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val minDelay = launch { delay(1000L) }
            performRefresh()
            minDelay.join()
            _isRefreshing.value = false
        }
    }

    // MARK: - Lógica interna

    private suspend fun performRefresh() {
        if (_loadState.value is LoadState.Loading) return

        val hasData = _gtfsData.value != null

        try {
            println("[DataRepository] Iniciando refresco…")
            withContext(Dispatchers.IO) { cacheDir.mkdirs() }

            // GTFS estático Tuvisa: descargar solo si no está fresco
            if (!isGtfsFresh()) {
                if (!hasData) _loadState.value = LoadState.Loading("Downloading GTFS data…")
                println("[DataRepository] Descargando GTFS ZIP Tuvisa…")
                val zipData = withContext(Dispatchers.IO) { downloadBytes(TUVISA_GTFS_URL) }
                println("[DataRepository] ZIP descargado: ${zipData.size} bytes")
                if (!hasData) _loadState.value = LoadState.Loading("Extracting GTFS data…")
                withContext(Dispatchers.IO) { ZipExtractor.extract(zipData, gtfsDir) }
                println("[DataRepository] ZIP descomprimido")
            } else {
                println("[DataRepository] GTFS Tuvisa en caché y vigente, omitiendo descarga")
            }

            // GTFS estático Euskotren: descargar solo si no está fresco
            if (!isEuskoTranFresh()) {
                if (!hasData) _loadState.value = LoadState.Loading("Downloading tram data…")
                println("[DataRepository] Descargando GTFS ZIP Euskotren…")
                val tramZip = withContext(Dispatchers.IO) { downloadBytes(EUSKOTREN_GTFS_URL) }
                println("[DataRepository] ZIP Euskotren descargado: ${tramZip.size} bytes")
                if (!hasData) _loadState.value = LoadState.Loading("Extracting tram data…")
                withContext(Dispatchers.IO) { ZipExtractor.extract(tramZip, euskotrenGtfsDir) }
                println("[DataRepository] ZIP Euskotren descomprimido")
            } else {
                println("[DataRepository] GTFS Euskotren en caché y vigente, omitiendo descarga")
            }

            // Feed RT Tuvisa: siempre actualizar
            if (!hasData) _loadState.value = LoadState.Loading("Downloading real-time data…")
            println("[DataRepository] Descargando feed RT Tuvisa…")
            val pbData = withContext(Dispatchers.IO) { downloadBytes(TUVISA_RT_URL) }
            println("[DataRepository] Feed RT Tuvisa: ${pbData.size} bytes")
            withContext(Dispatchers.IO) { pbFile.writeBytes(pbData) }

            // Feed RT Euskotren: siempre actualizar
            println("[DataRepository] Descargando feed RT Euskotren…")
            val tramPbData = withContext(Dispatchers.IO) { downloadBytes(EUSKOTREN_RT_URL) }
            println("[DataRepository] Feed RT Euskotren: ${tramPbData.size} bytes")
            withContext(Dispatchers.IO) { euskotrenPbFile.writeBytes(tramPbData) }

            // Alertas Tuvisa: descarga no crítica
            println("[DataRepository] Descargando alertas Tuvisa…")
            try {
                val data = withContext(Dispatchers.IO) { downloadBytes(TUVISA_ALERTS_URL) }
                withContext(Dispatchers.IO) { tuvisaAlertsPbFile.writeBytes(data) }
                println("[DataRepository] Alertas Tuvisa: ${data.size} bytes")
            } catch (e: Exception) {
                println("[DataRepository] Advertencia: alertas Tuvisa no disponibles: $e")
            }

            // Alertas Euskotren: descarga no crítica
            println("[DataRepository] Descargando alertas Euskotren…")
            try {
                val data = withContext(Dispatchers.IO) { downloadBytes(EUSKOTREN_ALERTS_URL) }
                withContext(Dispatchers.IO) { euskotrenAlertsPbFile.writeBytes(data) }
                println("[DataRepository] Alertas Euskotren: ${data.size} bytes")
            } catch (e: Exception) {
                println("[DataRepository] Advertencia: alertas Euskotren no disponibles: $e")
            }

            // Parsear en background
            if (!hasData) _loadState.value = LoadState.Loading("Processing data…")
            val (parsed, delays, alerts) = withContext(Dispatchers.IO) { parseInBackground() }
            println("[DataRepository] Parseados: ${parsed.stops.size} paradas (${parsed.stops.values.count { it.isTram }} tranvía), ${delays.size} trips RT")

            _gtfsData.value = parsed
            _tripDelays.value = delays
            applyAlerts(alerts)
            _activeStopIds.value = computeStopsWithUpcomingArrivals(parsed)
            lastRefresh = Date()
            _version.value++
            _loadState.value = LoadState.Ready
            println("[DataRepository] Listo")

        } catch (e: Exception) {
            println("[DataRepository] ERROR: $e")
            if (_gtfsData.value != null) {
                _loadState.value = LoadState.Ready
            } else {
                val cached = withContext(Dispatchers.IO) { tryLoadFromCache() }
                if (cached != null) {
                    _gtfsData.value = cached.first
                    _tripDelays.value = cached.second
                    applyAlerts(cached.third)
                    _activeStopIds.value = computeStopsWithUpcomingArrivals(cached.first)
                    _version.value++
                    _loadState.value = LoadState.Ready
                    println("[DataRepository] Cargado desde caché")
                } else {
                    _loadState.value = LoadState.Failed(e.message ?: "Unknown error")
                    println("[DataRepository] Sin caché disponible")
                }
            }
        }
    }

    private fun isGtfsFresh(): Boolean {
        val stopsFile = File(gtfsDir, "stops.txt")
        if (!stopsFile.exists()) return false
        return System.currentTimeMillis() - stopsFile.lastModified() < maxAgeMs
    }

    private fun isEuskoTranFresh(): Boolean {
        val stopsFile = File(euskotrenGtfsDir, "stops.txt")
        if (!stopsFile.exists()) return false
        return System.currentTimeMillis() - stopsFile.lastModified() < maxAgeMs
    }

    private fun applyAlerts(alerts: ServiceAlerts) {
        baseServiceAlerts = alerts
        _serviceAlerts.value = if (BuildConfig.DEBUG && simulateAlerts) injectSimulatedAlerts(alerts) else alerts
    }

    private fun injectSimulatedAlerts(base: ServiceAlerts): ServiceAlerts {
        val gtfs = _gtfsData.value ?: return base
        val result = ServiceAlerts()
        // Copiar alertas reales
        for ((k, v) in base.stopAlerts) result.stopAlerts.getOrPut(k) { mutableListOf() }.addAll(v)
        for ((k, v) in base.routeAlerts) result.routeAlerts.getOrPut(k) { mutableListOf() }.addAll(v)
        result.stopIds.addAll(base.stopIds)
        result.routeIds.addAll(base.routeIds)

        val fakeAlerts = listOf(
            Pair("[TEST] Service disruption", "[TEST] Temporary delays due to works on the route. Estimated recovery time: 20 minutes."),
            Pair("[TEST] Route deviation", "[TEST] The bus will follow an alternative route and will not stop at all scheduled stops."),
            Pair("[TEST] Reduced frequency", "[TEST] Due to driver shortage, service frequency is reduced on this line today.")
        )

        for ((stopId, _) in gtfs.stops) {
            if (Math.random() < 0.30) {
                val t = fakeAlerts.random()
                val alert = ServiceAlert(headerText = t.first, descriptionText = t.second)
                result.stopAlerts.getOrPut(stopId) { mutableListOf() }.add(alert)
                result.stopIds.add(stopId)
            }
        }
        for ((routeId, _) in gtfs.routes) {
            if (Math.random() < 0.30) {
                val t = fakeAlerts.random()
                val alert = ServiceAlert(headerText = t.first, descriptionText = t.second)
                result.routeAlerts.getOrPut(routeId) { mutableListOf() }.add(alert)
                result.routeIds.add(routeId)
            }
        }
        return result
    }

    private fun parseInBackground(): Triple<GtfsData, Map<String, TripDelayInfo>, ServiceAlerts> {
        var gtfs = loadGtfs(gtfsDir)
        val tramGtfs = loadEuskoTranGtfs(euskotrenGtfsDir)

        // Fusionar datos del tranvía en el GTFS principal
        for ((id, stop) in tramGtfs.stops) gtfs.stops[id] = stop
        for ((id, trip) in tramGtfs.trips) gtfs.trips[id] = trip
        for ((id, route) in tramGtfs.routes) gtfs.routes[id] = route
        for ((id, arrivals) in tramGtfs.stopArrivals) {
            gtfs.stopArrivals.getOrPut(id) { mutableListOf() }.addAll(arrivals)
        }
        for ((date, svcIds) in tramGtfs.activeDates) {
            gtfs.activeDates.getOrPut(date) { mutableSetOf() }.addAll(svcIds)
        }

        val delays = mutableMapOf<String, TripDelayInfo>()
        if (pbFile.exists()) {
            delays.putAll(loadTripDelays(pbFile.readBytes()))
        }
        if (euskotrenPbFile.exists()) {
            val tramDelays = loadTripDelays(euskotrenPbFile.readBytes())
            delays.putAll(tramDelays)
        }

        val alerts = ServiceAlerts()
        if (tuvisaAlertsPbFile.exists()) {
            val a = loadAlerts(tuvisaAlertsPbFile.readBytes())
            mergeAlerts(alerts, a)
        }
        if (euskotrenAlertsPbFile.exists()) {
            val a = loadAlerts(euskotrenAlertsPbFile.readBytes())
            mergeAlerts(alerts, a)
        }

        return Triple(gtfs, delays, alerts)
    }

    private fun mergeAlerts(into: ServiceAlerts, from: ServiceAlerts) {
        into.stopIds.addAll(from.stopIds)
        into.routeIds.addAll(from.routeIds)
        for ((k, v) in from.stopAlerts) into.stopAlerts.getOrPut(k) { mutableListOf() }.addAll(v)
        for ((k, v) in from.routeAlerts) into.routeAlerts.getOrPut(k) { mutableListOf() }.addAll(v)
    }

    private fun tryLoadFromCache(): Triple<GtfsData, Map<String, TripDelayInfo>, ServiceAlerts>? {
        if (!File(gtfsDir, "stops.txt").exists()) return null
        return parseInBackground()
    }

    private fun downloadBytes(urlString: String): ByteArray {
        val url = URL(urlString)
        val conn = url.openConnection()
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        return conn.getInputStream().use { it.readBytes() }
    }

    // MARK: - Singleton

    companion object {
        @Volatile
        private var instance: DataRepository? = null

        fun getInstance(context: Context): DataRepository =
            instance ?: synchronized(this) {
                instance ?: DataRepository(context.applicationContext).also { instance = it }
            }

        // URLs de datos
        private const val TUVISA_GTFS_URL =
            "https://www.vitoria-gasteiz.org/we001/http/vgTransit/google_transit.zip"
        private const val TUVISA_RT_URL =
            "https://www.vitoria-gasteiz.org/we001/http/vgTransit/realTime/tripUpdates.pb"
        private const val TUVISA_ALERTS_URL =
            "https://opendata.euskadi.eus/transport/moveuskadi/tuvisa/gtfsrt_tuvisa_alerts.pb"
        private const val EUSKOTREN_GTFS_URL =
            "https://opendata.euskadi.eus/transport/moveuskadi/euskotren/gtfs_euskotren.zip"
        private const val EUSKOTREN_RT_URL =
            "https://opendata.euskadi.eus/transport/moveuskadi/euskotren/gtfsrt_euskotren_trip_updates.pb"
        private const val EUSKOTREN_ALERTS_URL =
            "https://opendata.euskadi.eus/transport/moveuskadi/euskotren/gtfsrt_euskotren_alerts.pb"
    }
}

// MARK: - Factory para ViewModelProvider

class DataRepositoryFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DataRepository.getInstance(context) as T
    }
}
