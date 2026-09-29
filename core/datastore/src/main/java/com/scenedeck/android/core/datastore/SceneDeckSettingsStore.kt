package com.scenedeck.android.core.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// TODO(M-later): migrate to Proto DataStore (typed schema) once the protobuf toolchain
// is wired — Preferences DataStore was chosen for M2 to avoid the protoc/AGP-9
// built-in-Kotlin source-set fight. Keys below are the de-facto schema; keep stable.
private val Context.settingsDataStore by preferencesDataStore(name = "scenedeck_settings")

/**
 * Persisted user settings snapshot. Enum-like values are stored as names
 * (themeFamily/motionLevel map to :core:designsystem enums in :app).
 */
data class SettingsSnapshot(
    val themeFamily: String = "SCENEDECK",
    val darkMode: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val motionLevel: String = "FULL",
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lastUsedProfileId: Long? = null,
    val onboardingCompleted: Boolean = false,
    val confirmStartStream: Boolean = false,
    val confirmStopStream: Boolean = true,
    val confirmStartRecord: Boolean = false,
    val confirmStopRecord: Boolean = true,
)

/** Typed Preferences-DataStore access for user settings (FEATURE_SPEC §9). */
@Suppress("TooManyFunctions") // one setter per persisted key is the intended API
class SceneDeckSettingsStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val snapshot: Flow<SettingsSnapshot> = dataStore.data
        .map { prefs ->
            SettingsSnapshot(
                themeFamily = prefs[KEY_THEME_FAMILY] ?: "SCENEDECK",
                darkMode = prefs[KEY_DARK_MODE] ?: "SYSTEM",
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                motionLevel = prefs[KEY_MOTION_LEVEL] ?: "FULL",
                haptics = prefs[KEY_HAPTICS] ?: true,
                keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
                lastUsedProfileId = prefs[KEY_LAST_USED_PROFILE_ID],
                onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
                confirmStartStream = prefs[KEY_CONFIRM_START_STREAM] ?: false,
                confirmStopStream = prefs[KEY_CONFIRM_STOP_STREAM] ?: true,
                confirmStartRecord = prefs[KEY_CONFIRM_START_RECORD] ?: false,
                confirmStopRecord = prefs[KEY_CONFIRM_STOP_RECORD] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeFamily(value: String) = edit { it[KEY_THEME_FAMILY] = value }

    suspend fun setDarkMode(value: String) = edit { it[KEY_DARK_MODE] = value }

    suspend fun setDynamicColor(value: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = value }

    suspend fun setMotionLevel(value: String) = edit { it[KEY_MOTION_LEVEL] = value }

    suspend fun setHaptics(value: Boolean) = edit { it[KEY_HAPTICS] = value }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[KEY_KEEP_SCREEN_ON] = value }

    suspend fun setLastUsedProfileId(value: Long?) = edit { prefs ->
        if (value == null) prefs.remove(KEY_LAST_USED_PROFILE_ID) else prefs[KEY_LAST_USED_PROFILE_ID] = value
    }

    suspend fun setOnboardingCompleted(value: Boolean) = edit { it[KEY_ONBOARDING_COMPLETED] = value }

    suspend fun setConfirmStartStream(value: Boolean) = edit { it[KEY_CONFIRM_START_STREAM] = value }

    suspend fun setConfirmStopStream(value: Boolean) = edit { it[KEY_CONFIRM_STOP_STREAM] = value }

    suspend fun setConfirmStartRecord(value: Boolean) = edit { it[KEY_CONFIRM_START_RECORD] = value }

    suspend fun setConfirmStopRecord(value: Boolean) = edit { it[KEY_CONFIRM_STOP_RECORD] = value }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        dataStore.edit(transform)
    }

    companion object {
        /** Fresh isolated store for unit tests (avoids the delegate's singleton cache). */
        @VisibleForTesting
        fun forTesting(dataStore: DataStore<Preferences>): SceneDeckSettingsStore =
            SceneDeckSettingsStore(dataStore)

        private val KEY_THEME_FAMILY = stringPreferencesKey("themeFamily")
        private val KEY_DARK_MODE = stringPreferencesKey("darkMode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamicColor")
        private val KEY_MOTION_LEVEL = stringPreferencesKey("motionLevel")
        private val KEY_HAPTICS = booleanPreferencesKey("haptics")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
        private val KEY_LAST_USED_PROFILE_ID = longPreferencesKey("lastUsedProfileId")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboardingCompleted")
        private val KEY_CONFIRM_START_STREAM = booleanPreferencesKey("confirmStartStream")
        private val KEY_CONFIRM_STOP_STREAM = booleanPreferencesKey("confirmStopStream")
        private val KEY_CONFIRM_START_RECORD = booleanPreferencesKey("confirmStartRecord")
        private val KEY_CONFIRM_STOP_RECORD = booleanPreferencesKey("confirmStopRecord")
    }
}
