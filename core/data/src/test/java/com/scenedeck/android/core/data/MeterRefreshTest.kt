package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.VolumeMeterReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterRefreshTest {

    /** Ten batches at OBS's 50 ms cadence, tagged by index in the input name. */
    private fun obsMeters(): Flow<List<VolumeMeterReading>> = flow {
        repeat(10) { index ->
            delay(50)
            emit(listOf(VolumeMeterReading("Mic $index", emptyList())))
        }
    }

    @Test
    fun smoothPassesEveryBatch() = runTest {
        assertEquals(10, obsMeters().withMeterRefresh(MeterRefresh.SMOOTH).toList().size)
    }

    @Test
    fun balancedKeepsLatestBatchPerHundredMs() = runTest {
        val names =
            obsMeters().withMeterRefresh(MeterRefresh.BALANCED).toList().map {
                it.single().inputName
            }
        // 500 ms of 50 ms batches sampled every 100 ms: roughly every second batch survives.
        assertTrue(names.size in 4..5)
        assertEquals(names.distinct(), names)
    }

    @Test
    fun saverDropsMoreThanBalanced() = runTest {
        val saver = obsMeters().withMeterRefresh(MeterRefresh.SAVER).toList()
        assertTrue(saver.size in 1..2)
    }

    @Test
    fun offEmitsNothing() = runTest {
        assertTrue(obsMeters().withMeterRefresh(MeterRefresh.OFF).toList().isEmpty())
    }

    @Test
    fun unknownNamesFallBackToSmooth() {
        assertEquals(MeterRefresh.SMOOTH, MeterRefresh.fromName("NOPE"))
        assertEquals(MeterRefresh.SAVER, MeterRefresh.fromName("SAVER"))
    }
}
