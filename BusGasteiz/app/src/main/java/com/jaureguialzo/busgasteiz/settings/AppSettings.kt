package com.jaureguialzo.busgasteiz.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// MARK: - Ajustes de la app persistidos con DataStore

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class AppSettings(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob())
    private val KEY_SEARCH_RADIUS = floatPreferencesKey("search_radius")

    val searchRadiusFlow: StateFlow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_SEARCH_RADIUS] ?: 200f }
        .stateIn(scope, SharingStarted.Eagerly, 200f)

    suspend fun setSearchRadius(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEARCH_RADIUS] = value
        }
    }
}
