package com.jaureguialzo.busgasteiz.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jaureguialzo.busgasteiz.BuildConfig
import com.jaureguialzo.busgasteiz.R
import com.jaureguialzo.busgasteiz.data.DataRepository

// MARK: - Pantalla de información de la app

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    dataRepository: DataRepository
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            // Copyright
            item {
                SectionHeader(text = "")
                Text(
                    text = stringResource(R.string.copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
            }

            // Términos de servicio
            item {
                SectionHeader(text = stringResource(R.string.terms_of_service))
                Text(
                    text = stringResource(R.string.terms_of_service_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
            }

            // Política de privacidad
            item {
                SectionHeader(text = stringResource(R.string.privacy_policy))
                Text(
                    text = stringResource(R.string.privacy_policy_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
            }

            // Licencia de la app
            item {
                SectionHeader(text = stringResource(R.string.app_license))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Apache License 2.0")
                }
                HorizontalDivider()
            }

            // Fuentes de datos
            item {
                SectionHeader(text = stringResource(R.string.data_sources))
                DataSourceLink(
                    name = "Ayuntamiento de Vitoria-Gasteiz – TUVISA bus lines",
                    license = "CC BY",
                    url = "https://www.vitoria-gasteiz.org/wb021/was/contenidoAction.do?uid=app_j34_0021&idioma=es"
                )
                DataSourceLink(
                    name = "Ayuntamiento de Vitoria-Gasteiz – TUVISA real-time data",
                    license = "CC BY",
                    url = "https://www.vitoria-gasteiz.org/wb021/was/contenidoAction.do?uid=app_j34_0022&idioma=es"
                )
                DataSourceLink(
                    name = "Open Data Euskadi – Moveuskadi",
                    license = "CC BY",
                    url = "https://opendata.euskadi.eus/catalogo/-/moveuskadi-datos-de-la-red-de-transporte-publico-de-euskadi-operadores-horarios-paradas-calendario-tarifas-etc/"
                )
                HorizontalDivider()
            }

            // Código fuente
            item {
                SectionHeader(text = stringResource(R.string.support_source))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/busgasteiz"))
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.github_repo))
                }
                HorizontalDivider()
            }

            // Paletas de colores
            item {
                SectionHeader(text = stringResource(R.string.color_palettes))
                DataSourceLink(
                    name = "Autumn Rainbow – COLOURlovers",
                    license = "CC BY-NC-SA",
                    url = "http://www.colourlovers.com/palette/3240116/%E2%80%A2Autumn_Rainbow%E2%80%A2"
                )
                HorizontalDivider()
            }

            // Sección de pruebas (solo DEBUG)
            if (BuildConfig.DEBUG) {
                item {
                    SectionHeader(text = stringResource(R.string.testing))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.simulate_alerts),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = dataRepository.simulateAlerts,
                            onCheckedChange = { dataRepository.simulateAlerts = it }
                        )
                    }
                    Text(
                        stringResource(R.string.simulate_alerts_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DataSourceLink(name: String, license: String, url: String) {
    val context = LocalContext.current
    TextButton(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Column {
            Text(name, style = MaterialTheme.typography.bodySmall)
            Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
