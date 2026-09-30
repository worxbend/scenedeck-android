package com.scenedeck.android.background

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// App-private store: :core:datastore's schema is owned by :core, and M7's keep-alive
// flag is a platform concern of :app (foreground service), so it lives here.
private val Context.backgroundDataStore by preferencesDataStore(name = "scenedeck_background")

/** App-level background behavior flags (separate from :core user settings). */
data class BackgroundSettings(
    /** Keep the OBS session alive via a foreground service when the app is backgrounded. */
    val keepAliveEnabled: Boolean = false
)

class BackgroundSettingsStore private constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.backgroundDataStore)

    val settings: Flow<BackgroundSettings> =
        dataStore.data.map { prefs ->
            BackgroundSettings(keepAliveEnabled = prefs[KEY_KEEP_ALIVE] ?: false)
        }

    suspend fun setKeepAliveEnabled(value: Boolean) {
        dataStore.edit { it[KEY_KEEP_ALIVE] = value }
    }

    private companion object {
        val KEY_KEEP_ALIVE = booleanPreferencesKey("keepAliveEnabled")
    }
}
