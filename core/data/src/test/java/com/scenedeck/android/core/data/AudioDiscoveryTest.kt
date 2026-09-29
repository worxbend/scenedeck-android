package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioDiscoveryTest {

    private lateinit var client: FakeAudioObsClient
    private lateinit var discovery: AudioDiscovery

    @Before
    fun setUp() {
        client = FakeAudioObsClient()
        discovery = AudioDiscovery(client)
    }

    @Test
    fun specialInputsComeFirst(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = null, allowList = emptySet())
        assertEquals(listOf("Desktop Audio", "Mic/Aux"), inputs.map { it.name })
        assertTrue(inputs.all { it.scope == MixerScope.GLOBAL })
    }

    @Test
    fun activeSceneInputsFollowGlobals(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = emptySet())
        assertEquals(
            listOf("Desktop Audio", "Mic/Aux", "Test Tone 440", "Grouped Tone"),
            inputs.map { it.name },
        )
        val direct = inputs.single { it.name == "Test Tone 440" }
        assertEquals(MixerScope.SCENE, direct.scope)
        assertEquals(null, direct.scopePath)
    }

    @Test
    fun recursesIntoEnabledNestedScenesWithPath(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = "Starting Soon", allowList = emptySet())
        val nested = inputs.single { it.name == "Nested Tone" }
        assertEquals(MixerScope.NESTED, nested.scope)
        assertEquals("Starting Soon › Nested Audio", nested.scopePath)
    }

    @Test
    fun prunesDisabledNestedScenesAndItems(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = emptySet())
        assertTrue(inputs.none { it.name == "Disabled Tone" })
        assertTrue(inputs.none { it.name == "Hidden Tone" })
    }

    @Test
    fun dedupesByInputName(): Unit = runBlocking {
        // "Test Tone 440" appears both as a direct item and inside the group.
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = emptySet())
        assertEquals(1, inputs.count { it.name == "Test Tone 440" })
    }

    @Test
    fun groupItemsGetGroupScope(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = emptySet())
        val grouped = inputs.single { it.name == "Grouped Tone" }
        assertEquals(MixerScope.GROUP, grouped.scope)
        assertEquals("Cam 1 › Music Group", grouped.scopePath)
    }

    @Test
    fun skipsInputsWithoutVolumeOrMuteState(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = emptySet())
        assertTrue(inputs.none { it.name == "Broken Source" })
    }

    @Test
    fun allowListFiltersEverything(): Unit = runBlocking {
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = setOf("Mic/Aux"))
        assertEquals(listOf("Mic/Aux"), inputs.map { it.name })
    }

    @Test
    fun sceneListFailureYieldsGlobalsOnly(): Unit = runBlocking {
        client.failSceneItems = true
        val inputs = discovery.discover(activeScene = "Cam 1", allowList = emptySet())
        assertEquals(listOf("Desktop Audio", "Mic/Aux"), inputs.map { it.name })
    }

    /** Rig-shaped fake covering only what [AudioDiscovery] calls. */
    @Suppress("TooManyFunctions")
    private class FakeAudioObsClient : FakeObsClient() {
        var failSceneItems = false

        override suspend fun getSpecialInputs(): SpecialInputs =
            SpecialInputs(desktop1 = "Desktop Audio", mic1 = "Mic/Aux")

        override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> {
            if (failSceneItems) error("boom")
            return when (sceneName) {
                "Cam 1" -> listOf(
                    input(1, "Test Tone 440", enabled = true),
                    input(2, "Disabled Tone", enabled = false),
                    input(3, "Broken Source", enabled = true),
                    group(4, "Music Group", enabled = true),
                )

                "Music Group" -> listOf(
                    input(1, "Grouped Tone", enabled = true),
                    input(2, "Test Tone 440", enabled = true), // duplicate for dedupe
                )

                "Starting Soon" -> listOf(scene(1, "Nested Audio", enabled = true))
                "Nested Audio" -> listOf(
                    input(1, "Nested Tone", enabled = true),
                    scene(2, "Disabled Nested", enabled = false),
                )

                else -> emptyList()
            }
        }

        override suspend fun getInputMute(inputName: String): Boolean = when (inputName) {
            "Broken Source" -> error("no mute state")
            else -> false
        }

        override suspend fun getInputVolume(inputName: String): Double = when (inputName) {
            "Broken Source" -> error("no volume state")
            "Test Tone 440" -> 0.7
            else -> 1.0
        }

        private fun input(id: Int, name: String, enabled: Boolean) = SceneItemInfo(
            id = id,
            index = id,
            sourceName = name,
            enabled = enabled,
            isGroup = false,
            inputKind = "ffmpeg_source",
        )

        private fun group(id: Int, name: String, enabled: Boolean) = SceneItemInfo(
            id = id,
            index = id,
            sourceName = name,
            enabled = enabled,
            isGroup = true,
            inputKind = null,
        )

        private fun scene(id: Int, name: String, enabled: Boolean) = SceneItemInfo(
            id = id,
            index = id,
            sourceName = name,
            enabled = enabled,
            isGroup = false,
            inputKind = null,
        )
    }
}
