# AGENTS.md — BusGasteiz Android

Aplicación para Android que visualiza la información en tiempo real de los autobuses urbanos y el tranvía de Vitoria-Gasteiz. Es el equivalente funcional de la app iOS; debe comportarse de forma idéntica en cuanto a datos, lógica y funcionalidad, pero usar tecnologías nativas de Android, iconos y convenciones de **Material Design 3**.

---

## Estructura del proyecto

```
android/BusGasteiz/
├── build.gradle.kts                        # Configuración de proyecto (raíz)
├── app/
│   ├── google-services.json                # Configuración de Firebase
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
│           │   └── FavoritesRepository.kt  # Favoritos sincronizados con Firestore (auth anónima)
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
| Persistencia local      | `DataStore<Preferences>` (Jetpack DataStore)              |
| Favoritos               | Firebase Firestore (`firebase-firestore-ktx`)             |
| Autenticación           | Firebase Auth anónima (`firebase-auth-ktx`)               |
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
| Favoritos                        | `UserDefaults` (local)                   | Firebase Firestore (sincronizado entre dispositivos)          |
| Sincronización en la nube        | — (solo local en este proyecto)          | Firebase Firestore con autenticación anónima                  |
| Cadenas localizadas              | `Localizable.xcstrings`                  | `strings.xml` + `values-es/`, `values-eu/`                   |
| Vista "sin contenido"            | `ContentUnavailableView`                 | Componente Compose personalizado                              |
| Botón de cierre de sheet         | `SheetCloseButton` (adaptativo iOS 26+)  | `IconButton { Icon(Icons.Default.Close) }` (uniforme)         |
| Caché de ficheros                | `Documents/GTFSCache/`                   | `context.filesDir` + subcarpeta `GTFSCache/`                  |
| Sistema de iconos                | SF Symbols                               | Material Icons (`androidx.compose.material:material-icons-extended`) |
| Convenciones de diseño           | Human Interface Guidelines (Apple)       | Material Design 3 (Google)                                    |
| Color dinámico del tema          | Acento del sistema (`Color.accentColor`) | Dynamic Color de Material You (`dynamicLightColorScheme`)     |

### Sincronización de favoritos con Firestore

Los favoritos se sincronizan en Firestore mediante **autenticación anónima**. Al arrancar la app,
`FavoritesRepository` inicia sesión anónima con Firebase Auth si no existe ya una sesión. Esto
proporciona un `uid` estable por dispositivo sin requerir que el usuario cree una cuenta.

Estructura de datos en Firestore:

```
users/{uid}/
    favorites/
        stops     → array de stop_id strings
        routes    → array de strings "stopId::routeShortName"
```

La sesión anónima persiste entre reinicios de la app. Si el usuario desinstala y reinstala,
se genera un nuevo `uid` y los favoritos previos se pierden (comportamiento equivalente al de iOS).

> **Nota de privacidad**: la autenticación anónima no recopila datos personales del usuario;
> el `uid` es un identificador opaco generado por Firebase en el dispositivo.

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

- Mapa `GoogleMap` con marcadores de paradas mediante **`MarkerComposable`** (maps-compose).
  - Los marcadores renderizan el composable `StopIcon` (mismo aspecto que la lista de paradas).
  - Tamaño de marcador: `size=27.dp`, `iconSize=14.dp` (25 % más pequeño que en lista).
  - `anchor = Offset(0.5f, 0.5f)` para que el icono circular quede centrado sobre la coordenada.
- Se actualiza al mover el mapa (idle callback de `CameraPositionState`).
- **Radio y zoom sincronizados**: `radiusToZoom(radius, lat)` convierte el radio en metros a nivel
  de zoom de Google Maps (`log2(156543·cos(lat)·360 / (radius·1.5))`). Un `LaunchedEffect(searchRadius)`
  anima la cámara al zoom correspondiente cuando cambia el radio en el selector.
- **Zoom inicial**: se lee el radio almacenado con `runBlocking { appSettings.searchRadiusFlow.first() }`
  dentro de `rememberCameraPositionState` para iniciar el mapa con el zoom correcto. Valor por defecto: 200 m (~zoom 17).
- Toolbar con **icono `NearMe`** para relocalizar y botón de refresco con animación de spinner
  (mínimo 1 segundo). Idéntica a la toolbar de `NearbyStopsScreen`.
- Menú de radio de búsqueda (100–2000 m) integrado en la toolbar del mapa.
- Al pulsar un marcador abre `StopDetailSheet` como `ModalBottomSheet`.

### `StopDetailSheet`

- `ModalBottomSheet` con llegadas en los próximos 60 min.
- Aplica `WindowInsets.statusBars` como padding superior para no solaparse con la barra de estado
  ni con la cámara perforada del dispositivo.
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
- **`isPullRefreshing` pattern**: la animación pull-to-refresh se controla con un estado local
  `isPullRefreshing` que solo se activa al arrastrar manualmente. Se limpia cuando
  `dataRepository.isRefreshing` vuelve a `false`. Evita que el spinner aparezca duplicado
  cuando se refresca desde otro origen (ej. refresco automático de 10 min).

### `AboutScreen`

- `Scaffold` con `TopAppBar` y botón de cierre o navegación atrás.
- Secciones: copyright, términos de uso, política de privacidad, licencia Apache 2.0,
  fuentes de datos (con licencia CC BY), enlace a GitHub, paletas de color.
- **Iconos en todas las secciones**, equivalentes a los de iOS:
  - App License: `Icons.Default.Description`
  - TUVISA bus lines: `Icons.Default.DirectionsBus`
  - TUVISA real-time data: `Icons.Default.Sensors`
  - Open Data Euskadi (tranvía): `Icons.Default.Tram`
  - Color Palettes: `Icons.Default.Palette`
  - GitHub: `Icons.Default.Code`
- En builds `DEBUG`: toggle para simular alertas, con icono `Icons.Default.Warning` en rojo.

---

## Componentes visuales

### `StopIcon`

Equivalente de `StopIconView` de iOS:
- Círculo relleno con color primario (`MaterialTheme.colorScheme.primary`) o gris si sin llegadas.
- Icono `Icons.Default.DirectionsBus` / `Icons.Default.Tram` en blanco.
- Borde blanco y sombra sutil.
- Parámetros: `size` (por defecto 36 dp), `iconSize` (por defecto `size * 0.52f`; en la lista de
  paradas queda un 30 % más grande que la versión original), `isTram`, `hasArrivals`.
- Los marcadores del mapa usan `size=27.dp, iconSize=14.dp` explícitos (marcador 25 % más pequeño
  que en la lista; el icono interior mantiene su tamaño relativo original).

### `RouteBadge`

Equivalente de `RouteBadgeView` de iOS:
- `RoundedCornerShape` relleno con el color de la línea.
- Color de texto determinado por luminancia (umbral 140): negro para fondos claros, blanco para oscuros.
- Escala el texto con `adjustsFontSizeToFit` equivalente (`AutoSizeText` o `adjustedFontSize`).

---

## Gestión de favoritos (`FavoritesRepository.kt`)

- `favoriteStopIds: Flow<Set<String>>` — paradas completas guardadas.
- `favoriteRouteKeys: Flow<Set<String>>` — claves `"stopId::routeShortName"` para líneas concretas.
- Persistencia y sincronización con **Firebase Firestore** bajo `users/{uid}/favorites`.
- Al inicializar, llama a `Firebase.auth.currentUser ?: Firebase.auth.signInAnonymously()` para
  obtener un `uid` estable sin intervención del usuario.
- Los cambios locales se escriben en Firestore con `set(..., SetOptions.merge())` y se escuchan
  en tiempo real con `addSnapshotListener` para reflejar cambios desde otros dispositivos.
- Expuesto como `StateFlow` al `ViewModel` para observación en Compose.

### Formato de clave de línea favorita y advertencia con IDs de Euskotren

Las claves de línea-en-parada usan el separador `"::"`: `"<stopId>::<routeShortName>"`.

> ⚠️ **Los IDs de parada de Euskotren terminan en dos puntos** (p.ej. `ES:Euskotren:StopPlace:1559:`).
> Esto produce triples `":::"` en la clave compuesta. Al parsear, **siempre dividir por la última
> ocurrencia de `"::"` ** (no la primera), ya que los nombres de línea nunca contienen `":"`.
>
> En Kotlin: `key.lastIndexOf("::")` + `key.substring(0, idx)` / `key.substring(idx + 2)` (ya implementado en `parsedRouteKeys`).
>
> No usar `key.split("::")`: encontraría el primer `"::"` y dejaría el trailing `":"`
> del stopId fuera, rompiendo la búsqueda en `gtfs.stops`.

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
- Los marcadores del mapa son composables `StopIcon` renderizados con `MarkerComposable`
  (mismo aspecto visual que en la lista de paradas).
- Marcadores seleccionados se amplían visualmente para indicar selección.
- Soportar modo claro y oscuro mediante `BusGasteizTheme` con `dynamicColor = true` donde sea posible.
- Edge-to-edge habilitado (`enableEdgeToEdge()`) con `WindowInsets` gestionados por Scaffold.
- **Color de acento**: verde `#60A589` (equivalente al `AccentColor` de iOS). Es el color primario
  del tema cuando el dispositivo no soporta Dynamic Color o lo tiene desactivado.

---

## Iconos de la aplicación

El icono se genera con el script `utils/gen_icon_android.py`:

- **Fondo adaptativo** (`drawable/ic_launcher_background.xml`): vector sólido `#60A589`.
- **Foreground adaptativo** (`drawable-v24/ic_launcher_foreground.xml`): vector del icono de
  autobús (`directions_bus`) centrado en la zona segura de 72 dp.
- **PNGs de densidad** (`mipmap-mdpi` → `mipmap-xxxhdpi`): generados con `rsvg-convert` a
  48, 72, 96, 144 y 192 px. Hay versión cuadrada (`ic_launcher.png`) y circular
  (`ic_launcher_round.png`; recortada con `clipPath` en el SVG, no en el `<g transform>`
  para que `librsvg` evalúe correctamente las coordenadas del clip).
- El script también genera el icono de **512×512 px para Google Play Store** en
  `busgasteiz/temp/ic_play_store_512.png` (autobús 25 % más grande que en los iconos de app).

---

## Configuración de Firebase

El fichero `google-services.json` está en `app/`, que es la ubicación correcta para el plugin
de Gradle de Google Services.

El plugin y las dependencias de Firebase deben añadirse a los ficheros de build:

**`BusGasteiz/build.gradle.kts`** (raíz):
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

**`app/build.gradle.kts`**:
```kotlin
plugins {
    id("com.google.gms.google-services")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.x.x"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
}
```

Usar la versión más reciente del BOM de Firebase disponible en el momento de implementar.

### Servicios habilitados en la consola de Firebase

| Servicio                  | Estado    | Uso                                              |
|---------------------------|-----------|--------------------------------------------------|
| Authentication — Anónima  | Habilitada| Sesión de usuario sin registro; genera `uid`     |
| Firestore Database        | Habilitada| Almacenamiento y sincronización de favoritos     |

### Reglas de seguridad de Firestore recomendadas

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

Cada usuario anónimo solo puede leer y escribir su propio subárbol `users/{uid}/`.

---



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
