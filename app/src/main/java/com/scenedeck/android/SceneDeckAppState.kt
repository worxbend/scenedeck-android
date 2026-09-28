package com.scenedeck.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.UserSettings
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * Hoisted, session-scoped app state: theme family, dark-mode override, dynamic color,
 * motion level, haptics and keep-screen-on. Survives configuration changes via
 * [rememberSaveable] (instant restoration layer); the source of truth is
 * [SettingsRepository] (Preferences DataStore), applied/written through by
 * [rememberSceneDeckAppState].
 */
class SceneDeckAppState(
    initialThemeFamily: ThemeFamily = ThemeFamily.SCENEDECK,
    initialDarkMode: DarkMode = DarkMode.SYSTEM,
    initialDynamicColor: Boolean = false,
    initialMotionLevel: MotionLevel = MotionLevel.FULL,
    initialHaptics: Boolean = true,
    initialKeepScreenOn: Boolean = false,
) {
    var themeFamily by mutableStateOf(initialThemeFamily)
    var darkMode by mutableStateOf(initialDarkMode)
    var dynamicColor by mutableStateOf(initialDynamicColor)
    var motionLevel by mutableStateOf(initialMotionLevel)
    var haptics by mutableStateOf(initialHaptics)
    var keepScreenOn by mutableStateOf(initialKeepScreenOn)

    /** Resolves the effective dark flag from the override and the system setting. */
    fun isDarkTheme(systemInDarkTheme: Boolean): Boolean = when (darkMode) {
        DarkMode.SYSTEM -> systemInDarkTheme
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    /** Serializes the current values to a saveable map (also used by [Saver]). */
    fun toSaveableMap(): Map<String, Any> = mapOf(
        KEY_THEME_FAMILY to themeFamily.name,
        KEY_DARK_MODE to darkMode.name,
        KEY_DYNAMIC_COLOR to dynamicColor,
        KEY_MOTION_LEVEL to motionLevel.name,
        KEY_HAPTICS to haptics,
        KEY_KEEP_SCREEN_ON to keepScreenOn,
    )

    /** Applies a persisted settings snapshot (repository → state direction). */
    internal fun applySnapshot(settings: UserSettings) {
        themeFamily = enumValueOrDefault(settings.themeFamily, ThemeFamily.SCENEDECK)
        darkMode = settings.darkMode
        dynamicColor = settings.dynamicColor
        motionLevel = enumValueOrDefault(settings.motionLevel, MotionLevel.FULL)
        haptics = settings.haptics
        keepScreenOn = settings.keepScreenOn
    }

    companion object {
        private const val KEY_THEME_FAMILY = "themeFamily"
        private const val KEY_DARK_MODE = "darkMode"
        private const val KEY_DYNAMIC_COLOR = "dynamicColor"
        private const val KEY_MOTION_LEVEL = "motionLevel"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_KEEP_SCREEN_ON = "keepScreenOn"

        /** Restores a state from a map produced by [SceneDeckAppState.toSaveableMap]. */
        fun fromSaveableMap(map: Map<String, Any?>): SceneDeckAppState = SceneDeckAppState(
            initialThemeFamily = enumValueOrDefault(
                map[KEY_THEME_FAMILY] as? String,
                ThemeFamily.SCENEDECK,
            ),
            initialDarkMode = enumValueOrDefault(map[KEY_DARK_MODE] as? String, DarkMode.SYSTEM),
            initialDynamicColor = map[KEY_DYNAMIC_COLOR] as? Boolean ?: false,
            initialMotionLevel = enumValueOrDefault(
                map[KEY_MOTION_LEVEL] as? String,
                MotionLevel.FULL,
            ),
            initialHaptics = map[KEY_HAPTICS] as? Boolean ?: true,
            initialKeepScreenOn = map[KEY_KEEP_SCREEN_ON] as? Boolean ?: false,
        )

        val Saver = mapSaver(
            save = { state -> state.toSaveableMap() },
            restore = { map -> fromSaveableMap(map) },
        )

        internal inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }
}

/**
 * Creates the app state and, when [settings] is provided, keeps it in sync with
 * persisted settings both ways: repository → state (external changes) and
 * state → repository (user toggles). [rememberSaveable] stays the instant
 * restoration layer so there is no settings-read flash on rotation.
 */
@Composable
fun rememberSceneDeckAppState(settings: SettingsRepository? = null): SceneDeckAppState {
    val state = rememberSaveable(saver = SceneDeckAppState.Saver) { SceneDeckAppState() }

    if (settings != null) {
        LaunchedEffect(settings) {
            settings.settings.collect { snapshot -> state.applySnapshot(snapshot) }
        }
        LaunchedEffect(settings) {
            snapshotFlow {
                UserSettings(
                    themeFamily = state.themeFamily.name,
                    darkMode = state.darkMode,
                    dynamicColor = state.dynamicColor,
                    motionLevel = state.motionLevel.name,
                    haptics = state.haptics,
                    keepScreenOn = state.keepScreenOn,
                )
            }
                .drop(1) // skip the saveable-restored initial value
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    settings.update {
                        it.copy(
                            themeFamily = snapshot.themeFamily,
                            darkMode = snapshot.darkMode,
                            dynamicColor = snapshot.dynamicColor,
                            motionLevel = snapshot.motionLevel,
                            haptics = snapshot.haptics,
                            keepScreenOn = snapshot.keepScreenOn,
                        )
                    }
                }
        }
    }
    return state
}
