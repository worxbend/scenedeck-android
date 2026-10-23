package com.scenedeck.android.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUpdateTest {
    @Test
    fun connectedEmptyDeckClearsCachedSceneActionsAndProgram() {
        val prefs = mutablePreferencesOf()
        WidgetSnapshot(listOf("Removed"), "Removed", "Connected").writeTo(prefs)

        WidgetUpdate(WidgetSnapshot(connectionLabel = "Connected"), ready = true).writeTo(prefs)

        assertEquals(emptyList<String>(), WidgetSnapshot.readFrom(prefs).sceneNames)
        assertNull(prefs[SceneDeckWidgetKeys.sceneName(0)])
        assertNull(prefs[SceneDeckWidgetKeys.programScene])
        assertEquals("Connected", prefs[SceneDeckWidgetKeys.connectionLabel])
    }

    @Test
    fun unreadyDeckPreservesColdStartActionsAndUpdatesHeader() {
        val prefs = mutablePreferencesOf()
        WidgetSnapshot(listOf("Main"), "Main", "Connected").writeTo(prefs)

        WidgetUpdate(WidgetSnapshot(connectionLabel = "Retry 2"), ready = false).writeTo(prefs)

        assertEquals(listOf("Main"), WidgetSnapshot.readFrom(prefs).sceneNames)
        assertEquals("Main", prefs[SceneDeckWidgetKeys.programScene])
        assertEquals("Retry 2", prefs[SceneDeckWidgetKeys.connectionLabel])
    }

    @Test
    fun unrelatedMetadataAndStudioChangesDoNotScheduleWidgetWork() = runTest {
        val states = MutableSharedFlow<DeckState>()
        states.widgetUpdates().test {
            val deck = DeckState(connectionState = ready, scenes = listOf(scene("Main")))
            states.emit(deck)
            assertEquals(listOf("Main"), awaitItem().snapshot.sceneNames)

            states.emit(deck.copy(scenes = listOf(scene("Main").copy(iconName = "camera"))))
            states.emit(deck.copy(studioMode = true, previewScene = "Other"))
            expectNoEvents()

            states.emit(deck.copy(currentProgramScene = "Main"))
            assertEquals("Main", awaitItem().snapshot.programScene)
            states.emit(deck.copy(connectionState = ConnectionState.Disconnected))
            assertEquals("Offline", awaitItem().snapshot.connectionLabel)
        }
    }

    @Test
    fun onlySixVisibleSceneNamesAffectWidgetProjection() = runTest {
        val states = MutableSharedFlow<DeckState>()
        states.widgetUpdates().test {
            val visible = (1..6).map { scene("Scene $it") }
            states.emit(DeckState(connectionState = ready, scenes = visible + scene("Hidden")))
            val update = awaitItem()
            assertTrue(update.ready)
            assertEquals(visible.map { it.name }, update.snapshot.sceneNames)

            states.emit(
                DeckState(connectionState = ready, scenes = visible + scene("Renamed hidden"))
            )
            expectNoEvents()
            states.emit(DeckState(connectionState = ready, scenes = visible.reversed()))
            assertEquals(visible.reversed().map { it.name }, awaitItem().snapshot.sceneNames)
        }
    }

    private fun scene(name: String) = SceneCardState(name, SceneRole.PRIMARY, null, null, 0, false)

    private companion object {
        val ready = ConnectionState.Ready(ObsVersionInfo("32", "5", 1, "test"))
    }
}
