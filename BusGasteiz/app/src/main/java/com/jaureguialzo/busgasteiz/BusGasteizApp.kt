package com.jaureguialzo.busgasteiz

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.LocationRepository
import com.jaureguialzo.busgasteiz.settings.AppSettings
import com.jaureguialzo.busgasteiz.ui.AboutScreen
import com.jaureguialzo.busgasteiz.ui.FavoritesScreen
import com.jaureguialzo.busgasteiz.ui.MapScreen
import com.jaureguialzo.busgasteiz.ui.NearbyStopsScreen
import com.jaureguialzo.busgasteiz.ui.RouteArrivalsScreen
import com.jaureguialzo.busgasteiz.ui.StopDetailScreen

// MARK: - Raíz de la aplicación con navegación por pestañas

@Composable
fun BusGasteizApp(
    dataRepository: DataRepository,
    locationRepository: LocationRepository,
    favoritesRepository: FavoritesRepository,
    appSettings: AppSettings
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.STOPS) }
    val stopsNavController = rememberNavController()
    val favoritesNavController = rememberNavController()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentDestination == AppDestinations.STOPS,
                onClick = {
                    if (currentDestination == AppDestinations.STOPS) {
                        stopsNavController.popBackStack("stops", false)
                    }
                    currentDestination = AppDestinations.STOPS
                },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_stops)) }
            )
            item(
                selected = currentDestination == AppDestinations.MAP,
                onClick = { currentDestination = AppDestinations.MAP },
                icon = { Icon(Icons.Default.Map, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_map)) }
            )
            item(
                selected = currentDestination == AppDestinations.FAVORITES,
                onClick = {
                    if (currentDestination == AppDestinations.FAVORITES) {
                        favoritesNavController.popBackStack("favorites", false)
                    }
                    currentDestination = AppDestinations.FAVORITES
                },
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_favorites)) }
            )
        }
    ) {
        when (currentDestination) {
            AppDestinations.STOPS -> {
                StopsNavGraph(
                    navController = stopsNavController,
                    dataRepository = dataRepository,
                    locationRepository = locationRepository,
                    favoritesRepository = favoritesRepository,
                    appSettings = appSettings
                )
            }
            AppDestinations.MAP -> {
                MapScreen(
                    dataRepository = dataRepository,
                    locationRepository = locationRepository,
                    favoritesRepository = favoritesRepository,
                    appSettings = appSettings,
                    navController = stopsNavController
                )
            }
            AppDestinations.FAVORITES -> {
                FavoritesNavGraph(
                    navController = favoritesNavController,
                    dataRepository = dataRepository,
                    locationRepository = locationRepository,
                    favoritesRepository = favoritesRepository
                )
            }
        }
    }
}

// MARK: - Grafo de navegación de la pestaña Paradas

@Composable
private fun StopsNavGraph(
    navController: NavController,
    dataRepository: DataRepository,
    locationRepository: LocationRepository,
    favoritesRepository: FavoritesRepository,
    appSettings: AppSettings
) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = "stops",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("stops") {
            NearbyStopsScreen(
                dataRepository = dataRepository,
                locationRepository = locationRepository,
                favoritesRepository = favoritesRepository,
                appSettings = appSettings,
                navController = navController,
                onAboutClick = { navController.navigate("about") }
            )
        }
        composable(
            route = "stop_detail/{stopId}/{distance}",
            arguments = listOf(
                navArgument("stopId") { type = NavType.StringType },
                navArgument("distance") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val distance = backStackEntry.arguments?.getFloat("distance")?.toDouble() ?: 0.0
            val gtfs = dataRepository.gtfsData.collectAsState().value ?: return@composable
            val stop = gtfs.stops[stopId] ?: return@composable
            StopDetailScreen(
                stop = stop,
                distance = distance,
                dataRepository = dataRepository,
                favoritesRepository = favoritesRepository,
                navController = navController
            )
        }
        composable(
            route = "route_arrivals/{stopId}/{distance}/{routeShortName}/{routeColor}",
            arguments = listOf(
                navArgument("stopId") { type = NavType.StringType },
                navArgument("distance") { type = NavType.FloatType },
                navArgument("routeShortName") { type = NavType.StringType },
                navArgument("routeColor") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val distance = backStackEntry.arguments?.getFloat("distance")?.toDouble() ?: 0.0
            val routeShortName = backStackEntry.arguments?.getString("routeShortName") ?: return@composable
            val routeColor = backStackEntry.arguments?.getString("routeColor") ?: ""
            val gtfs = dataRepository.gtfsData.collectAsState().value ?: return@composable
            val stop = gtfs.stops[stopId] ?: return@composable
            RouteArrivalsScreen(
                stop = stop,
                distance = distance,
                routeShortName = routeShortName,
                routeColor = routeColor,
                dataRepository = dataRepository,
                favoritesRepository = favoritesRepository,
                navController = navController
            )
        }
        composable("about") {
            AboutScreen(navController = navController, dataRepository = dataRepository)
        }
    }
}

// MARK: - Grafo de navegación de la pestaña Favoritos

@Composable
private fun FavoritesNavGraph(
    navController: NavController,
    dataRepository: DataRepository,
    locationRepository: LocationRepository,
    favoritesRepository: FavoritesRepository
) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = "favorites",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("favorites") {
            FavoritesScreen(
                dataRepository = dataRepository,
                locationRepository = locationRepository,
                favoritesRepository = favoritesRepository,
                navController = navController
            )
        }
        composable(
            route = "stop_detail/{stopId}/{distance}",
            arguments = listOf(
                navArgument("stopId") { type = NavType.StringType },
                navArgument("distance") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val distance = backStackEntry.arguments?.getFloat("distance")?.toDouble() ?: 0.0
            val gtfs = dataRepository.gtfsData.collectAsState().value ?: return@composable
            val stop = gtfs.stops[stopId] ?: return@composable
            StopDetailScreen(
                stop = stop,
                distance = distance,
                dataRepository = dataRepository,
                favoritesRepository = favoritesRepository,
                navController = navController
            )
        }
        composable(
            route = "route_arrivals/{stopId}/{distance}/{routeShortName}/{routeColor}",
            arguments = listOf(
                navArgument("stopId") { type = NavType.StringType },
                navArgument("distance") { type = NavType.FloatType },
                navArgument("routeShortName") { type = NavType.StringType },
                navArgument("routeColor") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val distance = backStackEntry.arguments?.getFloat("distance")?.toDouble() ?: 0.0
            val routeShortName = backStackEntry.arguments?.getString("routeShortName") ?: return@composable
            val routeColor = backStackEntry.arguments?.getString("routeColor") ?: ""
            val gtfs = dataRepository.gtfsData.collectAsState().value ?: return@composable
            val stop = gtfs.stops[stopId] ?: return@composable
            RouteArrivalsScreen(
                stop = stop,
                distance = distance,
                routeShortName = routeShortName,
                routeColor = routeColor,
                dataRepository = dataRepository,
                favoritesRepository = favoritesRepository,
                navController = navController
            )
        }
    }
}
