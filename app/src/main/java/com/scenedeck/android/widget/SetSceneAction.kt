package com.scenedeck.android.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.scenedeck.android.background.SceneSwitcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Widget scene tap → SetCurrentProgramScene. Extracts the scene name and delegates
 * to [SceneSwitcher], which connects to the last-used profile first when the
 * session is down — this also covers the cold-process case (the system starts the
 * app process to deliver this callback).
 */
class SetSceneAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val sceneName = parameters[SceneDeckWidgetKeys.sceneNameParam] ?: return
        val switcher = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .sceneSwitcher()
        switcher.switchTo(sceneName)
        // Recompose immediately; the DeckState push follows via the updater.
        SceneDeckWidget().update(context, glanceId)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun sceneSwitcher(): SceneSwitcher

    fun widgetUpdater(): SceneDeckWidgetUpdater
}
