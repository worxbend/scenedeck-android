package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ChannelLevels
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.VolumeMeterReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreflightRunnerTest {
    private val context = MutableWorkspaceContext()
    private val origin = TEST_WORKSPACE
    private val config = PreflightConfig("Studio", listOf("Camera", "Missing"), "Mic")
    private val stats = ObsStats(0.0, 0.0, 2_048.0, 30.0, 1.0, 0, 0, 0, 0)

    @Test
    fun independentResultsKeepReadFailuresUnknownAndNeverLeakServerDetails() = runTest {
        val client =
            object : FakeObsClient(statsResponse = stats.copy(availableDiskSpaceMb = 1.0)) {
                    override suspend fun getSceneList(): SceneListSnapshot =
                        error("password=private-server-comment")

                    override suspend fun getInputMute(inputName: String) = false
                }
                .apply { setReady() }
        val runner = PreflightRunner(client, context).apply { nowMs = { 123L } }
        val report = runner.run(origin, config.copy(expectedCollection = "Other"), false)
        val rows = report.findings.associateBy { it.id }
        assertEquals(PreflightStatus.FAIL, rows.getValue("collection").status)
        assertEquals(PreflightStatus.UNKNOWN, rows.getValue("scene:Camera").status)
        assertEquals(PreflightStatus.FAIL, rows.getValue("disk").status)
        assertEquals(PreflightStatus.PASS, rows.getValue("microphone").status)
        assertEquals(123L, report.completedAtMs)
        assertFalse(report.toString().contains("private-server-comment"))
        assertTrue(client.sceneSwitchCalls.isEmpty())
    }

    @Test
    fun missingMutedAndUncheckedDifferFromUnavailableReadings() = runTest {
        val client =
            object :
                    FakeObsClient(
                        SceneListSnapshot("Camera", listOf(SceneSummary("Camera", 0))),
                        stats,
                    ) {
                    override suspend fun getInputMute(inputName: String) = true
                }
                .apply { setReady() }
        val runner = PreflightRunner(client, context)
        val rows =
            runner
                .run(
                    origin,
                    config.copy(manualChecks = listOf(ManualPreflightCheck("lights", "Lights"))),
                    false,
                )
                .findings
                .associateBy { it.id }
        assertEquals(PreflightStatus.PASS, rows.getValue("scene:Camera").status)
        assertEquals(PreflightStatus.FAIL, rows.getValue("scene:Missing").status)
        assertEquals(PreflightStatus.FAIL, rows.getValue("microphone").status)
        assertEquals(PreflightStatus.PASS, rows.getValue("disk").status)
        assertEquals(PreflightStatus.UNKNOWN, rows.getValue("manual:lights").status)
        client.statsResponse = stats.copy(availableDiskSpaceMb = Double.NaN)
        val unknown = runner.run(origin, PreflightConfig("Studio"), false).findings
        assertEquals(3, unknown.count { it.status == PreflightStatus.UNKNOWN })
    }

    @Test
    fun slowReadTimesOutIndependentlyAndCallerCancellationPropagates() = runTest {
        var cancellations = 0
        var calls = 0
        val client =
            object : FakeObsClient() {
                    override suspend fun getStats(): ObsStats {
                        calls++
                        try {
                            awaitCancellation()
                        } finally {
                            cancellations++
                        }
                    }

                    override suspend fun getInputMute(inputName: String) = false
                }
                .apply { setReady() }
        val runner = PreflightRunner(client, context)
        val report = async { runner.run(origin, config, false) }
        advanceTimeBy(8_001)
        assertEquals(
            PreflightStatus.UNKNOWN,
            report.await().findings.single { it.id == "disk" }.status,
        )
        assertEquals(1, calls)
        val canceled = async { runner.run(origin, config, false) }
        runCurrent()
        canceled.cancelAndJoin()
        assertTrue(canceled.isCancelled)
        assertEquals(2, cancellations)
    }

    @Test
    fun replacementWorkspaceRejectsCompletedOldRead() = runTest {
        val response = CompletableDeferred<ObsStats>()
        val client =
            object : FakeObsClient() {
                    override suspend fun getStats() = response.await()
                }
                .apply { setReady() }
        val result = async {
            runCatching { PreflightRunner(client, context).run(origin, config, false) }
        }
        runCurrent()
        context.select(origin.copy(key = WorkspaceKey(8, "Studio"), targetGeneration = 2))
        response.complete(stats)
        assertTrue(result.await().exceptionOrNull() is WorkspaceChangedException)
    }

    @Test
    fun signalUsesOnlyCurrentValidSamplesAndDistinguishesQuietFromMissing() = runTest {
        val batches = MutableSharedFlow<ObsSessionMeters>()
        val client =
            object : FakeObsClient() {
                override val metersWithContext = batches
            }
        suspend fun observe(peak: Float?, token: ObsSessionToken = origin.token): PreflightFinding {
            val result = async { observePreflightSignal(client, origin, "Mic") }
            runCurrent()
            if (peak != null)
                batches.emit(
                    ObsSessionMeters(
                        token,
                        listOf(VolumeMeterReading("Mic", listOf(ChannelLevels(peak, peak, peak)))),
                    )
                )
            advanceTimeBy(4_001)
            return result.await()
        }
        assertEquals(PreflightStatus.UNKNOWN, observe(null).status)
        assertEquals(PreflightStatus.UNKNOWN, observe(Float.NaN).status)
        assertEquals(PreflightStatus.UNKNOWN, observe(1f, ObsSessionToken(99, 1)).status)
        assertEquals(PreflightStatus.FAIL, observe(0f).status)
        assertEquals(PreflightStatus.PASS, observe(0.1f).status)
    }
}
