package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.obs.ObsRequestFailedException
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DoctorProbeTest {
    @Test(expected = IOException::class)
    fun sceneReadTransportFailureAbortsScan(): Unit = runBlocking {
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> =
                    throw IOException("socket lost")
            }
        probeBrokenAudioInputs(client, listOf("Scene"))
    }

    @Test
    fun obsVolumeRejectionStillReportsBrokenInput(): Unit = runBlocking {
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String) =
                    listOf(SceneItemInfo(1, 0, "Mic", true, false, "input"))

                override suspend fun getInputVolume(inputName: String): Double =
                    throw ObsRequestFailedException(
                        "GetInputVolume",
                        "ResourceNotFound",
                        "No audio state",
                    )
            }
        assertEquals(
            mapOf("Scene" to listOf("Mic")),
            probeBrokenAudioInputs(client, listOf("Scene")),
        )
    }

    @Test
    fun videoOnlyInputsAreNotCountedAsBroken(): Unit = runBlocking {
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String) =
                    listOf(
                        SceneItemInfo(1, 0, "Camera BG", true, false, "color_source_v3"),
                        SceneItemInfo(2, 1, "Title", true, false, "text_ft2_source_v2"),
                    )

                override suspend fun getInputVolume(inputName: String): Double =
                    throw ObsRequestFailedException(
                        "GetInputVolume",
                        "InvalidResourceState",
                        "The specified input does not support audio.",
                    )
            }
        assertEquals(
            emptyMap<String, List<String>>(),
            probeBrokenAudioInputs(client, listOf("Cam")),
        )
    }

    @Test
    fun sceneRejectedByObsIsSkippedNotFatal(): Unit = runBlocking {
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> =
                    if (sceneName == "Gone") {
                        throw ObsRequestFailedException(
                            "GetSceneItemList",
                            "ResourceNotFound",
                            null,
                        )
                    } else {
                        listOf(SceneItemInfo(1, 0, "Mic", true, false, "input"))
                    }

                override suspend fun getInputVolume(inputName: String): Double =
                    throw ObsRequestFailedException("GetInputVolume", "ResourceNotFound", null)
            }
        assertEquals(
            mapOf("Live" to listOf("Mic")),
            probeBrokenAudioInputs(client, listOf("Gone", "Live")),
        )
    }
}
