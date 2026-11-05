package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shared monitoring uses existing telemetry/meters. No additional OBS requests or polling. */
@Singleton
class LiveAlertsRepository
@Inject
constructor(
    private val client: ObsClient,
    private val stats: StatsRepository,
    private val active: ActiveConnection,
    private val preflight: PreflightRepository,
    private val clock: MonotonicClock,
    @ApplicationScope scope: CoroutineScope,
) {
    private val lock = Any()
    private val engine = LiveAlertEngine(preflight.workspaces::isCurrent)
    private var setup: PreflightSetupState? = null
    private val current = MutableStateFlow(engine.state)
    val state = current.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            requestResult {
                client.metersWithContext.collect { batch ->
                    synchronized(lock) { engine.receiveMeters(batch, clock.nowMs()) }
                }
            }
            // An ended/failed meter stream ages to unknown; never expose its exception text.
        }
        scope.launch { stats.telemetry.collect { refresh() } }
        scope.launch { client.connectionState.collect { refresh() } }
        scope.launch { active.target.collect { refresh() } }
        scope.launch {
            preflight.state.collect { value ->
                synchronized(lock) {
                    setup = value
                    refresh()
                }
            }
        }
        scope.launch {
            while (isActive) {
                refresh()
                delay(1_000)
            }
        }
    }

    private fun refresh() =
        synchronized(lock) {
            engine.update(
                client.connectionState.value,
                active.target.value,
                stats.telemetry.value,
                setup,
                clock.nowMs(),
            )
            current.value = engine.state
        }

    fun armSpeech(target: SpeechTarget): Boolean =
        synchronized(lock) {
            refresh()
            engine.armSpeech(target).also { current.value = engine.state }
        }

    fun stopSpeech() =
        synchronized(lock) {
            engine.stopSpeech()
            refresh()
        }

    fun dismiss(alert: LiveAlert) =
        synchronized(lock) {
            refresh()
            engine.dismiss(alert.kind, alert.occurrence, clock.nowMs())
            current.value = engine.state
        }

    fun setHaptics(enabled: Boolean) =
        synchronized(lock) {
            engine.setHaptics(enabled)
            current.value = engine.state
        }
}
