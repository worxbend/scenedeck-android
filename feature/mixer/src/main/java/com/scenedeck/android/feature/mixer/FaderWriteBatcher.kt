package com.scenedeck.android.feature.mixer

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces fader writes to OBS: trailing debounce per input, so a fader drag emits at most one
 * request per window; the final value is sent immediately on drag end.
 */
internal class FaderWriteBatcher<T>(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val delayMs: Long,
    private val send: suspend (T, Double) -> Unit,
) {
    private val jobs = mutableMapOf<T, Job>()

    fun preview(inputName: T, volumeMul: Double) {
        jobs[inputName]?.cancel()
        jobs[inputName] = scope.launch {
            delay(delayMs)
            send(inputName, volumeMul)
        }
    }

    fun commit(inputName: T, volumeMul: Double) {
        jobs[inputName]?.cancel()
        jobs.remove(inputName)
        scope.launch { send(inputName, volumeMul) }
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
