package com.scenedeck.android.core.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// TODO(M-later): migrate to Proto DataStore (typed schema) once the protobuf toolchain
// is wired — Preferences DataStore was chosen for M2 to avoid the protoc/AGP-9
// built-in-Kotlin source-set fight. Keys below are the de-facto schema; keep stable.
private val Context.settingsDataStore by preferencesDataStore(name = "scenedeck_settings")

/**
 * Persisted user settings snapshot. Enum-like values are stored as names (themeFamily/motionLevel
 * map to :core:designsystem enums in :app).
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
    /** Mixer: input names allowed to appear (empty = all). */
    val audioAllowList: Set<String> = emptySet(),
    /** Mixer: locally locked inputs (UI-only lock). */
    val lockedInputs: Set<String> = emptySet(),
    /** Mixer page mode: ACTIVE / SELECTED / PINNED. */
    val mixerMode: String = "ACTIVE",
    val mixerSelectedScene: String? = null,
    /** Mixer grouping: SCOPE / SCENE_PATH / NONE. */
    val mixerGrouping: String = "SCOPE",
    /** Scene preview thumbnails on deck cards (M6). */
    val scenePreviewsEnabled: Boolean = true,
)

/** Typed Preferences-DataStore access for user settings (FEATURE_SPEC §9). */
@Suppress("TooManyFunctions") // one setter per persisted key is the intended API
class SceneDeckSettingsStore private constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)

    val snapshot: Flow<SettingsSnapshot> =
        dataStore.data.map { it.toSnapshot() }.distinctUntilChanged()

    /** Applies read/modify/write as one DataStore transaction, preserving concurrent edits. */
    suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) = edit { prefs ->
        val updated = transform(prefs.toSnapshot())
        prefs[KEY_THEME_FAMILY] = updated.themeFamily
        prefs[KEY_DARK_MODE] = updated.darkMode
        prefs[KEY_DYNAMIC_COLOR] = updated.dynamicColor
        prefs[KEY_MOTION_LEVEL] = updated.motionLevel
        prefs[KEY_HAPTICS] = updated.haptics
        prefs[KEY_KEEP_SCREEN_ON] = updated.keepScreenOn
        prefs[KEY_ONBOARDING_COMPLETED] = updated.onboardingCompleted
        prefs[KEY_CONFIRM_START_STREAM] = updated.confirmStartStream
        prefs[KEY_CONFIRM_STOP_STREAM] = updated.confirmStopStream
        prefs[KEY_CONFIRM_START_RECORD] = updated.confirmStartRecord
        prefs[KEY_CONFIRM_STOP_RECORD] = updated.confirmStopRecord
        prefs[KEY_AUDIO_ALLOW_LIST] = updated.audioAllowList
        prefs[KEY_LOCKED_INPUTS] = updated.lockedInputs
        prefs[KEY_MIXER_MODE] = updated.mixerMode
        prefs[KEY_MIXER_GROUPING] = updated.mixerGrouping
        prefs[KEY_SCENE_PREVIEWS_ENABLED] = updated.scenePreviewsEnabled
        val profileId = updated.lastUsedProfileId
        if (profileId == null) prefs.remove(KEY_LAST_USED_PROFILE_ID)
        else prefs[KEY_LAST_USED_PROFILE_ID] = profileId
        val scene = updated.mixerSelectedScene
        if (scene == null) prefs.remove(KEY_MIXER_SELECTED_SCENE)
        else prefs[KEY_MIXER_SELECTED_SCENE] = scene
    }

    suspend fun setInputLocked(inputName: String, locked: Boolean) = edit { prefs ->
        val current = prefs[KEY_LOCKED_INPUTS].orEmpty()
        prefs[KEY_LOCKED_INPUTS] = if (locked) current + inputName else current - inputName
    }

    private fun Preferences.toSnapshot(): SettingsSnapshot =
        SettingsSnapshot(
            themeFamily = valueOrDefault(KEY_THEME_FAMILY, "SCENEDECK"),
            darkMode = valueOrDefault(KEY_DARK_MODE, "SYSTEM"),
            dynamicColor = valueOrDefault(KEY_DYNAMIC_COLOR, false),
            motionLevel = valueOrDefault(KEY_MOTION_LEVEL, "FULL"),
            haptics = valueOrDefault(KEY_HAPTICS, true),
            keepScreenOn = valueOrDefault(KEY_KEEP_SCREEN_ON, false),
            lastUsedProfileId = this[KEY_LAST_USED_PROFILE_ID],
            onboardingCompleted = valueOrDefault(KEY_ONBOARDING_COMPLETED, false),
            confirmStartStream = valueOrDefault(KEY_CONFIRM_START_STREAM, false),
            confirmStopStream = valueOrDefault(KEY_CONFIRM_STOP_STREAM, true),
            confirmStartRecord = valueOrDefault(KEY_CONFIRM_START_RECORD, false),
            confirmStopRecord = valueOrDefault(KEY_CONFIRM_STOP_RECORD, true),
            audioAllowList = valueOrDefault(KEY_AUDIO_ALLOW_LIST, emptySet()),
            lockedInputs = valueOrDefault(KEY_LOCKED_INPUTS, emptySet()),
            mixerMode = valueOrDefault(KEY_MIXER_MODE, "ACTIVE"),
            mixerSelectedScene = this[KEY_MIXER_SELECTED_SCENE],
            mixerGrouping = valueOrDefault(KEY_MIXER_GROUPING, "SCOPE"),
            scenePreviewsEnabled = valueOrDefault(KEY_SCENE_PREVIEWS_ENABLED, true),
        )

    private fun <T> Preferences.valueOrDefault(key: Preferences.Key<T>, default: T): T =
        this[key] ?: default

    suspend fun setThemeFamily(value: String) = edit { it[KEY_THEME_FAMILY] = value }

    suspend fun setDarkMode(value: String) = edit { it[KEY_DARK_MODE] = value }

    suspend fun setDynamicColor(value: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = value }

    suspend fun setMotionLevel(value: String) = edit { it[KEY_MOTION_LEVEL] = value }

    suspend fun setHaptics(value: Boolean) = edit { it[KEY_HAPTICS] = value }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[KEY_KEEP_SCREEN_ON] = value }

    suspend fun setLastUsedProfileId(value: Long?) = edit { prefs ->
        if (value == null) prefs.remove(KEY_LAST_USED_PROFILE_ID)
        else prefs[KEY_LAST_USED_PROFILE_ID] = value
    }

    suspend fun setOnboardingCompleted(value: Boolean) = edit {
        it[KEY_ONBOARDING_COMPLETED] = value
    }

    suspend fun setConfirmStartStream(value: Boolean) = edit {
        it[KEY_CONFIRM_START_STREAM] = value
    }

    suspend fun setConfirmStopStream(value: Boolean) = edit { it[KEY_CONFIRM_STOP_STREAM] = value }

    suspend fun setConfirmStartRecord(value: Boolean) = edit {
        it[KEY_CONFIRM_START_RECORD] = value
    }

    suspend fun setConfirmStopRecord(value: Boolean) = edit { it[KEY_CONFIRM_STOP_RECORD] = value }

    suspend fun setAudioAllowList(value: Set<String>) = edit { it[KEY_AUDIO_ALLOW_LIST] = value }

    suspend fun setLockedInputs(value: Set<String>) = edit { it[KEY_LOCKED_INPUTS] = value }

    suspend fun setMixerMode(value: String) = edit { it[KEY_MIXER_MODE] = value }

    suspend fun setMixerSelectedScene(value: String?) = edit { prefs ->
        if (value == null) prefs.remove(KEY_MIXER_SELECTED_SCENE)
        else prefs[KEY_MIXER_SELECTED_SCENE] = value
    }

    suspend fun setMixerGrouping(value: String) = edit { it[KEY_MIXER_GROUPING] = value }

    suspend fun setScenePreviewsEnabled(value: Boolean) = edit {
        it[KEY_SCENE_PREVIEWS_ENABLED] = value
    }

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
        private val KEY_AUDIO_ALLOW_LIST = stringSetPreferencesKey("audioAllowList")
        private val KEY_LOCKED_INPUTS = stringSetPreferencesKey("lockedInputs")
        private val KEY_MIXER_MODE = stringPreferencesKey("mixerMode")
        private val KEY_MIXER_SELECTED_SCENE = stringPreferencesKey("mixerSelectedScene")
        private val KEY_MIXER_GROUPING = stringPreferencesKey("mixerGrouping")
        private val KEY_SCENE_PREVIEWS_ENABLED = booleanPreferencesKey("scenePreviewsEnabled")
    }
}
