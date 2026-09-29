package com.scenedeck.android.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.scenedeck.android.core.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotTest {

    private fun WidgetSnapshot.writtenTo(prefs: Preferences): Preferences =
        prefs.toMutablePreferences().also { writeTo(it) }.toPreferences()

    @Test
    fun `write then read round-trips`() {
        val snapshot = WidgetSnapshot(
            sceneNames = listOf("Cam 1", "Screen"),
            programScene = "Cam 1",
            connectionLabel = "Live",
        )

        val restored = WidgetSnapshot.readFrom(snapshot.writtenTo(emptyPreferences()))

        assertEquals(snapshot, restored)
    }

    @Test
    fun `write clears stale slots from a larger previous snapshot`() {
        val big = WidgetSnapshot(
            sceneNames = listOf("A", "B", "C", "D"),
            programScene = "A",
        )
        val prefs = big.writtenTo(emptyPreferences())

        val small = WidgetSnapshot(sceneNames = listOf("A"), programScene = null)
        val restored = WidgetSnapshot.readFrom(small.writtenTo(prefs))

        assertEquals(listOf("A"), restored.sceneNames)
        assertNull(restored.programScene)
    }

    @Test
    fun `read on empty preferences yields offline empty snapshot`() {
        val restored = WidgetSnapshot.readFrom(emptyPreferences())

        assertEquals(WidgetSnapshot(), restored)
    }

    @Test
    fun `write mutates in place (Glance updateAppWidgetState discards return values)`() {
        val prefs = mutablePreferencesOf()
        WidgetSnapshot(sceneNames = listOf("Cam 1"), connectionLabel = "Live").writeTo(prefs)

        assertEquals(1, prefs[SceneDeckWidgetKeys.sceneCount])
        assertEquals("Cam 1", prefs[SceneDeckWidgetKeys.sceneName(0)])
        assertEquals("Live", prefs[SceneDeckWidgetKeys.connectionLabel])
    }

    @Test
    fun `connection labels mirror the status strip`() {
        assertEquals("Live", connectionLabelFor(ready))
        assertEquals("Connecting", connectionLabelFor(ConnectionState.Connecting))
        assertEquals("Retry 2", connectionLabelFor(ConnectionState.Reconnecting(2)))
        assertEquals("Offline", connectionLabelFor(ConnectionState.Disconnected))
    }

    private companion object {
        val ready = ConnectionState.Ready(
            com.scenedeck.android.core.model.ObsVersionInfo(
                obsVersion = "31.0.0",
                obsWebSocketVersion = "5.5.2",
                rpcVersion = 1,
            ),
        )
    }
}
