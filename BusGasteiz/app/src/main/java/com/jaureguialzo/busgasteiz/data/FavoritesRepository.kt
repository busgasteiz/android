package com.jaureguialzo.busgasteiz.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// MARK: - Favoritos sincronizados con Firestore (auth anónima)

class FavoritesRepository {

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _favoriteStopIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteStopIds: StateFlow<Set<String>> = _favoriteStopIds

    private val _favoriteRouteKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteRouteKeys: StateFlow<Set<String>> = _favoriteRouteKeys

    val isEmpty: Boolean
        get() = _favoriteStopIds.value.isEmpty() && _favoriteRouteKeys.value.isEmpty()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            ensureSignedIn()
            attachListener()
        }
    }

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                println("[FavoritesRepository] Error al iniciar sesión anónima: $e")
            }
        }
    }

    private fun attachListener() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("favorites").document("data")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("[FavoritesRepository] Error Firestore: $error")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val stops = (snapshot.get("stops") as? List<*>)
                        ?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                    val routes = (snapshot.get("routes") as? List<*>)
                        ?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                    _favoriteStopIds.value = stops
                    _favoriteRouteKeys.value = routes
                }
            }
    }

    fun isStopFavorite(stopId: String): Boolean = _favoriteStopIds.value.contains(stopId)

    fun isRouteFavorite(stopId: String, routeShortName: String): Boolean =
        _favoriteRouteKeys.value.contains(routeKey(stopId, routeShortName))

    fun toggleFavoriteStop(stopId: String) {
        val current = _favoriteStopIds.value.toMutableSet()
        if (current.contains(stopId)) current.remove(stopId) else current.add(stopId)
        _favoriteStopIds.value = current
        saveToFirestore()
    }

    fun toggleFavoriteRoute(stopId: String, routeShortName: String) {
        val key = routeKey(stopId, routeShortName)
        val current = _favoriteRouteKeys.value.toMutableSet()
        if (current.contains(key)) current.remove(key) else current.add(key)
        _favoriteRouteKeys.value = current
        saveToFirestore()
    }

    private fun saveToFirestore() {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "stops" to _favoriteStopIds.value.toList(),
            "routes" to _favoriteRouteKeys.value.toList()
        )
        db.collection("users").document(uid).collection("favorites").document("data")
            .set(data, SetOptions.merge())
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
