package com.jaureguialzo.busgasteiz.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jaureguialzo.busgasteiz.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// MARK: - Wrapper de FusedLocationProviderClient

class LocationRepository(private val context: Context) {

    companion object {
        const val VITORIA_LAT = 42.846667
        const val VITORIA_LON = -2.673056
        const val VITORIA_RADIUS_M = 10_000f
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    private fun defaultLocation() = Location("default").apply {
        latitude = VITORIA_LAT
        longitude = VITORIA_LON
    }

    /// Posición activa usada por las vistas para listar paradas y centrar el mapa.
    /// Inicializada con el centro de Vitoria-Gasteiz; solo cambia cuando el usuario
    /// pulsa el botón de localización o desplaza el mapa manualmente.
    private val _activePosition = MutableStateFlow(defaultLocation())
    val activePosition: StateFlow<Location> = _activePosition

    /// Se incrementa cuando se resuelve la posición activa (al inicio o al pulsar el botón
    /// de localización). Las vistas lo observan para reaccionar al cambio de posición.
    private val _locationVersion = MutableStateFlow(0)
    val locationVersion: StateFlow<Int> = _locationVersion

    /// Mensaje de aviso para el toast de localización. Se establece cuando se usa
    /// la posición predeterminada; se limpia después de mostrarse.
    private val _positionToastMessage = MutableStateFlow<String?>(null)
    val positionToastMessage: StateFlow<String?> = _positionToastMessage

    @Volatile private var hasResolvedInitialPosition = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                _location.value = loc
                if (!hasResolvedInitialPosition) {
                    hasResolvedInitialPosition = true
                    resolveActivePosition()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(50f)
            .build()
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stopUpdates() {
        fusedClient.removeLocationUpdates(callback)
    }

    /// Aplica las reglas de posición:
    /// - Sin permiso o sin fix GPS → posición predeterminada + toast
    /// - Dentro de 10 km del centro de Vitoria → posición GPS
    /// - Fuera de 10 km → posición predeterminada + toast
    ///
    /// Llama a este método al pulsar el botón de localización.
    fun resolveActivePosition() {
        val loc = _location.value
        val def = defaultLocation()
        if (loc != null && loc.distanceTo(def) <= VITORIA_RADIUS_M) {
            _activePosition.value = loc
        } else {
            _activePosition.value = def
            _positionToastMessage.value = if (loc == null) {
                context.getString(R.string.location_unavailable_toast)
            } else {
                context.getString(R.string.outside_area_toast)
            }
        }
        _locationVersion.value++
    }

    /// Actualiza la posición activa al centro del mapa cuando el usuario desplaza el mapa.
    /// No modifica locationVersion (no es una resolución de posición explícita).
    fun setActivePositionToMapCenter(lat: Double, lon: Double) {
        val loc = Location("map").apply {
            latitude = lat
            longitude = lon
        }
        _activePosition.value = loc
    }

    fun clearToastMessage() {
        _positionToastMessage.value = null
    }
}
