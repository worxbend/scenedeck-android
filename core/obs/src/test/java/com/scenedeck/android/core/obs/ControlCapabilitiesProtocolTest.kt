package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsControl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ControlCapabilitiesProtocolTest : ObsClientTestBase() {
    @Test
    fun studioSceneSupportDoesNotImplyTakeWithoutItsAdvertisedWrite(): Unit = runBlocking {
        val requests =
            listOf(
                "GetSceneList",
                "GetCurrentProgramScene",
                "SetCurrentProgramScene",
                "GetStudioModeEnabled",
                "GetCurrentPreviewScene",
                "SetCurrentPreviewScene",
            )
        FakeObsServer().use { server ->
            server.start()
            server.responseData = { type, fixture ->
                if (type == "GetVersion")
                    fixture!!.replace(
                        "[\"GetVersion\",\"GetSceneList\"]",
                        Json.encodeToString(requests),
                    )
                else fixture
            }
            server.enqueueSession()
            val client = newClient()
            client.connect("127.0.0.1", server.port)
            val ready = client.connectionState.value as ConnectionState.Ready
            assertEquals(
                setOf(
                    ObsControl.PROGRAM_SCENE,
                    ObsControl.PREVIEW_SCENE,
                    ObsControl.FOLLOW_STUDIO_SCENE,
                ),
                ready.sessionInfo.capabilities.controls,
            )
            client.disconnect()
        }
    }

    @Test
    fun incompleteReadWritePairsAreNotAdvertisedAsVerifiableControls(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.responseData = { type, fixture ->
                if (type == "GetVersion")
                    fixture!!.replace(
                        "[\"GetVersion\",\"GetSceneList\"]",
                        Json.encodeToString(
                            listOf(
                                "GetInputMute",
                                "GetStreamStatus",
                                "StartStream",
                                "GetMediaInputStatus",
                                "TriggerMediaInputAction",
                                "FutureRequest",
                            )
                        ),
                    )
                else fixture
            }
            server.enqueueSession()
            val client = newClient()
            client.connect("127.0.0.1", server.port)
            val ready = client.connectionState.value as ConnectionState.Ready
            assertEquals(setOf(ObsControl.MEDIA_STATE), ready.sessionInfo.capabilities.controls)
            client.disconnect()
        }
    }

    @Test
    fun completeControlsAreRetainedFromTheHandshakeWithoutVersionGuessing(): Unit = runBlocking {
        val requests =
            listOf(
                "GetSceneList",
                "GetCurrentProgramScene",
                "SetCurrentProgramScene",
                "GetStudioModeEnabled",
                "GetCurrentPreviewScene",
                "SetCurrentPreviewScene",
                "TriggerStudioModeTransition",
                "GetInputMute",
                "SetInputMute",
                "GetInputVolume",
                "SetInputVolume",
                "GetMediaInputStatus",
                "TriggerMediaInputAction",
                "GetStreamStatus",
                "StartStream",
                "StopStream",
                "GetRecordStatus",
                "StartRecord",
                "StopRecord",
            )
        FakeObsServer().use { server ->
            server.start()
            server.responseData = { type, fixture ->
                if (type == "GetVersion")
                    fixture!!.replace(
                        "[\"GetVersion\",\"GetSceneList\"]",
                        Json.encodeToString(requests),
                    )
                else fixture
            }
            server.enqueueSession()
            val client = newClient()
            client.connect("127.0.0.1", server.port)
            val ready = client.connectionState.value as ConnectionState.Ready
            assertEquals(ObsControl.entries.toSet(), ready.sessionInfo.capabilities.controls)
            client.disconnect()
        }
    }
}
