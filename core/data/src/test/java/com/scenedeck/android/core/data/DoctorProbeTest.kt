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
}
