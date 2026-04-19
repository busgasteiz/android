package com.jaureguialzo.busgasteiz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - Badge cuadrado de línea de autobús/tranvía

@Composable
fun RouteBadge(
    shortName: String,
    colorHex: String,
    hasAlert: Boolean = false,
    outerSize: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val inner = outerSize - 4.dp
    val radius = inner * 10f / 44f
    val fontSize = maxOf(inner.value * 15f / 44f, 10f).sp

    val fillColor = parseHexColor(colorHex)
    val textColor = luminanceTextColor(colorHex)

    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        // Reborde blanco con sombra
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(outerSize)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(radius + 2.dp))
                .clip(RoundedCornerShape(radius + 2.dp))
                .background(Color.White)
        ) {
            // Fondo de color de la línea
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(inner)
                    .clip(RoundedCornerShape(radius))
                    .background(fillColor)
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = shortName,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        // Badge de alerta rojo
        if (hasAlert) {
            val badgeSize = maxOf(9.dp, outerSize * 0.27f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(badgeSize + 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(RoundedCornerShape(50))
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

/// Convierte un string hexadecimal de 6 dígitos a Color, o Color primario como fallback.
fun parseHexColor(hex: String): Color {
    val h = hex.trim().trimStart('#')
    if (h.length != 6) return Color(0xFF3F51B5)
    return try {
        val value = h.toLong(16)
        Color(
            red = ((value shr 16) and 0xFF).toFloat() / 255f,
            green = ((value shr 8) and 0xFF).toFloat() / 255f,
            blue = (value and 0xFF).toFloat() / 255f
        )
    } catch (e: Exception) {
        Color(0xFF3F51B5)
    }
}

/// Devuelve Color.Black o Color.White según la luminancia del fondo (umbral 140, igual que iOS).
fun luminanceTextColor(hex: String): Color {
    val h = hex.trim().trimStart('#').lowercase()
    if (h.isEmpty() || h == "ffffff") return Color.Black
    if (h.length != 6) return Color.White
    return try {
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        val lum = 0.299 * r + 0.587 * g + 0.114 * b
        if (lum > 140) Color.Black else Color.White
    } catch (e: Exception) {
        Color.White
    }
}
