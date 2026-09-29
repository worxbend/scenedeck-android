package com.scenedeck.android.core.data

import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.datastore.SettingsSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Typed user settings (FEATURE_SPEC §9), persisted via :core:datastore. */
data class UserSettings(
    val themeFamily: String = "SCENEDECK",
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val motionLevel: String = "FULL",
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lastUsedProfileId: Long? = null,
    val onboardingCompleted: Boolean = false,
    /** Output Safety: ask before starting the stream (FEATURE_SPEC §4). */
    val confirmStartStream: Boolean = false,
    /** Output Safety: ask before stopping the stream. */
    val confirmStopStream: Boolean = true,
    /** Output Safety: ask before starting a recording. */
    val confirmStartRecord: Boolean = false,
    /** Output Safety: ask before stopping a recording. */
    val confirmStopRecord: Boolean = true,
    val audioAllowList: Set<String> = emptySet(),
    val lockedInputs: Set<String> = emptySet(),
    val mixerMode: String = "ACTIVE",
    val mixerSelectedScene: String? = null,
    val mixerGrouping: String = "SCOPE",
)

@Singleton
@Suppress("TooManyFunctions") // one setter per persisted setting is the intended API
class SettingsRepository @Inject constructor(
    private val store: SceneDeckSettingsStore,
) {
    val settings: Flow<UserSettings> = store.snapshot.map { it.toUserSettings() }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val current = store.snapshot.first().toUserSettings()
        persist(transform(current))
    }

    suspend fun setThemeFamily(value: String) = store.setThemeFamily(value)

    suspend fun setDarkMode(value: DarkMode) = store.setDarkMode(value.name)

    suspend fun setDynamicColor(value: Boolean) = store.setDynamicColor(value)

    suspend fun setMotionLevel(value: String) = store.setMotionLevel(value)

    suspend fun setHaptics(value: Boolean) = store.setHaptics(value)

    suspend fun setKeepScreenOn(value: Boolean) = store.setKeepScreenOn(value)

    suspend fun setLastUsedProfileId(value: Long?) = store.setLastUsedProfileId(value)

    suspend fun setOnboardingCompleted(value: Boolean) = store.setOnboardingCompleted(value)

    suspend fun setConfirmStartStream(value: Boolean) = store.setConfirmStartStream(value)

    suspend fun setConfirmStopStream(value: Boolean) = store.setConfirmStopStream(value)

    suspend fun setConfirmStartRecord(value: Boolean) = store.setConfirmStartRecord(value)

    suspend fun setConfirmStopRecord(value: Boolean) = store.setConfirmStopRecord(value)

    suspend fun setAudioAllowList(value: Set<String>) = store.setAudioAllowList(value)

    suspend fun setLockedInputs(value: Set<String>) = store.setLockedInputs(value)

    suspend fun setMixerMode(value: String) = store.setMixerMode(value)

    suspend fun setMixerSelectedScene(value: String?) = store.setMixerSelectedScene(value)

    suspend fun setMixerGrouping(value: String) = store.setMixerGrouping(value)

    private suspend fun persist(settings: UserSettings) {
        store.setThemeFamily(settings.themeFamily)
        store.setDarkMode(settings.darkMode.name)
        store.setDynamicColor(settings.dynamicColor)
        store.setMotionLevel(settings.motionLevel)
        store.setHaptics(settings.haptics)
        store.setKeepScreenOn(settings.keepScreenOn)
        store.setLastUsedProfileId(settings.lastUsedProfileId)
        store.setOnboardingCompleted(settings.onboardingCompleted)
        store.setConfirmStartStream(settings.confirmStartStream)
        store.setConfirmStopStream(settings.confirmStopStream)
        store.setConfirmStartRecord(settings.confirmStartRecord)
        store.setConfirmStopRecord(settings.confirmStopRecord)
        store.setAudioAllowList(settings.audioAllowList)
        store.setLockedInputs(settings.lockedInputs)
        store.setMixerMode(settings.mixerMode)
        store.setMixerSelectedScene(settings.mixerSelectedScene)
        store.setMixerGrouping(settings.mixerGrouping)
    }

    private fun SettingsSnapshot.toUserSettings() = UserSettings(
        themeFamily = themeFamily,
        darkMode = darkMode.toDarkModeOrDefault(),
        dynamicColor = dynamicColor,
        motionLevel = motionLevel,
        haptics = haptics,
        keepScreenOn = keepScreenOn,
        lastUsedProfileId = lastUsedProfileId,
        onboardingCompleted = onboardingCompleted,
        confirmStartStream = confirmStartStream,
        confirmStopStream = confirmStopStream,
        confirmStartRecord = confirmStartRecord,
        confirmStopRecord = confirmStopRecord,
        audioAllowList = audioAllowList,
        lockedInputs = lockedInputs,
        mixerMode = mixerMode,
        mixerSelectedScene = mixerSelectedScene,
        mixerGrouping = mixerGrouping,
    )

    private fun String.toDarkModeOrDefault(): DarkMode =
        runCatching { DarkMode.valueOf(this) }.getOrDefault(DarkMode.SYSTEM)
}
