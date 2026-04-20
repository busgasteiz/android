package com.jaureguialzo.busgasteiz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Icono circular de parada (lista y mapa)

@Composable
fun StopIcon(
    size: Dp = 28.dp,
    isTram: Boolean,
    hasArrivals: Boolean,
    hasAlert: Boolean = false,
    modifier: Modifier = Modifier,
    iconSize: Dp = size * 0.40f
) {
    val fillColor = if (hasArrivals) MaterialTheme.colorScheme.primary else Color.Gray

    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        // Reborde blanco exterior con sombra
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + 4.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            // Fondo de color sólido
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(fillColor)
            ) {
                Icon(
                    imageVector = if (isTram) Icons.Default.Tram else Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        // Badge de alerta rojo en la esquina superior derecha
        if (hasAlert) {
            val badgeSize = maxOf(10.dp, size * 0.32f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(badgeSize + 2.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(badgeSize * 0.6f)
                    )
                }
            }
        }
    }
}
