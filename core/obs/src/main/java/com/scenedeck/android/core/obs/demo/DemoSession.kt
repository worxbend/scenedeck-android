package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsNotConnectedException
import com.scenedeck.android.core.obs.ObsRequestOrigin
import com.scenedeck.android.core.obs.ObsWorkspaceChangedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DemoSession(private val scope: CoroutineScope, private val clock: () -> Long) {
    private val lock = Any()
    private val current = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state = current.asStateFlow()
    private val rawEvents =
        MutableSharedFlow<ObsEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val rawMeters =
        MutableSharedFlow<List<VolumeMeterReading>>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val stampedEvents =
        MutableSharedFlow<ObsSessionEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val stampedMeters =
        MutableSharedFlow<ObsSessionMeters>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = rawEvents.asSharedFlow()
    val meters = rawMeters.asSharedFlow()
    val contextEvents = stampedEvents.asSharedFlow()
    val contextMeters = stampedMeters.asSharedFlow()
    private var generation = 0L
    private var studio: DemoState? = null
    private var meterJob: Job? = null

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { synchronized(lock) { stop() } }
    }

    suspend fun connect(allowed: Boolean) {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        synchronized(lock) {
            caller.ensureActive()
            scope.coroutineContext.ensureActive()
            stop()
            if (allowed) {
                val id = ++generation
                studio = DemoState(clock) { event -> publish(id, event) }
                current.value = ConnectionState.Ready(DemoObsClient.VERSION, sessionId = id)
                meterJob =
                    scope
                        .launch(start = CoroutineStart.LAZY) {
                            combine(rawMeters.subscriptionCount, stampedMeters.subscriptionCount) {
                                    raw,
                                    stamped ->
                                    raw + stamped > 0
                                }
                                .distinctUntilChanged()
                                .collectLatest { listening ->
                                    if (listening)
                                        while (isActive) {
                                            sample(id)
                                            delay(100)
                                        }
                                }
                        }
                        .also { it.start() }
            } else {
                current.value =
                    ConnectionState.Failed(
                        ConnectionError.Protocol("Real connections are unavailable in demo")
                    )
            }
        }
    }

    suspend fun disconnect() {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        synchronized(lock) {
            caller.ensureActive()
            stop()
        }
    }

    /** Local-only teardown: exit invalidates queued work before returning to Android. */
    fun invalidate() = synchronized(lock) { stop() }

    suspend fun <T> request(block: DemoState.() -> T): T {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val origin = ready().token
        val expected = caller[ObsRequestOrigin]?.token
        if (expected != null && expected != origin) throw ObsWorkspaceChangedException()
        return synchronized(lock) {
            caller.ensureActive()
            scope.coroutineContext.ensureActive()
            if (origin != ready().token) throw ObsWorkspaceChangedException()
            val result = block(requireNotNull(studio))
            caller.ensureActive()
            result
        }
    }

    private fun ready() =
        current.value as? ConnectionState.Ready ?: throw ObsNotConnectedException()

    private fun stop() {
        meterJob?.cancel()
        meterJob = null
        studio = null
        current.value = ConnectionState.Disconnected
    }

    private fun publish(id: Long, event: ObsEvent) {
        val ready =
            (current.value as? ConnectionState.Ready)?.takeIf { it.sessionId == id } ?: return
        stampedEvents.tryEmit(ObsSessionEvent(ready.token, event))
        rawEvents.tryEmit(event)
    }

    private fun sample(id: Long) =
        synchronized(lock) {
            val ready = (current.value as? ConnectionState.Ready)?.takeIf { it.sessionId == id }
            val model = studio
            if (ready != null && model != null) {
                val playing = model.media.status(DemoCatalog.CLIP).state == MediaStateKind.PLAYING
                val readings = model.audio.meters(model.elapsed(), playing)
                stampedMeters.tryEmit(ObsSessionMeters(ready.token, readings))
                rawMeters.tryEmit(readings)
            }
        }
}

internal class DemoState(private val clock: () -> Long, emit: (ObsEvent) -> Unit) {
    private val born = clock()
    val studio = DemoStudio(emit)
    val audio = DemoAudio(emit)
    val outputs = DemoOutputs(clock, emit)
    val media = DemoMedia(clock, emit)

    fun elapsed() = (clock() - born).coerceAtLeast(0)
}
