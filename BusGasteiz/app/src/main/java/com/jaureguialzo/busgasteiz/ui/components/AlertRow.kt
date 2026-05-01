package com.jaureguialzo.busgasteiz.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.ServiceAlert

// MARK: - Fila de alerta de servicio

@Composable
private fun effectText(effect: Int): String? = when (effect) {
    1  -> stringResource(R.string.alert_effect_no_service)
    2  -> stringResource(R.string.alert_effect_reduced_service)
    3  -> stringResource(R.string.alert_effect_significant_delays)
    4  -> stringResource(R.string.alert_effect_detour)
    5  -> stringResource(R.string.alert_effect_additional_service)
    6  -> stringResource(R.string.alert_effect_modified_service)
    9  -> stringResource(R.string.alert_effect_stop_moved)
    11 -> stringResource(R.string.alert_effect_accessibility_issue)
    else -> null
}

@Composable
fun AlertRow(
    alert: ServiceAlert,
    modifier: Modifier = Modifier
) {
    // Si description == header (p.ej. alertas de manifestación), usar el texto de efecto
    val displayDescription = if (alert.descriptionText.isNotEmpty() && alert.descriptionText != alert.headerText) {
        alert.descriptionText
    } else {
        effectText(alert.effect)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                if (alert.headerText.isNotEmpty()) {
                    Text(
                        text = alert.headerText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    )
                }
                if (displayDescription != null) {
                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
