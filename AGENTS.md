# AGENTS.md — BusGasteiz Android

Aplicación para Android que visualiza la información en tiempo real de los autobuses urbanos y el tranvía de Vitoria-Gasteiz. Es el equivalente funcional de la app iOS; debe comportarse de forma idéntica en cuanto a datos, lógica y funcionalidad, pero usar tecnologías nativas de Android, iconos y convenciones de **Material Design 3**.

---

## Estructura del proyecto

```
android/BusGasteiz/
├── build.gradle.kts                        # Configuración de proyecto (raíz)
├── app/
│   ├── build.gradle.kts                    # Dependencias y configuración del módulo app
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/jaureguialzo/busgasteiz/
│           ├── MainActivity.kt             # Entry point; aplica BusGasteizTheme y lanza BusGasteizApp
│           ├── BusGasteizApp.kt            # NavigationSuiteScaffold raíz (3 destinos)
│           ├── AppDestinations.kt          # Enum de destinos de navegación principal (Stops/Map/Favorites)
│           ├── NavDestination.kt           # Sealed class para navegación push tipada entre pantallas
│           ├── data/
│           │   ├── Models.kt               # Data classes (StopInfo, TripInfo, RouteInfo, …)
│           │   ├── DataRepository.kt       # Singleton ViewModel; descarga, caché y refresco de datos
│           │   ├── GtfsParser.kt           # Parsers GTFS/GTFS-RT y motor de consulta de llegadas
│           │   ├── ProtoReader.kt          # Decodificador protobuf de bajo nivel (sin dependencias)
│           │   ├── ZipExtractor.kt         # Descompresión de ZIPs (ZipInputStream)
│           │   ├── LocationRepository.kt   # Wrapper de FusedLocationProviderClient
│           │   └── FavoritesRepository.kt  # Favoritos persistidos con DataStore<Preferences>
│           ├── settings/
│           │   └── AppSettings.kt          # DataStore<Preferences>; radio de búsqueda y otros ajustes
│           └── ui/
│               ├── NearbyStopsScreen.kt    # Pestaña "Stops" — lista de paradas cercanas
│               ├── MapScreen.kt            # Pestaña "Map" — mapa con marcadores de paradas
│               ├── FavoritesScreen.kt      # Pestaña "Favorites" — paradas y líneas guardadas
│               ├── StopDetailSheet.kt      # Bottom sheet de detalle de parada; llegadas en tiempo real
│               ├── AboutScreen.kt          # Pantalla "About": licencias, fuentes de datos, privacidad
│               ├── components/
│               │   ├── StopIcon.kt         # Icono circular de parada (bus/tranvía, con/sin llegadas)
│               │   ├── RouteBadge.kt       # Badge cuadrado con color y nombre corto de línea
│               │   └── AlertRow.kt         # Fila de alerta de servicio
│               └── theme/
│                   ├── Color.kt            # Colores del tema (generados con Material Theme Builder)
│                   ├── Theme.kt            # BusGasteizTheme (claro/oscuro, dynamic color)
│                   └── Type.kt             # Tipografía
```

---

## Tecnologías y requisitos

| Elemento                | Valor                                                     |
|-------------------------|-----------------------------------------------------------|
| Lenguaje                | Kotlin                                                    |
| Framework UI            | Jetpack Compose + Material 3                              |
| Observación de estado   | `ViewModel` + `StateFlow` (androidx.lifecycle)            |
| Android mínimo          | API 23 (Android 6.0 Marshmallow)                         |
| Android objetivo        | API 36 (Android 16)                                       |
| Mapas                   | Google Maps SDK + Maps Compose (`com.google.maps.android:maps-compose`) |
| Localización GPS        | `FusedLocationProviderClient` (Google Play Services)      |
| Localización de strings | `strings.xml` + carpetas `values-es/`, `values-eu/`       |
| Persistencia            | `DataStore<Preferences>` (Jetpack DataStore)              |
| Red                     | `OkHttp` o `HttpURLConnection` (stdlib)                   |
| Navegación              | Compose Navigation (`NavHost`)                            |
| Barra de pestañas       | `NavigationSuiteScaffold` (adapta a bottom bar / rail / drawer) |

---

## Equivalencias iOS → Android

| Concepto                         | iOS (Swift)                              | Android (Kotlin)                                              |
|----------------------------------|------------------------------------------|---------------------------------------------------------------|
| Lenguaje                         | Swift                                    | Kotlin                                                        |
| Framework UI                     | SwiftUI                                  | Jetpack Compose                                               |
| Observación de estado            | `@Observable` (Observation framework)   | `ViewModel` + `StateFlow` / `MutableStateFlow`                |
| Propagación de estado            | `@Environment` + `.environment()`       | `CompositionLocalProvider` o parámetros explícitos al ViewModel |
| Hilo principal                   | `@MainActor`                             | `Dispatchers.Main` / `viewModelScope.launch`                  |
| Hilo de fondo                    | `Task.detached`                          | `withContext(Dispatchers.IO)` / `Dispatchers.IO`              |
| HTTP                             | `URLSession.shared`                      | `OkHttp` / `HttpURLConnection`                                |
| Descompresión ZIP                | `ZIPExtractor` (custom)                  | `ZipInputStream` (Java stdlib)                                |
| Protobuf sin dependencias        | `ProtoReader` (custom)                   | Puerto Kotlin de `ProtoReader` (mismo wire format binario)    |
| Localización GPS                 | `CLLocationManager`                      | `FusedLocationProviderClient` (Play Services)                 |
| Mapas                            | MapKit (`Map`, `Annotation`)             | Google Maps SDK + `GoogleMap` Composable                      |
| Barra de pestañas                | `TabView`                                | `NavigationSuiteScaffold` / `NavigationBar`                   |
| Navegación push                  | `NavigationStack` + `NavigationPath`     | `NavHost` + `NavController` (Compose Navigation)             |
| Ajustes persistidos              | `UserDefaults`                           | `DataStore<Preferences>`                                      |
| Favoritos                        | `UserDefaults` (local)                   | `DataStore<Preferences>` (local)                              |
| Sincronización en la nube        | —  (solo local en este proyecto)         | — (solo local; ver nota abajo)                                |
| Cadenas localizadas              | `Localizable.xcstrings`                  | `strings.xml` + `values-es/`, `values-eu/`                   |
| Vista "sin contenido"            | `ContentUnavailableView`                 | Componente Compose personalizado                              |
| Botón de cierre de sheet         | `SheetCloseButton` (adaptativo iOS 26+)  | `IconButton { Icon(Icons.Default.Close) }` (uniforme)         |
| Caché de ficheros                | `Documents/GTFSCache/`                   | `context.filesDir` + subcarpeta `GTFSCache/`                  |
| Sistema de iconos                | SF Symbols                               | Material Icons (`androidx.compose.material:material-icons-extended`) |
| Convenciones de diseño           | Human Interface Guidelines (Apple)       | Material Design 3 (Google)                                    |
| Color dinámico del tema          | Acento del sistema (`Color.accentColor`) | Dynamic Color de Material You (`dynamicLightColorScheme`)     |

### Nota sobre sincronización de favoritos

En iOS los favoritos se guardan en `UserDefaults` sin sincronización iCloud activa. En Android se
usa igualmente almacenamiento **solo local** con `DataStore<Preferences>`. Si en el futuro se
quisiera sincronización entre dispositivos, las opciones recomendadas son:

1. **Google Drive Backup** — copia de seguridad automática de `DataStore` (configurada en
   `backup_rules.xml`; ya habilitado en el `AndroidManifest.xml` de la plantilla).
2. **Firebase Firestore** — sincronización en tiempo real, requiere cuenta Google y dependencias
   adicionales (`com.google.firebase:firebase-firestore-ktx`).
3. Mantener solo local (paridad con iOS actual).

---

## Arquitectura

### Flujo de datos

```
DataRepository (singleton ViewModel, viewModelScope + Dispatchers.IO)
    ├── Descarga GTFS ZIP (Tuvisa)          → gtfsDir (filesDir/GTFSCache/GTFS_Data/)
    ├── Descarga GTFS ZIP (Euskotren)       → euskotrenGtfsDir (filesDir/GTFSCache/Euskotren_Data/)
    ├── Descarga RT .pb (Tuvisa)            → pbFile
    ├── Descarga RT .pb (Euskotren)         → euskotrenPbFile
    ├── Descarga alertas .pb (Tuvisa)       → tuvisaAlertsPbFile  (no crítico: errores ignorados)
    ├── Descarga alertas .pb (Euskotren)    → euskotrenAlertsPbFile (no crítico: errores ignorados)
    ├── parseInBackground() [Dispatchers.IO]
    │       loadGtfs()           → GtfsData (Tuvisa)
    │       loadEuskoTranGtfs()  → GtfsData (tranvía VGZ)
    │       merge tram → main GtfsData
    │       loadTripDelays() ×2  → Map<String, TripDelayInfo>
    │       loadAlerts() ×2      → ServiceAlerts (fusionados)
    ├── gtfsData: StateFlow<GtfsData?>
    ├── tripDelays: StateFlow<Map<String, TripDelayInfo>>
    ├── serviceAlerts: StateFlow<ServiceAlerts>
    ├── activeStopIds: StateFlow<Set<String>>   ← precomputado tras cada carga
    ├── loadState: StateFlow<LoadState>
    ├── version: StateFlow<Int>                 ← se incrementa con cada recarga
    └── isRefreshing: StateFlow<Boolean>
```

Los datos estáticos (GTFS ZIP) se refrescan cada **10 minutos**. El feed RT y las alertas se
descargan en cada refresco. `forceRefresh()` fuerza una recarga inmediata.

### Fusión de datos de tranvía

Igual que en iOS: `loadEuskoTranGtfs()` filtra el GTFS de Euskotren al operador `EUS_TrGa`
(líneas TG1, TG2 y 41). Los andenes se agrupan por su `StopPlace` padre para evitar duplicados.

---

## Fuentes de datos

Idénticas a la app iOS:

| Recurso                          | URL                                                                                              |
|----------------------------------|--------------------------------------------------------------------------------------------------|
| GTFS estático Tuvisa             | `https://www.vitoria-gasteiz.org/we001/http/vgTransit/google_transit.zip`                        |
| RT trip updates Tuvisa           | `https://www.vitoria-gasteiz.org/we001/http/vgTransit/realTime/tripUpdates.pb`                   |
| RT alertas Tuvisa                | `https://opendata.euskadi.eus/transport/moveuskadi/tuvisa/gtfsrt_tuvisa_alerts.pb`               |
| GTFS estático Euskotren          | `https://opendata.euskadi.eus/transport/moveuskadi/euskotren/gtfs_euskotren.zip`                  |
| RT trip updates Euskotren (tram) | `https://opendata.euskadi.eus/transport/moveuskadi/euskotren/gtfsrt_euskotren_trip_updates.pb`    |
| RT alertas Euskotren (tram)      | `https://opendata.euskadi.eus/transport/moveuskadi/euskotren/gtfsrt_euskotren_alerts.pb`          |

---

## Modelos de datos principales (`Models.kt`)

| Data class        | Propósito                                                           |
|-------------------|---------------------------------------------------------------------|
| `StopInfo`        | Parada (id, name, nameEu, nameEs, lat, lon, isTram)                 |
| `TripInfo`        | Viaje (id, routeId, headsign, serviceId)                            |
| `RouteInfo`       | Línea (id, shortName, longName, color hex)                          |
| `StopTimeEntry`   | Horario individual (tripId, stopSequence, arrivalSecs)              |
| `GtfsData`        | Contenedor: stops, trips, routes, stopArrivals, activeDates         |
| `TripDelayInfo`   | Retraso RT: generalDelay, stopDelays[stopId], vehicleLabel          |
| `ServiceAlert`    | Alerta de servicio: headerText, descriptionText                     |
| `ServiceAlerts`   | Contenedor: stopAlerts[stopId], routeAlerts[routeId]                |
| `UpcomingArrival` | Resultado de consulta: horario programado + predicho + retraso + hasAlert |
| `RouteTag`        | Línea resumida para listas: shortName, color, hasAlert              |
| `NearbyStop`      | Parada + distancia + hasArrivals + routes (List<RouteTag>) + hasAlert |

`StopInfo.localizedName` devuelve `nameEu`/`nameEs` según el idioma del sistema
(`Locale.getDefault().language`), con fallback a `name`.

---

## Motor de consulta (`GtfsParser.kt`)

| Función                            | Descripción                                                                            |
|------------------------------------|----------------------------------------------------------------------------------------|
| `loadGtfs(folder)`                 | Carga GTFS estático Tuvisa (routes, trips, stops, translations, calendar_dates, stop_times) |
| `loadEuskoTranGtfs(folder)`        | Carga y filtra GTFS Euskotren al tranvía de Vitoria-Gasteiz                            |
| `loadActiveDatesFromCalendar()`    | Expande `calendar.txt` a fechas individuales + aplica excepciones                      |
| `loadTripDelays(data)`             | Decodifica feed GTFS-RT protobuf → `Map<String, TripDelayInfo>`                        |
| `loadAlerts(data)`                 | Decodifica feed GTFS-RT Alerts → `ServiceAlerts`                                       |
| `routesForStop(stopId, gtfsData, alerts)` | Calcula `List<RouteTag>` para una parada, marcando líneas con alerta           |
| `computeStopsWithUpcomingArrivals` | Calcula `Set<String>` de stop_id con llegadas en los próximos 60 min                   |
| `computeNearbyStops`               | Paradas en radio Haversine, ordenadas por distancia                                    |
| `computeStopsInBounds`             | Paradas en bounding box del mapa visible                                               |
| `computeArrivals`                  | Llegadas de una parada en ventana de 60 min con retrasos RT aplicados                  |
| `computeNextArrivals`              | Primer servicio futuro por línea (hasta 7 días)                                        |
| `resolveServiceDate`               | Determina si un horario cae hoy o ayer (servicios nocturnos que cruzan medianoche)      |
| `variantSuffix(routeId, headsign)` | Devuelve sufijo de variante (A/B/C) para líneas con ramales (p.ej. línea 5)            |

**Optimización de rendimiento**: `computeStopsWithUpcomingArrivals` usa aritmética de epoch pura.
Los formateadores de fecha son singletons (`companion object`) para evitar su recreación en bucles.

---

## Pantallas principales

### `BusGasteizApp` — NavigationSuiteScaffold raíz

Tres destinos con `NavHost` independiente por pestaña:
1. **Stops** (`NearbyStopsScreen`) — lista de paradas cercanas ordenadas por distancia.
2. **Map** (`MapScreen`) — mapa con marcadores de paradas.
3. **Favorites** (`FavoritesScreen`) — paradas y líneas guardadas.

Usa `NavigationSuiteScaffold` para adaptarse automáticamente a bottom navigation bar (teléfono),
navigation rail (tablet en vertical) y navigation drawer (tablet en horizontal).

Pulsar un destino ya seleccionado resetea su pila de navegación al nivel raíz (misma lógica que iOS).

### `NearbyStopsScreen`

- Muestra el estado de carga de `DataRepository` (`Idle`, `Loading`, `Failed`, `Ready`).
- Lee `AppSettings.searchRadius` para filtrar paradas por distancia.
- Cada fila: `StopIcon` + nombre de parada + distancia + `RouteBadge`s de línea.
- Icono en gris si la parada no tiene llegadas en los próximos 60 min.
- Indicador de alerta si `NearbyStop.hasAlert`.
- Navega a `StopDetailSheet` al pulsar una parada.
- Botón "About" en la `TopAppBar` que abre `AboutScreen`.

### `MapScreen`

- Mapa `GoogleMap` con marcadores de paradas mediante `MarkerComposable`.
- Se actualiza al mover el mapa (idle callback de `CameraPositionState`).
- Menú de radio de búsqueda (100–2000 m); botón de recentrado en la posición del usuario.
- Al pulsar un marcador abre `StopDetailSheet` como `ModalBottomSheet`.

### `StopDetailSheet`

- `ModalBottomSheet` con llegadas en los próximos 60 min.
- Muestra alertas de servicio al principio de la lista si las hay.
- Si no hay llegadas, muestra estado vacío + sección **"Next scheduled services"**.
- Etiqueta de tiempo: `Xm` si ≤ 60 min, `Xh Ym` si > 60 min.
- Botón de estrella (favorito); botón de cierre (`Icons.Default.Close`).
- Cada línea muestra un `RouteBadge` con su color.
- Desde "Next scheduled services" navega a una pantalla de llegadas por línea.

### `FavoritesScreen`

- Muestra paradas favoritas y líneas favoritas por parada.
- Estado vacío con instrucciones si no hay favoritos.
- Botón de recarga manual en la `TopAppBar` (`Icons.Default.Refresh`).

### `AboutScreen`

- `Scaffold` con `TopAppBar` y botón de cierre o navegación atrás.
- Secciones: copyright, términos de uso, política de privacidad, licencia Apache 2.0,
  fuentes de datos (con licencia CC BY), enlace a GitHub.
- En builds `DEBUG`: toggle para simular alertas de servicio.

---

## Componentes visuales

### `StopIcon`

Equivalente de `StopIconView` de iOS:
- Círculo relleno con color primario (`MaterialTheme.colorScheme.primary`) o gris si sin llegadas.
- Icono `Icons.Default.DirectionsBus` / icono de tranvía en blanco.
- Borde blanco y sombra sutil.
- Parámetros: `size`, `isTram`, `hasArrivals`.

### `RouteBadge`

Equivalente de `RouteBadgeView` de iOS:
- `RoundedCornerShape` relleno con el color de la línea.
- Color de texto determinado por luminancia (umbral 140): negro para fondos claros, blanco para oscuros.
- Escala el texto con `adjustsFontSizeToFit` equivalente (`AutoSizeText` o `adjustedFontSize`).

---

## Gestión de favoritos (`FavoritesRepository.kt`)

- `favoriteStopIds: Flow<Set<String>>` — paradas completas guardadas.
- `favoriteRouteKeys: Flow<Set<String>>` — claves `"stopId::routeShortName"` para líneas concretas.
- Persistencia con `DataStore<Preferences>` (equivalente Android de `UserDefaults`).
- Expuesto como `StateFlow` al `ViewModel` para observación en Compose.

---

## Ajustes compartidos (`AppSettings.kt`)

`DataStore<Preferences>` centraliza los ajustes de usuario:

| Clave                    | Tipo     | Por defecto | Descripción                              |
|--------------------------|----------|-------------|------------------------------------------|
| `search_radius`          | `Float`  | `200f`      | Radio de búsqueda en metros              |

Acceder desde Compose a través de `AppSettings.searchRadiusFlow.collectAsState()`.

---

## Navegación tipada (`NavDestination.kt`)

`sealed class NavDestination` centraliza los destinos de navegación push:

| Subclase                                                            | Destino                                          |
|---------------------------------------------------------------------|--------------------------------------------------|
| `StopDetail(stop, distance, starLeading)`                           | `StopDetailSheet` — detalle de llegadas          |
| `RouteArrivals(stop, distance, routeShortName, routeColor)`         | Pantalla de llegadas filtradas por línea          |

---

## Localización

- Idiomas: **inglés** (por defecto), **castellano** (`values-es/`), **euskera** (`values-eu/`).
- Si el idioma del dispositivo no coincide con ninguno, Android usa el por defecto (inglés).
- Todos los strings visibles al usuario deben estar en `strings.xml`.
- Los nombres de paradas tienen versión en euskera (`nameEu`) y castellano (`nameEs`) según
  `translations.txt` de Tuvisa. `StopInfo.localizedName` los selecciona automáticamente.

---

## Convenciones de código

- Un commit por cada funcionalidad nueva o corrección relevante.
- Usar `StateFlow` (inmutable hacia la UI) y `MutableStateFlow` (interno al ViewModel/Repository).
- Las funciones de parsing GTFS son `suspend fun` ejecutadas con `withContext(Dispatchers.IO)`.
- No crear `SimpleDateFormat` dentro de bucles; usar singletons en `companion object`.
- Las conversiones de bytes a enteros con signo usan `ByteBuffer` o extensiones explícitas.
- Seguir las recomendaciones de **Material Design 3** y las guías de calidad de apps de Google Play.

---

## Diseño de UI

- Iconos de parada: circulares, equivalentes a 48 dp, color primario del tema o gris.
- Badges de línea: esquinas redondeadas, color de la línea, texto blanco o negro según luminancia.
- Mapa en estilo estándar de Google Maps (igual que el estilo `.standard` de MapKit).
- Marcadores seleccionados se amplían visualmente para indicar selección.
- Soportar modo claro y oscuro mediante `BusGasteizTheme` con `dynamicColor = true` donde sea posible.
- Edge-to-edge habilitado (`enableEdgeToEdge()`) con `WindowInsets` gestionados por Scaffold.

---

## Configuración de Google Maps

La clave de API de Google Maps se configura en `local.properties` (no versionar):

```
MAPS_API_KEY=AIza...
```

Y se referencia en `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

Añadir `local.properties` al `.gitignore`. En el README indicar cómo obtener y configurar la clave.
