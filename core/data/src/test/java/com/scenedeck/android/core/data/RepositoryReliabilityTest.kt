package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.obs.ObsNotConnectedException
import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryReliabilityTest {
    @Test
    fun doctorOnlyReportsFailedVolumeProbes() = runTest {
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String) =
                    listOf(
                        SceneItemInfo(1, 0, "Healthy", true, false, "audio"),
                        SceneItemInfo(2, 1, "Broken", true, false, "audio"),
                        SceneItemInfo(3, 2, "Disabled", false, false, "audio"),
                    )

                override suspend fun getInputVolume(inputName: String): Double {
                    if (inputName == "Broken") {
                        throw ObsRequestFailedException(
                            "GetInputVolume",
                            "ResourceNotFound",
                            "missing input",
                        )
                    }
                    return 1.0
                }
            }
        assertEquals(
            mapOf("Main" to listOf("Broken")),
            probeBrokenAudioInputs(client, listOf("Main")),
        )
    }

    @Test
    fun doctorAbortsOnTransportFailureInsteadOfReportingBroken() = runTest {
        val disconnect = ObsNotConnectedException()
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String) =
                    listOf(SceneItemInfo(1, 0, "Mic", true, false, "audio"))

                override suspend fun getInputVolume(inputName: String): Double = throw disconnect
            }
        try {
            probeBrokenAudioInputs(client, listOf("Main"))
            error("Expected the transport failure to abort the probe")
        } catch (actual: ObsNotConnectedException) {
            assertSame(disconnect, actual)
        }
    }

    @Test
    fun doctorProbePropagatesCancellation() = runTest {
        val cancelled = CancellationException("scan replaced")
        val client =
            object : FakeObsClient() {
                override suspend fun getSceneItemList(sceneName: String) =
                    listOf(SceneItemInfo(1, 0, "Mic", true, false, "audio"))

                override suspend fun getInputVolume(inputName: String): Double = throw cancelled
            }
        try {
            probeBrokenAudioInputs(client, listOf("Main"))
            error("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test
    fun cancelledDiscoveryDoesNotReturnPartialSuccess() = runTest {
        val cancelled = CancellationException("session replaced")
        val client =
            object : FakeObsClient() {
                override suspend fun getSpecialInputs() = throw cancelled
            }
        try {
            AudioDiscovery(client).discover(null, emptySet())
            error("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test
    fun requestFailuresRemainRecoverable() {
        assertTrue(requestResult { error("unavailable input") }.isFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyHistoryCapacity() {
        TelemetryHistory(0)
    }
}
