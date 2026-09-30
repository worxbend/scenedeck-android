package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneItemInfo
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
                    if (inputName == "Broken") error("missing input")
                    return 1.0
                }
            }
        assertEquals(
            mapOf("Main" to listOf("Broken")),
            probeBrokenAudioInputs(client, listOf("Main")),
        )
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
