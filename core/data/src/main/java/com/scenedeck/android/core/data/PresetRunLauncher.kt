package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Deliberate invocations survive screen navigation, but never survive process interruption. */
@Singleton
class PresetRunLauncher
@Inject
constructor(
    private val runner: PresetRunner,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val claimed = AtomicBoolean(false)
    private val pending = MutableStateFlow(false)
    val busy = pending.asStateFlow()
    val state = runner.state

    fun start(plan: PresetPlan, acknowledgedOutputs: Set<Int>): Boolean {
        if (!scope.isActive || !claimed.compareAndSet(false, true)) return false
        pending.value = true
        val job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                runner.run(plan, acknowledgedOutputs)
            }
        job.invokeOnCompletion {
            pending.value = false
            claimed.set(false)
        }
        return !job.isCancelled
    }

    fun cancel(runId: String) = runner.cancel(runId)
}
