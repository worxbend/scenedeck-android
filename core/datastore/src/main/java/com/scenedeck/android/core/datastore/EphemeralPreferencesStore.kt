package com.scenedeck.android.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local preferences for the isolated demo graph. Never opens a file. */
class EphemeralPreferencesStore : DataStore<Preferences> {
    private val values = MutableStateFlow(emptyPreferences())
    private val mutation = Mutex()
    override val data = values.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutation.withLock {
            val updated = transform(values.value).toPreferences()
            currentCoroutineContext().ensureActive()
            values.value = updated
            updated
        }
}
