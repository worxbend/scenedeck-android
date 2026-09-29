package com.scenedeck.android.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.action.ActionParameters
import com.scenedeck.android.core.model.ConnectionState

/** Glance preference keys + the scene-tap action parameter (one definition site). */
object SceneDeckWidgetKeys {
    const val MAX_SCENES = 6

    val sceneCount = intPreferencesKey("sceneCount")
    val programScene = stringPreferencesKey("programScene")
    val connectionLabel = stringPreferencesKey("connectionLabel")

    fun sceneName(slot: Int) = stringPreferencesKey("scene_$slot")

    val sceneNameParam = ActionParameters.Key<String>("sceneName")
}

/** Short status word for the widget header (mirrors the StatusStrip labels). */
fun connectionLabelFor(state: ConnectionState): String = when (state) {
    is ConnectionState.Ready -> "Live"
    is ConnectionState.Connecting, is ConnectionState.Identifying -> "Connecting"
    is ConnectionState.Reconnecting -> "Retry ${state.attempt}"
    is ConnectionState.Failed -> "Failed"
    ConnectionState.Disconnected -> "Offline"
}

/** Snapshot written into each widget's Glance preferences by the updater. */
data class WidgetSnapshot(
    val sceneNames: List<String> = emptyList(),
    val programScene: String? = null,
    val connectionLabel: String = "Offline",
) {
    /** Mutates [prefs] in place (Glance's updateAppWidgetState discards return values). */
    fun writeTo(prefs: MutablePreferences) {
        prefs[SceneDeckWidgetKeys.sceneCount] = sceneNames.size
        sceneNames.forEachIndexed { slot, name ->
            prefs[SceneDeckWidgetKeys.sceneName(slot)] = name
        }
        // Clear stale slots from a previous, larger snapshot.
        for (slot in sceneNames.size until SceneDeckWidgetKeys.MAX_SCENES) {
            prefs.remove(SceneDeckWidgetKeys.sceneName(slot))
        }
        val program = programScene
        if (program != null) {
            prefs[SceneDeckWidgetKeys.programScene] = program
        } else {
            prefs.remove(SceneDeckWidgetKeys.programScene)
        }
        prefs[SceneDeckWidgetKeys.connectionLabel] = connectionLabel
    }

    companion object {
        fun readFrom(prefs: Preferences): WidgetSnapshot {
            val count = prefs[SceneDeckWidgetKeys.sceneCount] ?: 0
            return WidgetSnapshot(
                sceneNames = (0 until count)
                    .mapNotNull { prefs[SceneDeckWidgetKeys.sceneName(it)] }
                    .take(SceneDeckWidgetKeys.MAX_SCENES),
                programScene = prefs[SceneDeckWidgetKeys.programScene],
                connectionLabel = prefs[SceneDeckWidgetKeys.connectionLabel] ?: "Offline",
            )
        }
    }
}
