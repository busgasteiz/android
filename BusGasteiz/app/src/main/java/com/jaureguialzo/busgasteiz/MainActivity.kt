package com.jaureguialzo.busgasteiz

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.jaureguialzo.busgasteiz.data.DataRepository
import com.jaureguialzo.busgasteiz.data.FavoritesRepository
import com.jaureguialzo.busgasteiz.data.LocationRepository
import com.jaureguialzo.busgasteiz.settings.AppSettings
import com.jaureguialzo.busgasteiz.ui.theme.BusGasteizTheme

// El lint de androidx.activity emite InvalidFragmentVersionForActivityResult en proyectos
// sin Fragment porque su regla asume que Fragment siempre está presente. Al ser una app
// Compose pura (sin Fragment), registerForActivityResult en ComponentActivity funciona
// correctamente y la advertencia es un falso positivo.
@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private lateinit var locationRepository: LocationRepository
    private lateinit var dataRepository: DataRepository

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationRepository.startUpdates()
        } else {
            locationRepository.resolveActivePosition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dataRepository = DataRepository.getInstance(applicationContext)
        this.dataRepository = dataRepository
        locationRepository = LocationRepository(this)
        val favoritesRepository = FavoritesRepository(applicationContext)
        val appSettings = AppSettings(this)

        // Solicitar permisos de localización
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            locationRepository.startUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            BusGasteizTheme {
                // Iniciar carga de datos al arrancar
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    dataRepository.refreshIfNeeded()
                }
                BusGasteizApp(
                    dataRepository = dataRepository,
                    locationRepository = locationRepository,
                    favoritesRepository = favoritesRepository,
                    appSettings = appSettings
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        locationRepository.stopUpdates()
    }

    override fun onStart() {
        super.onStart()
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            locationRepository.startUpdates()
        }
        if (dataRepository.needsRefresh && dataRepository.gtfsData.value != null) {
            dataRepository.forceRefresh()
        } else {
            dataRepository.refreshIfNeeded()
        }
    }
}
