package com.jaureguialzo.busgasteiz.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// MARK: - Utilidades de fecha y distancia

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

// MARK: - Formateadores compartidos (SimpleDateFormat es caro de crear — singletons)

private object GtfsDateFormats {
    val MADRID_TZ: TimeZone = TimeZone.getTimeZone("Europe/Madrid")

    val gtfsDateFmt: SimpleDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = MADRID_TZ
    }

    val timeFmt: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = MADRID_TZ
    }
}

fun dateString(date: Date): String = GtfsDateFormats.gtfsDateFmt.format(date)

/// Convierte (fecha de servicio yyyyMMdd, segundos desde medianoche) → Date epoch en ms.
fun scheduledDateEpoch(serviceDate: String, secondsFromMidnight: Int): Long? {
    val base = try {
        GtfsDateFormats.gtfsDateFmt.parse(serviceDate)?.time ?: return null
    } catch (e: Exception) {
        return null
    }
    return base + secondsFromMidnight * 1000L
}

fun formatTime(epochMs: Long): String = GtfsDateFormats.timeFmt.format(Date(epochMs))

fun minutesUntil(epochMs: Long, now: Long = System.currentTimeMillis()): Int =
    ((epochMs - now) / 60000.0).let { Math.round(it).toInt() }

/// Determina la fecha de servicio (hoy o ayer) que hace que el horario caiga en la ventana.
fun resolveServiceDate(
    trip: TripInfo,
    arrivalSecs: Int,
    activeServiceIds: Set<String>,
    yesterdayActiveIds: Set<String>,
    today: String,
    yesterday: String,
    windowStartMs: Long,
    windowEndMs: Long
): String? {
    val candidates = listOf(
        Pair(today, activeServiceIds),
        Pair(yesterday, yesterdayActiveIds)
    )
    for ((date, validIds) in candidates) {
        val svcOk = validIds.contains(trip.serviceId) || trip.serviceId == "UNDEFINED"
        if (!svcOk) continue
        val schTime = scheduledDateEpoch(date, arrivalSecs) ?: continue
        if (schTime >= windowStartMs && schTime <= windowEndMs) return date
    }
    return null
}

// MARK: - Parser CSV mínimo (respeta comillas dobles)

fun splitCsv(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    for (c in line) {
        when {
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { fields.add(current.toString()); current.clear() }
            else -> current.append(c)
        }
    }
    fields.add(current.toString())
    return fields
}

private fun idx(headers: List<String>, name: String): Int? =
    headers.indexOf(name).takeIf { it >= 0 }

private fun get(row: List<String>, i: Int?): String =
    if (i != null && i < row.size) row[i] else ""

private fun parseSecs(s: String): Int {
    val parts = s.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return -1
    return parts[0] * 3600 + parts[1] * 60 + parts[2]
}

// MARK: - Cargador GTFS estático (Tuvisa)

fun loadGtfs(folder: File): GtfsData {
    val g = GtfsData()

    // routes.txt
    readCsvFile(File(folder, "routes.txt")) { headers, row ->
        val id = get(row, idx(headers, "route_id")); if (id.isEmpty()) return@readCsvFile
        g.routes[id] = RouteInfo(
            id = id,
            shortName = get(row, idx(headers, "route_short_name")),
            longName = get(row, idx(headers, "route_long_name")),
            color = get(row, idx(headers, "route_color"))
        )
    }

    // trips.txt
    readCsvFile(File(folder, "trips.txt")) { headers, row ->
        val id = get(row, idx(headers, "trip_id")); if (id.isEmpty()) return@readCsvFile
        g.trips[id] = TripInfo(
            id = id,
            routeId = get(row, idx(headers, "route_id")),
            headsign = get(row, idx(headers, "trip_headsign")),
            serviceId = get(row, idx(headers, "service_id"))
        )
    }

    // stops.txt
    readCsvFile(File(folder, "stops.txt")) { headers, row ->
        val id = get(row, idx(headers, "stop_id")); if (id.isEmpty()) return@readCsvFile
        g.stops[id] = StopInfo(
            id = id,
            name = get(row, idx(headers, "stop_name")),
            lat = get(row, idx(headers, "stop_lat")).toDoubleOrNull() ?: 0.0,
            lon = get(row, idx(headers, "stop_lon")).toDoubleOrNull() ?: 0.0
        )
    }

    // translations.txt — nombres localizados de paradas (eu y es)
    readCsvFile(File(folder, "translations.txt")) { headers, row ->
        if (get(row, idx(headers, "table_name")) != "stops") return@readCsvFile
        if (get(row, idx(headers, "field_name")) != "stop_name") return@readCsvFile
        val lang = get(row, idx(headers, "language"))
        val rid = get(row, idx(headers, "record_id"))
        if (rid.isEmpty() || lang.isEmpty()) return@readCsvFile
        val translation = get(row, idx(headers, "translation"))
        val existing = g.stops[rid] ?: return@readCsvFile
        when (lang) {
            "eu" -> g.stops[rid] = existing.copy(nameEu = translation)
            "es" -> g.stops[rid] = existing.copy(nameEs = translation)
        }
    }

    // calendar_dates.txt (solo exception_type=1, Tuvisa usa este fichero en vez de calendar.txt)
    readCsvFile(File(folder, "calendar_dates.txt")) { headers, row ->
        val svcId = get(row, idx(headers, "service_id"))
        val date = get(row, idx(headers, "date"))
        val ex = get(row, idx(headers, "exception_type"))
        if (svcId.isEmpty() || date.isEmpty() || ex != "1") return@readCsvFile
        g.activeDates.getOrPut(date) { mutableSetOf() }.add(svcId)
    }

    // stop_times.txt — índice por stop_id
    readCsvFile(File(folder, "stop_times.txt")) { headers, row ->
        val tid = get(row, idx(headers, "trip_id"))
        val sid = get(row, idx(headers, "stop_id"))
        if (tid.isEmpty() || sid.isEmpty()) return@readCsvFile
        var secs = parseSecs(get(row, idx(headers, "arrival_time")))
        if (secs < 0) secs = parseSecs(get(row, idx(headers, "departure_time")))
        if (secs < 0) return@readCsvFile
        val entry = StopTimeEntry(
            tripId = tid,
            stopSequence = get(row, idx(headers, "stop_sequence")).toIntOrNull() ?: 0,
            arrivalSecs = secs
        )
        g.stopArrivals.getOrPut(sid) { mutableListOf() }.add(entry)
    }

    return g
}

// MARK: - Lector de fechas activas desde calendar.txt (Euskotren usa este formato)

fun loadActiveDatesFromCalendar(folder: File): Map<String, MutableSet<String>> {
    val activeDates = mutableMapOf<String, MutableSet<String>>()
    val tz = TimeZone.getTimeZone("Europe/Madrid")
    val cal = Calendar.getInstance(tz)
    val df = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }

    // calendar.txt: expande horario semanal en fechas individuales
    val dayColumns = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    readCsvFile(File(folder, "calendar.txt")) { headers, row ->
        val svcId = get(row, idx(headers, "service_id")); if (svcId.isEmpty()) return@readCsvFile
        val flags = dayColumns.map { get(row, idx(headers, it)) == "1" }
        val startDate = df.parse(get(row, idx(headers, "start_date"))) ?: return@readCsvFile
        val endDate = df.parse(get(row, idx(headers, "end_date"))) ?: return@readCsvFile
        cal.time = startDate
        while (!cal.time.after(endDate)) {
            // Weekday: Calendar usa 1=Dom, 2=Lun, …, 7=Sáb → (wd-2+7)%7 = 0=Lun…6=Dom
            val wd = (cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7
            if (flags[wd]) {
                activeDates.getOrPut(df.format(cal.time)) { mutableSetOf() }.add(svcId)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    // calendar_dates.txt: aplica adiciones (type=1) y eliminaciones (type=2)
    readCsvFile(File(folder, "calendar_dates.txt")) { headers, row ->
        val svcId = get(row, idx(headers, "service_id"))
        val date = get(row, idx(headers, "date"))
        val ex = get(row, idx(headers, "exception_type"))
        if (svcId.isEmpty() || date.isEmpty()) return@readCsvFile
        when (ex) {
            "1" -> activeDates.getOrPut(date) { mutableSetOf() }.add(svcId)
            "2" -> activeDates[date]?.remove(svcId)
        }
    }

    return activeDates
}

// MARK: - Cargador GTFS Euskotren Tranvía Vitoria-Gasteiz

/// Carga únicamente las paradas, viajes y horarios del tranvía de Vitoria-Gasteiz
/// (operador EUS_TrGa: líneas TG1, TG2) desde el GTFS estático de Euskotren.
/// Las paradas resultantes llevan isTram = true.
fun loadEuskoTranGtfs(folder: File): GtfsData {
    val g = GtfsData()
    val vitoriaTramAgency = "ES:Euskotren:Operator:EUS_TrGa:"

    // 1. routes.txt — filtrar al operador del tranvía de Vitoria
    val tramRouteIds = mutableSetOf<String>()
    readCsvFile(File(folder, "routes.txt")) { headers, row ->
        val id = get(row, idx(headers, "route_id")); if (id.isEmpty()) return@readCsvFile
        val shortName = get(row, idx(headers, "route_short_name"))
        // Solo rutas del tranvía de Gasteiz con nombre visible (TG1, TG2)
        if (get(row, idx(headers, "agency_id")) == vitoriaTramAgency && shortName.startsWith("TG")) {
            tramRouteIds.add(id)
            g.routes[id] = RouteInfo(
                id = id,
                shortName = shortName,
                longName = get(row, idx(headers, "route_long_name")),
                color = get(row, idx(headers, "route_color"))
            )
        }
    }

    // 2. trips.txt — filtrar a rutas de tranvía
    val tramTripIds = mutableSetOf<String>()
    readCsvFile(File(folder, "trips.txt")) { headers, row ->
        val id = get(row, idx(headers, "trip_id")); if (id.isEmpty()) return@readCsvFile
        val routeId = get(row, idx(headers, "route_id"))
        if (tramRouteIds.contains(routeId)) {
            tramTripIds.add(id)
            g.trips[id] = TripInfo(
                id = id,
                routeId = routeId,
                headsign = get(row, idx(headers, "trip_headsign")),
                serviceId = get(row, idx(headers, "service_id"))
            )
        }
    }

    // 2b. stops.txt — mapa Quay → StopPlace para agrupar andenes
    val quayToStopPlace = mutableMapOf<String, String>()
    val stopPlaceInfo = mutableMapOf<String, Triple<String, Double, Double>>()
    readCsvFile(File(folder, "stops.txt")) { headers, row ->
        val id = get(row, idx(headers, "stop_id")); if (id.isEmpty()) return@readCsvFile
        val locType = get(row, idx(headers, "location_type"))
        if (locType == "1") {
            // StopPlace: guardar datos como parada canónica
            val lat = get(row, idx(headers, "stop_lat")).toDoubleOrNull() ?: 0.0
            val lon = get(row, idx(headers, "stop_lon")).toDoubleOrNull() ?: 0.0
            stopPlaceInfo[id] = Triple(get(row, idx(headers, "stop_name")), lat, lon)
        } else if (locType == "0") {
            // Quay (andén): apuntar a su StopPlace padre
            val parent = get(row, idx(headers, "parent_station"))
            if (parent.isNotEmpty()) quayToStopPlace[id] = parent
        }
    }

    // 3. stop_times.txt — filtrar a viajes de tranvía; traducir Quay → StopPlace
    readCsvFile(File(folder, "stop_times.txt")) { headers, row ->
        val tid = get(row, idx(headers, "trip_id"))
        val quayId = get(row, idx(headers, "stop_id"))
        if (tid.isEmpty() || quayId.isEmpty() || !tramTripIds.contains(tid)) return@readCsvFile
        // Resolver al StopPlace padre para agrupar andenes del mismo apeadero
        val sid = quayToStopPlace[quayId] ?: quayId
        var secs = parseSecs(get(row, idx(headers, "arrival_time")))
        if (secs < 0) secs = parseSecs(get(row, idx(headers, "departure_time")))
        if (secs < 0) return@readCsvFile
        g.stopArrivals.getOrPut(sid) { mutableListOf() }.add(
            StopTimeEntry(
                tripId = tid,
                stopSequence = get(row, idx(headers, "stop_sequence")).toIntOrNull() ?: 0,
                arrivalSecs = secs
            )
        )
    }

    // 4. stops — crear una entrada por StopPlace que tenga stop_times
    for (spId in g.stopArrivals.keys) {
        val info = stopPlaceInfo[spId] ?: continue
        g.stops[spId] = StopInfo(id = spId, name = info.first, lat = info.second, lon = info.third, isTram = true)
    }

    // 5. Fechas activas desde calendar.txt + calendar_dates.txt
    val tramDates = loadActiveDatesFromCalendar(folder)
    for ((date, svcIds) in tramDates) {
        g.activeDates.getOrPut(date) { mutableSetOf() }.addAll(svcIds)
    }

    return g
}

// MARK: - Decodificador GTFS-RT TripUpdates (protobuf)
// ⚠️ El feed de Tuvisa tiene campos no estándar: field 2 = StopTimeUpdate, field 3 = VehicleDescriptor
// (al revés que en el estándar GTFS-RT). El feed de Euskotren sigue el estándar.

fun loadTripDelays(data: ByteArray): Map<String, TripDelayInfo> {
    val delays = mutableMapOf<String, TripDelayInfo>()
    val r = ProtoReader(data)
    while (r.hasMore) {
        val (field, wire) = r.readTag() ?: break
        when (field) {
            1 -> r.readLengthDelimited()  // header, ignorar
            2 -> r.readLengthDelimited()?.let { parseTUEntity(it, delays) }
            else -> r.skipField(wire)
        }
    }
    return delays
}

private fun parseTUEntity(data: ByteArray, delays: MutableMap<String, TripDelayInfo>) {
    val r = ProtoReader(data)
    var tuData: ByteArray? = null
    var deleted = false
    while (r.hasMore) {
        val (field, wire) = r.readTag() ?: break
        when (field) {
            1 -> r.readLengthDelimited()  // id
            2 -> r.readVarint()?.let { deleted = it != 0uL }
            3 -> tuData = r.readLengthDelimited()
            else -> r.skipField(wire)
        }
    }
    if (deleted || tuData == null) return

    val tu = ProtoReader(tuData)
    var tripId = ""
    val info = TripDelayInfo()
    while (tu.hasMore) {
        val (field, wire) = tu.readTag() ?: break
        when (field) {
            1 -> {  // TripDescriptor
                tu.readLengthDelimited()?.let { d ->
                    val td = ProtoReader(d)
                    while (td.hasMore) {
                        val (f2, w2) = td.readTag() ?: break
                        when (f2) {
                            1 -> td.readLengthDelimited()?.let { tripId = String(it, Charsets.UTF_8) }
                            else -> td.skipField(w2)
                        }
                    }
                }
            }
            // ⚠️ Campo 2 en el feed de Tuvisa = StopTimeUpdate (estándar: VehicleDescriptor)
            2 -> {
                tu.readLengthDelimited()?.let { d ->
                    val stu = ProtoReader(d)
                    var stopId = ""
                    var arrDelay = 0
                    while (stu.hasMore) {
                        val (f2, w2) = stu.readTag() ?: break
                        when (f2) {
                            1 -> stu.readVarint()  // stop_sequence
                            2 -> {  // StopTimeEvent (arrival)
                                stu.readLengthDelimited()?.let { d2 ->
                                    val ste = ProtoReader(d2)
                                    while (ste.hasMore) {
                                        val (f3, w3) = ste.readTag() ?: break
                                        when (f3) {
                                            1 -> ste.readVarint()?.let { v ->
                                                // Decodificar como zigzag signed int32
                                                arrDelay = zigzagToInt(v)
                                            }
                                            else -> ste.skipField(w3)
                                        }
                                    }
                                }
                            }
                            3 -> stu.readLengthDelimited()  // departure, ignorar
                            4 -> stu.readLengthDelimited()?.let { stopId = String(it, Charsets.UTF_8) }
                            else -> stu.skipField(w2)
                        }
                    }
                    if (stopId.isNotEmpty()) info.stopDelays[stopId] = arrDelay
                }
            }
            // ⚠️ Campo 3 en el feed de Tuvisa = VehicleDescriptor (estándar: StopTimeUpdate)
            3 -> {
                tu.readLengthDelimited()?.let { d ->
                    val vd = ProtoReader(d)
                    while (vd.hasMore) {
                        val (f2, w2) = vd.readTag() ?: break
                        when (f2) {
                            2 -> vd.readLengthDelimited()?.let { info.vehicleLabel = String(it, Charsets.UTF_8) }
                            else -> vd.skipField(w2)
                        }
                    }
                }
            }
            // Campo 5 = generalDelay (retraso global del viaje)
            5 -> tu.readVarint()?.let { v ->
                info.generalDelay = zigzagToInt(v)
            }
            else -> tu.skipField(wire)
        }
    }
    if (tripId.isNotEmpty()) delays[tripId] = info
}

/// Convierte un ULong varint codificado en zigzag a Int con signo
private fun zigzagToInt(v: ULong): Int {
    val l = v.toLong()
    return ((l ushr 1) xor -(l and 1L)).toInt()
}

// MARK: - Alertas de servicio (GTFS-RT Alerts)

fun loadAlerts(data: ByteArray): ServiceAlerts {
    val alerts = ServiceAlerts()
    val r = ProtoReader(data)
    while (r.hasMore) {
        val (field, wire) = r.readTag() ?: break
        when (field) {
            1 -> r.readLengthDelimited()  // header, ignorar
            2 -> r.readLengthDelimited()?.let { parseAlertEntity(it, alerts) }
            else -> r.skipField(wire)
        }
    }
    return alerts
}

private fun parseAlertEntity(data: ByteArray, alerts: ServiceAlerts) {
    val r = ProtoReader(data)
    var alertData: ByteArray? = null
    var deleted = false
    while (r.hasMore) {
        val (field, wire) = r.readTag() ?: break
        when (field) {
            1 -> r.readLengthDelimited()                             // id
            2 -> r.readVarint()?.let { deleted = it != 0uL }         // is_deleted
            5 -> alertData = r.readLengthDelimited()                  // Alert (campo 5, no estándar 3)
            else -> r.skipField(wire)
        }
    }
    if (deleted || alertData == null) return

    val stopIds = mutableListOf<String>()
    val routeIds = mutableListOf<String>()
    var headerParts = listOf<Pair<String, String>>()
    var descParts = listOf<Pair<String, String>>()

    val ar = ProtoReader(alertData)
    while (ar.hasMore) {
        val (field, wire) = ar.readTag() ?: break
        when (field) {
            1 -> ar.readLengthDelimited()  // active_period, ignorar
            2 -> {  // informed_entity (EntitySelector)
                ar.readLengthDelimited()?.let { d ->
                    val es = ProtoReader(d)
                    while (es.hasMore) {
                        val (f2, w2) = es.readTag() ?: break
                        when (f2) {
                            2 -> es.readLengthDelimited()?.let { dd ->
                                String(dd, Charsets.UTF_8).takeIf { it.isNotEmpty() }?.let { routeIds.add(it) }
                            }
                            5 -> es.readLengthDelimited()?.let { dd ->
                                String(dd, Charsets.UTF_8).takeIf { it.isNotEmpty() }?.let { stopIds.add(it) }
                            }
                            else -> es.skipField(w2)
                        }
                    }
                }
            }
            6 -> ar.readLengthDelimited()?.let { headerParts = parseTranslatedString(it) }
            7 -> ar.readLengthDelimited()?.let { descParts = parseTranslatedString(it) }
            else -> ar.skipField(wire)
        }
    }

    val header = bestTranslation(headerParts)
    val desc = bestTranslation(descParts)
    if (header.isEmpty() && desc.isEmpty()) return

    val alert = ServiceAlert(headerText = header, descriptionText = desc)
    for (sid in stopIds) {
        alerts.stopAlerts.getOrPut(sid) { mutableListOf() }.add(alert)
        alerts.stopIds.add(sid)
    }
    for (rid in routeIds) {
        alerts.routeAlerts.getOrPut(rid) { mutableListOf() }.add(alert)
        alerts.routeIds.add(rid)
    }
}

private fun parseTranslatedString(data: ByteArray): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val r = ProtoReader(data)
    while (r.hasMore) {
        val (field, wire) = r.readTag() ?: break
        when (field) {
            1 -> r.readLengthDelimited()?.let { d ->
                val tr = ProtoReader(d)
                var text = ""
                var lang = ""
                while (tr.hasMore) {
                    val (f2, w2) = tr.readTag() ?: break
                    when (f2) {
                        1 -> tr.readLengthDelimited()?.let { text = String(it, Charsets.UTF_8) }
                        2 -> tr.readLengthDelimited()?.let { lang = String(it, Charsets.UTF_8) }
                        else -> tr.skipField(w2)
                    }
                }
                if (text.isNotEmpty()) result.add(Pair(lang, text))
            }
            else -> r.skipField(wire)
        }
    }
    return result
}

private fun bestTranslation(parts: List<Pair<String, String>>): String {
    if (parts.isEmpty()) return ""
    val deviceLang = Locale.getDefault().language
    parts.firstOrNull { it.first == deviceLang }?.let { return it.second }
    parts.firstOrNull { it.first == "es" }?.let { return it.second }
    return parts[0].second
}

// MARK: - Motor de consulta

/// Calcula de una sola pasada el conjunto de stop_id con al menos una llegada
/// prevista en los próximos windowMinutes minutos.
/// Usa aritmética epoch pura (sin DateFormatter en el bucle) para ser rápido.
fun computeStopsWithUpcomingArrivals(gtfsData: GtfsData, windowMinutes: Int = 60): Set<String> {
    val nowMs = System.currentTimeMillis()
    val tz = TimeZone.getTimeZone("Europe/Madrid")

    fun midnightEpochMs(daysAgo: Int): Long {
        val cal = Calendar.getInstance(tz)
        cal.add(Calendar.DAY_OF_MONTH, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val todayMidnightMs = midnightEpochMs(0)
    val yesterdayMidnightMs = midnightEpochMs(1)
    val windowStartMs = nowMs - 60_000L
    val windowEndMs = nowMs + windowMinutes * 60_000L

    val today = dateString(Date(nowMs))
    val yesterday = dateString(Date(nowMs - 86_400_000L))
    val activeIds: Set<String> = gtfsData.activeDates[today] ?: emptySet()
    val yesterdayIds: Set<String> = gtfsData.activeDates[yesterday] ?: emptySet()

    val result = mutableSetOf<String>()
    outer@ for ((stopId, entries) in gtfsData.stopArrivals) {
        if (result.contains(stopId)) continue
        for (entry in entries) {
            val trip = gtfsData.trips[entry.tripId] ?: continue
            val svc = trip.serviceId
            // Comprobar hoy
            if (activeIds.contains(svc) || svc == "UNDEFINED") {
                val t = todayMidnightMs + entry.arrivalSecs * 1000L
                if (t >= windowStartMs && t <= windowEndMs) {
                    result.add(stopId)
                    continue@outer
                }
            }
            // Comprobar viajes que cruzaron medianoche
            if (yesterdayIds.contains(svc) || svc == "UNDEFINED") {
                val t = yesterdayMidnightMs + entry.arrivalSecs * 1000L
                if (t >= windowStartMs && t <= windowEndMs) {
                    result.add(stopId)
                    continue@outer
                }
            }
        }
    }
    return result
}

/// Líneas únicas que pasan por una parada, incluyendo variantes (5A/5B/5C),
/// ordenadas numéricamente/alfabéticamente.
fun routesForStop(stopId: String, gtfsData: GtfsData, alerts: ServiceAlerts = ServiceAlerts()): List<RouteTag> {
    val entries = gtfsData.stopArrivals[stopId] ?: return emptyList()
    val seen = mutableSetOf<String>()
    val tags = mutableListOf<RouteTag>()
    for (entry in entries) {
        val trip = gtfsData.trips[entry.tripId] ?: continue
        val route = gtfsData.routes[trip.routeId] ?: continue
        val suffix = variantSuffix(trip.routeId, trip.headsign)
        val displayName = route.shortName + (suffix ?: "")
        if (!seen.add(displayName)) continue
        val hasAlert = alerts.routeIds.contains(trip.routeId)
        tags.add(RouteTag(shortName = displayName, color = route.color, hasAlert = hasAlert))
    }
    tags.sortWith(Comparator { a, b ->
        val aNum = a.shortName.takeWhile { it.isDigit() }.toIntOrNull()
        val bNum = b.shortName.takeWhile { it.isDigit() }.toIntOrNull()
        when {
            aNum != null && bNum != null -> if (aNum != bNum) aNum - bNum else a.shortName.compareTo(b.shortName)
            aNum != null -> -1
            bNum != null -> 1
            else -> a.shortName.compareTo(b.shortName)
        }
    })
    return tags
}

/// Paradas dentro del radio, ordenadas por distancia.
fun computeNearbyStops(
    lat: Double,
    lon: Double,
    radius: Double,
    gtfsData: GtfsData,
    activeStopIds: Set<String>,
    alerts: ServiceAlerts = ServiceAlerts()
): List<NearbyStop> =
    gtfsData.stops.values.mapNotNull { stop ->
        val d = haversine(lat, lon, stop.lat, stop.lon)
        if (d > radius) return@mapNotNull null
        val routeTags = routesForStop(stop.id, gtfsData, alerts)
        val hasAlert = alerts.stopIds.contains(stop.id) || routeTags.any { it.hasAlert }
        NearbyStop(
            stop = stop,
            distance = d,
            hasArrivals = activeStopIds.contains(stop.id),
            routes = routeTags,
            hasAlert = hasAlert
        )
    }.sortedBy { it.distance }

/// Paradas dentro del área visible del mapa (bounding box), ordenadas por distancia al punto de referencia.
fun computeStopsInBounds(
    minLat: Double, maxLat: Double,
    minLon: Double, maxLon: Double,
    refLat: Double, refLon: Double,
    gtfsData: GtfsData,
    activeStopIds: Set<String>,
    alerts: ServiceAlerts = ServiceAlerts()
): List<NearbyStop> =
    gtfsData.stops.values.mapNotNull { stop ->
        if (stop.lat < minLat || stop.lat > maxLat || stop.lon < minLon || stop.lon > maxLon) return@mapNotNull null
        val d = haversine(refLat, refLon, stop.lat, stop.lon)
        val routeTags = routesForStop(stop.id, gtfsData, alerts)
        val hasAlert = alerts.stopIds.contains(stop.id) || routeTags.any { it.hasAlert }
        NearbyStop(
            stop = stop,
            distance = d,
            hasArrivals = activeStopIds.contains(stop.id),
            routes = routeTags,
            hasAlert = hasAlert
        )
    }.sortedBy { it.distance }

/// Llegadas previstas para una parada en los próximos windowMinutes minutos.
fun computeArrivals(
    stopId: String,
    distance: Double,
    gtfsData: GtfsData,
    delays: Map<String, TripDelayInfo>,
    alerts: ServiceAlerts = ServiceAlerts(),
    windowMinutes: Int = 60
): List<UpcomingArrival> {
    val stop = gtfsData.stops[stopId] ?: return emptyList()
    val entries = gtfsData.stopArrivals[stopId] ?: return emptyList()

    val nowMs = System.currentTimeMillis()
    val today = dateString(Date(nowMs))
    val yesterday = dateString(Date(nowMs - 86_400_000L))
    val activeIds: Set<String> = gtfsData.activeDates[today] ?: emptySet()
    val yesterdayIds: Set<String> = gtfsData.activeDates[yesterday] ?: emptySet()
    val windowStartMs = nowMs - 60_000L
    val windowEndMs = nowMs + windowMinutes * 60_000L

    val arrivals = mutableListOf<UpcomingArrival>()
    for (entry in entries) {
        val trip = gtfsData.trips[entry.tripId] ?: continue
        val serviceDate = resolveServiceDate(
            trip = trip,
            arrivalSecs = entry.arrivalSecs,
            activeServiceIds = activeIds,
            yesterdayActiveIds = yesterdayIds,
            today = today,
            yesterday = yesterday,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs
        ) ?: continue

        val schEpochMs = scheduledDateEpoch(serviceDate, entry.arrivalSecs) ?: continue

        val delayInfo = delays[entry.tripId]
        val delay: Int
        val isRT: Boolean
        when {
            delayInfo?.stopDelays?.containsKey(stopId) == true -> {
                delay = delayInfo.stopDelays[stopId]!!; isRT = true
            }
            delayInfo != null -> {
                delay = delayInfo.generalDelay; isRT = true
            }
            else -> {
                delay = 0; isRT = false
            }
        }

        val predEpochMs = schEpochMs + delay * 1000L
        if (predEpochMs < windowStartMs || predEpochMs > windowEndMs) continue

        val route = gtfsData.routes[trip.routeId]
        val routeDisplayName = (route?.shortName ?: trip.routeId) +
                (variantSuffix(trip.routeId, trip.headsign) ?: "")
        val hasAlert = alerts.routeIds.contains(trip.routeId) || alerts.stopIds.contains(stopId)
        arrivals.add(
            UpcomingArrival(
                stopId = stopId,
                stopName = stop.name,
                distanceMeters = distance,
                routeShortName = routeDisplayName,
                routeLongName = route?.longName ?: "",
                routeColor = route?.color ?: "",
                headsign = trip.headsign,
                scheduledTimeEpoch = schEpochMs,
                predictedTimeEpoch = predEpochMs,
                delaySecs = delay,
                vehicleLabel = delayInfo?.vehicleLabel ?: "",
                isRealTime = isRT,
                hasAlert = hasAlert
            )
        )
    }
    arrivals.sortBy { it.predictedTimeEpoch }
    return arrivals
}

/// Devuelve el primer servicio futuro por línea en los próximos daysAhead días,
/// útil para mostrar cuándo volverá a haber servicio cuando la ventana de 60 min está vacía.
fun computeNextArrivals(
    stopId: String,
    distance: Double,
    gtfsData: GtfsData,
    delays: Map<String, TripDelayInfo>,
    alerts: ServiceAlerts = ServiceAlerts(),
    daysAhead: Int = 7
): List<UpcomingArrival> {
    val stop = gtfsData.stops[stopId] ?: return emptyList()
    val entries = gtfsData.stopArrivals[stopId] ?: return emptyList()

    val nowMs = System.currentTimeMillis()
    val tz = TimeZone.getTimeZone("Europe/Madrid")

    // Construye la lista de días a buscar: ayer (viajes cross-midnight) + hoy + próximos días
    data class DayInfo(val dateStr: String, val midnightMs: Long, val svcIds: Set<String>)
    val days = mutableListOf<DayInfo>()
    for (offset in -1 until daysAhead) {
        val dayMs = nowMs + offset * 86_400_000L
        val dateStr = dateString(Date(dayMs))
        val cal = Calendar.getInstance(tz)
        cal.time = Date(dayMs)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val midnightMs = cal.timeInMillis
        val svcIds: Set<String> = gtfsData.activeDates[dateStr] ?: emptySet()
        days.add(DayInfo(dateStr, midnightMs, svcIds))
    }

    val bestEpochByRoute = mutableMapOf<String, Long>()
    val bestArrivalByRoute = mutableMapOf<String, UpcomingArrival>()

    for (entry in entries) {
        val trip = gtfsData.trips[entry.tripId] ?: continue
        val svc = trip.serviceId
        val route = gtfsData.routes[trip.routeId]
        val routeShortName = (route?.shortName ?: trip.routeId) +
                (variantSuffix(trip.routeId, trip.headsign) ?: "")

        for (day in days) {
            val active = day.svcIds.contains(svc) || svc == "UNDEFINED"
            if (!active) continue

            val epochMs = day.midnightMs + entry.arrivalSecs * 1000L
            if (epochMs <= nowMs) continue

            // Si ya tenemos una mejor, descartamos
            val best = bestEpochByRoute[routeShortName]
            if (best != null && best <= epochMs) continue

            val delayInfo = delays[entry.tripId]
            val delay: Int
            val isRT: Boolean
            when {
                delayInfo?.stopDelays?.containsKey(stopId) == true -> {
                    delay = delayInfo.stopDelays[stopId]!!; isRT = true
                }
                delayInfo != null -> {
                    delay = delayInfo.generalDelay; isRT = true
                }
                else -> {
                    delay = 0; isRT = false
                }
            }
            val predEpochMs = epochMs + delay * 1000L

            bestEpochByRoute[routeShortName] = epochMs
            val hasAlert = alerts.routeIds.contains(trip.routeId) || alerts.stopIds.contains(stopId)
            bestArrivalByRoute[routeShortName] = UpcomingArrival(
                stopId = stopId,
                stopName = stop.name,
                distanceMeters = distance,
                routeShortName = routeShortName,
                routeLongName = route?.longName ?: "",
                routeColor = route?.color ?: "",
                headsign = trip.headsign,
                scheduledTimeEpoch = epochMs,
                predictedTimeEpoch = predEpochMs,
                delaySecs = delay,
                vehicleLabel = delayInfo?.vehicleLabel ?: "",
                isRealTime = isRT,
                hasAlert = hasAlert
            )
            break  // este día ya da el mínimo para esta entrada; ir al siguiente entry
        }
    }

    return bestArrivalByRoute.values.sortedBy { it.scheduledTimeEpoch }
}

// MARK: - Variantes de línea (porta RouteVariantConfig.swift)

data class RouteVariantRule(
    val routeId: String,
    val suffix: String,
    val headsignContains: String
)

/// Devuelve el sufijo de variante (p.ej. "A") para un viaje dado, o null si no aplica ninguna regla.
fun variantSuffix(routeId: String, headsign: String): String? {
    val rules = listOf(
        // Línea 5: 5A = Astegieta, 5B = Jundiz/Ariñez, 5C = ITV Ariñez
        // "ARIÑEZ ITV" debe ir antes de "ARIÑEZ" para no quedar enmascarado.
        RouteVariantRule("5", "A", "ASTEGIETA"),
        RouteVariantRule("5", "C", "ARIÑEZ ITV"),
        RouteVariantRule("5", "B", "ARIÑEZ"),
        RouteVariantRule("5", "B", "JUNDIZ")
    )
    val upper = headsign.uppercase()
    for (rule in rules) {
        if (rule.routeId == routeId && upper.contains(rule.headsignContains)) {
            return rule.suffix
        }
    }
    return null
}

// MARK: - Utilidad de lectura CSV

private fun readCsvFile(file: File, block: (headers: List<String>, row: List<String>) -> Unit) {
    if (!file.exists()) return
    var headers: List<String>? = null
    file.bufferedReader(Charsets.UTF_8).useLines { lines ->
        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r', '\n')
            if (line.isBlank()) continue
            if (headers == null) {
                headers = splitCsv(line)
            } else {
                block(headers!!, splitCsv(line))
            }
        }
    }
}
