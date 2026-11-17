package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ScopeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "lightmoon_studio_prefs")

class StudioPreferences(private val context: Context) {

    private object Keys {
        val SCOPE_MODE = stringPreferencesKey("scope_mode")
        val SHOW_CLIPPING = booleanPreferencesKey("show_clipping")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_FILMSTRIP = booleanPreferencesKey("show_filmstrip")
    }

    val scopeMode: Flow<ScopeMode> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[Keys.SCOPE_MODE]
        ScopeMode.entries.firstOrNull { it.name == raw } ?: ScopeMode.HISTOGRAM
    }

    val showClipping: Flow<Boolean> = context.studioDataStore.data.map { prefs ->
        prefs[Keys.SHOW_CLIPPING] ?: false
    }

    val gridColumns: Flow<Int> = context.studioDataStore.data.map { prefs ->
        (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(2, 5)
    }

    val showFilmstrip: Flow<Boolean> = context.studioDataStore.data.map { prefs ->
        prefs[Keys.SHOW_FILMSTRIP] ?: false
    }

    suspend fun setScopeMode(mode: ScopeMode) {
        context.studioDataStore.edit { it[Keys.SCOPE_MODE] = mode.name }
    }

    suspend fun setShowClipping(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.SHOW_CLIPPING] = enabled }
    }

    suspend fun setGridColumns(columns: Int) {
        context.studioDataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setShowFilmstrip(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.SHOW_FILMSTRIP] = enabled }
    }
}
