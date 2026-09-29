package com.scenedeck.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Push-only widget updates: collects DeckState (PRIMARY scenes + program) and writes
 * a [WidgetSnapshot] into every placed widget instance, then recomposes. No
 * WorkManager / periodic updates — the app process is alive whenever the session
 * matters (foreground or keep-alive service), and [start] runs again on cold starts
 * via the Application.
 */
@Singleton
class SceneDeckWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deck: ObsStateRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            deck.deckState.collect { state ->
                val snapshot = WidgetSnapshot(
                    sceneNames = state.scenes
                        .take(SceneDeckWidgetKeys.MAX_SCENES)
                        .map { it.name },
                    programScene = state.currentProgramScene,
                    connectionLabel = connectionLabelFor(state.connectionState),
                )
                push(snapshot)
            }
        }
    }

    /** Re-pushes the current snapshot (e.g. right after a widget is placed). */
    fun poke() {
        scope.launch {
            val state = deck.deckState.value
            push(
                WidgetSnapshot(
                    sceneNames = state.scenes
                        .take(SceneDeckWidgetKeys.MAX_SCENES)
                        .map { it.name },
                    programScene = state.currentProgramScene,
                    connectionLabel = connectionLabelFor(state.connectionState),
                ),
            )
        }
    }

    private suspend fun push(snapshot: WidgetSnapshot) {
        val glanceIds = GlanceAppWidgetManager(context)
            .getGlanceIds(SceneDeckWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                if (snapshot.sceneNames.isEmpty()) {
                    // Offline / pre-connect: keep the last known grid so a cold tap can
                    // still connect-then-act; only the header label goes stale-truthful.
                    prefs[SceneDeckWidgetKeys.connectionLabel] = snapshot.connectionLabel
                } else {
                    snapshot.writeTo(prefs)
                }
            }
            SceneDeckWidget().update(context, glanceId)
        }
    }
}
