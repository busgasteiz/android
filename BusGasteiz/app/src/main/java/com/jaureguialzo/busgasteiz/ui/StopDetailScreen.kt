package com.jaureguialzo.busgasteiz.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jaureguialzo.busgasteiz.R
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
    val favoriteStopIds by favoritesRepository.favoriteStopIds.collectAsState()
    val isFavorite = favoriteStopIds.contains(stop.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stop.localizedName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { favoritesRepository.toggleFavoriteStop(stop.id) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        StopDetailContent(
            stop = stop,
            distance = distance,
            dataRepository = dataRepository,
            favoritesRepository = favoritesRepository,
            navController = navController,
            onClose = null,
            modifier = Modifier.padding(padding)
        )
    }
}
