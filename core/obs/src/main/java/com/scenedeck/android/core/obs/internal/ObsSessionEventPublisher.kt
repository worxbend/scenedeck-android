package com.scenedeck.android.core.obs.internal

import com.rejeq.ktobs.EventOpCode
import com.rejeq.ktobs.ObsSession
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsNotConnectedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** All methods run on the serial protocol lane, including initial Ready publication. */
internal class ObsSessionEventPublisher(
    private val connection: MutableStateFlow<ConnectionState>,
    private val emitEvent: (ObsSessionEvent) -> Unit,
    private val emitMeters: (ObsSessionMeters) -> Unit,
) {
    private val dispatcher = ObsEventDispatcher(::publishEvent, ::publishMeters)
    private var sessionId = 0L
    private var revision = 0L
    private val changing = MutableStateFlow(false)

    fun dispatch(session: ObsSession, event: EventOpCode, id: Long) =
        dispatcher.dispatch(session, event, id)

    fun beginSession(id: Long) {
        sessionId = id
        revision = 0
        changing.value = false
    }

    suspend fun awaitStable(id: Long) {
        changing.first { !it }
        if (sessionId != id) throw ObsNotConnectedException()
    }

    fun publishReady(id: Long, version: ObsVersionInfo) {
        if (sessionId != id) throw ObsNotConnectedException()
        connection.value = ConnectionState.Ready(version, id, revision, changing.value)
    }

    fun publishEvent(sessionId: Long, event: ObsEvent) {
        if (this.sessionId != sessionId) return
        when (event) {
            is ObsEvent.CurrentSceneCollectionChanging -> advance(changing = true)
            is ObsEvent.CurrentSceneCollectionChanged -> advance(changing = false)
            else -> Unit
        }
        // Collection events can arrive during the initial GetVersion. Remember their identity
        // for publishReady even though ordinary events still wait for the initial full refresh.
        val ready =
            (connection.value as? ConnectionState.Ready)?.takeIf { it.sessionId == sessionId }
                ?: return
        val next = ready.copy(collectionRevision = revision, collectionChanging = changing.value)
        // A concurrent disconnect must not be overwritten by a receiver publishing its last event.
        if (connection.compareAndSet(ready, next)) emitEvent(ObsSessionEvent(next.token, event))
    }

    private fun advance(changing: Boolean) {
        revision++
        this.changing.value = changing
    }

    fun publishMeters(sessionId: Long, readings: List<VolumeMeterReading>) {
        val ready = connection.value as? ConnectionState.Ready ?: return
        if (ready.sessionId == sessionId && !ready.collectionChanging) {
            emitMeters(ObsSessionMeters(ready.token, readings))
        }
    }
}
