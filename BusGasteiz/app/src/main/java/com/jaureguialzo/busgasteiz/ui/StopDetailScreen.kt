package com.jaureguialzo.busgasteiz.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.StopInfo

// MARK: - Pantalla de detalle de parada como destino push (wraps StopDetailContent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    stop: StopInfo,
    distance: Double,
    dataRepository: DataRepository,
    favoritesRepository: FavoritesRepository,
    navController: NavController
) {
    StopDetailContent(
        stop = stop,
        distance = distance,
        dataRepository = dataRepository,
        favoritesRepository = favoritesRepository,
        navController = navController,
        onClose = null
    )
}
