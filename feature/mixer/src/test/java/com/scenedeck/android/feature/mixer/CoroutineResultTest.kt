package com.scenedeck.android.feature.mixer

import com.scenedeck.android.core.common.coroutineResult
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutineResultTest {
    @Test
    fun preservesSuccessfulValue() {
        assertEquals(42, coroutineResult { 42 }.getOrThrow())
    }

    @Test
    fun capturesRecoverableFailure() {
        val failure = IllegalStateException("request failed")
        assertSame(failure, coroutineResult<Unit> { throw failure }.exceptionOrNull())
    }

    @Test
    fun cancellationPropagates() {
        val cancellation = CancellationException("screen dismissed")
        var propagated = false
        try {
            coroutineResult<Unit> { throw cancellation }
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test(expected = AssertionError::class)
    fun fatalErrorsPropagate() {
        coroutineResult<Unit> { throw AssertionError("invariant violated") }
    }
}
