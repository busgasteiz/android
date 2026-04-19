package com.jaureguialzo.busgasteiz

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.jaureguialzo.busgasteiz.R

// MARK: - Destinos de navegación principal (pestaña inferior)

enum class AppDestinations(val labelRes: Int, val icon: ImageVector) {
    STOPS(R.string.tab_stops, Icons.Default.List),
    MAP(R.string.tab_map, Icons.Default.Map),
    FAVORITES(R.string.tab_favorites, Icons.Default.Star)
}
