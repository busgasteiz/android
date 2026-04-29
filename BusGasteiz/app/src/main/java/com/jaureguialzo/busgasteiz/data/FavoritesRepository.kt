package com.jaureguialzo.busgasteiz.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// MARK: - Favoritos persistidos localmente con DataStore

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {

    private val STOPS_KEY = stringSetPreferencesKey("favorite_stops")
    private val ROUTES_KEY = stringSetPreferencesKey("favorite_routes")

    private val _favoriteStopIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteStopIds: StateFlow<Set<String>> = _favoriteStopIds

    private val _favoriteRouteKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteRouteKeys: StateFlow<Set<String>> = _favoriteRouteKeys

    val isEmpty: Boolean
        get() = _favoriteStopIds.value.isEmpty() && _favoriteRouteKeys.value.isEmpty()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.favoritesDataStore.data.first()
            _favoriteStopIds.value = prefs[STOPS_KEY] ?: emptySet()
            _favoriteRouteKeys.value = prefs[ROUTES_KEY] ?: emptySet()
        }
    }

    fun isStopFavorite(stopId: String): Boolean = _favoriteStopIds.value.contains(stopId)

    fun isRouteFavorite(stopId: String, routeShortName: String): Boolean =
        _favoriteRouteKeys.value.contains(routeKey(stopId, routeShortName))

    fun toggleFavoriteStop(stopId: String) {
        val current = _favoriteStopIds.value.toMutableSet()
        if (current.contains(stopId)) current.remove(stopId) else current.add(stopId)
        _favoriteStopIds.value = current
        saveToDataStore()
    }

    fun toggleFavoriteRoute(stopId: String, routeShortName: String) {
        val key = routeKey(stopId, routeShortName)
        val current = _favoriteRouteKeys.value.toMutableSet()
        if (current.contains(key)) current.remove(key) else current.add(key)
        _favoriteRouteKeys.value = current
        saveToDataStore()
    }

    private fun saveToDataStore() {
        CoroutineScope(Dispatchers.IO).launch {
            context.favoritesDataStore.edit { prefs ->
                prefs[STOPS_KEY] = _favoriteStopIds.value
                prefs[ROUTES_KEY] = _favoriteRouteKeys.value
            }
        }
    }

    private fun routeKey(stopId: String, routeShortName: String) = "$stopId::$routeShortName"

    /// Clase de clave de ruta parseada (equivalente a FavoritesManager.ParsedRouteKey de iOS)
    data class ParsedRouteKey(val stopId: String, val routeShortName: String) {
        val id: String get() = "$stopId::$routeShortName"
    }

    val parsedRouteKeys: List<ParsedRouteKey>
        get() = _favoriteRouteKeys.value.mapNotNull { key ->
            // Split on the LAST "::" to correctly handle stop IDs that contain ":" (e.g. Euskotren
            // StopPlace IDs like "ES:Euskotren:StopPlace:1559:" have a trailing colon, which
            // combined with the "::" separator produces ":::". Using the last "::" gives the
            // correct stopId including the trailing colon.
            val idx = key.lastIndexOf("::")
            if (idx < 0) return@mapNotNull null
            val stopId = key.substring(0, idx)
            val routeShortName = key.substring(idx + 2)
            if (stopId.isEmpty() || routeShortName.isEmpty()) return@mapNotNull null
            ParsedRouteKey(stopId = stopId, routeShortName = routeShortName)
        }.sortedBy { it.id }
}
