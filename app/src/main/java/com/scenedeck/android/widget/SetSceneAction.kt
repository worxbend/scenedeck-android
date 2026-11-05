package com.scenedeck.android.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.scenedeck.android.background.SceneSwitcher
import com.scenedeck.android.core.data.WorkspaceKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Widget scene tap → SetCurrentProgramScene. Extracts the scene name and delegates to
 * [SceneSwitcher], which connects to the widget’s recorded profile first when the session is down —
 * this also covers the cold-process case (the system starts the app process to deliver this
 * callback).
 */
class SetSceneAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val (sceneName, workspace) = parameters.sceneTarget() ?: return
        val switcher =
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                .sceneSwitcher()
        switcher.switchTo(sceneName, workspace)
        // Recompose immediately; the DeckState push follows via the updater.
        SceneDeckWidget().update(context, glanceId)
    }
}

private fun ActionParameters.sceneTarget(): Pair<String, WorkspaceKey>? {
    val scene = this[SceneDeckWidgetKeys.sceneNameParam]
    val profileId = this[SceneDeckWidgetKeys.profileIdParam]?.takeIf { it > 0 }
    val collection = this[SceneDeckWidgetKeys.collectionParam]
    return if (scene == null || profileId == null || collection.isNullOrBlank()) null
    else scene to WorkspaceKey(profileId, collection)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun sceneSwitcher(): SceneSwitcher

    fun widgetUpdater(): SceneDeckWidgetUpdater
}
