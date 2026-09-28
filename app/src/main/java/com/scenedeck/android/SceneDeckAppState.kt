package com.scenedeck.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.feature.settings.DarkMode

/**
 * Hoisted, session-scoped app state: theme family, dark-mode override, dynamic color
 * and motion level. Survives configuration changes via [rememberSaveable].
 *
 * TODO(M2): persist through :core:datastore (Proto DataStore) instead of process state.
 */
class SceneDeckAppState(
    initialThemeFamily: ThemeFamily = ThemeFamily.SCENEDECK,
    initialDarkMode: DarkMode = DarkMode.SYSTEM,
    initialDynamicColor: Boolean = false,
    initialMotionLevel: MotionLevel = MotionLevel.FULL,
) {
    var themeFamily by mutableStateOf(initialThemeFamily)
    var darkMode by mutableStateOf(initialDarkMode)
    var dynamicColor by mutableStateOf(initialDynamicColor)
    var motionLevel by mutableStateOf(initialMotionLevel)

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
    )

    companion object {
        private const val KEY_THEME_FAMILY = "themeFamily"
        private const val KEY_DARK_MODE = "darkMode"
        private const val KEY_DYNAMIC_COLOR = "dynamicColor"
        private const val KEY_MOTION_LEVEL = "motionLevel"

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
        )

        val Saver = mapSaver(
            save = { state -> state.toSaveableMap() },
            restore = { map -> fromSaveableMap(map) },
        )

        private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }
}

@Composable
fun rememberSceneDeckAppState(): SceneDeckAppState =
    rememberSaveable(saver = SceneDeckAppState.Saver) { SceneDeckAppState() }
