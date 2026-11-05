package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One shared media reader. Focused off-program channels join the existing polling lane. */
internal class MixerMediaController(
    private val client: ObsClient,
    private val workspaces: WorkspaceContext,
    private val clock: MonotonicClock,
    scope: CoroutineScope,
    sources: Flow<MediaSources>,
) {
    private val store: MediaPlaybackStore =
        MediaPlaybackStore(client, workspaces, clock, ::invalidateTargets)
    private var sequence = 0L
    private val watches = MutableStateFlow<Map<Long, Pair<WorkspaceSession, String>>>(emptyMap())
    private val current = store.current
    val state = current.asStateFlow()
    private val targets = store.targets
    private val seeks: MediaSeekController = MediaSeekController(client, clock, store, ::refresh)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect(store::onEvent)
        }
        scope.launch {
            combine(workspaces.state, client.connectionState, sources, watches) {
                    workspace,
                    connection,
                    source,
                    focused ->
                    val origin =
                        (workspace as? WorkspaceState.Ready)?.session?.takeIf(store::isCurrent)
                    val names =
                        source.names.takeIf { source.workspace == origin }.orEmpty() +
                            focused.values.filter { it.first == origin }.map { it.second }
                    Triple(
                        origin,
                        names,
                        (connection as? ConnectionState.Ready)
                            ?.sessionInfo
                            ?.capabilities
                            ?.mediaSeek == true,
                    )
                }
                .collect { (origin, names, supported) -> store.sync(origin, names, supported) }
        }
        scope.launch {
            combine(targets, current.subscriptionCount.map { it > 0 }.distinctUntilChanged()) {
                    names,
                    subscribed ->
                    names.takeIf { subscribed }.orEmpty()
                }
                .distinctUntilChanged()
                .collectLatest { names ->
                    store.clearSamples()
                    if (names.isNotEmpty()) {
                        while (isActive) {
                            names.forEach { refresh(it) }
                            delay(MEDIA_POLL_MS)
                        }
                    }
                }
        }
        scope.launch {
            current.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { subscribed ->
                    if (subscribed)
                        while (isActive) {
                            store.clearSamples(expiredOnly = true)
                            delay(1_000)
                        }
                }
        }
    }

    fun watch(origin: WorkspaceSession, inputName: String): AutoCloseable {
        val id =
            synchronized(store.lock) {
                if (!store.isCurrent(origin)) return AutoCloseable {}
                val id = ++sequence
                watches.value = watches.value + (id to (origin to inputName))
                id
            }
        return AutoCloseable { synchronized(store.lock) { watches.value = watches.value - id } }
    }

    suspend fun seek(
        target: MediaTarget,
        cursorMs: Long,
        expectedDurationMs: Long? = null,
    ): Boolean = seeks.perform(target, cursorMs, expectedDurationMs)

    fun isSeeking(origin: WorkspaceSession, inputName: String): Boolean =
        synchronized(store.lock) {
            current.value.takeIf { it.workspace == origin }?.readings?.get(inputName)?.seeking ==
                true
        }

    suspend fun refresh(origin: WorkspaceSession, inputName: String) {
        val target =
            synchronized(store.lock) {
                current.value.readings[inputName]?.target?.takeIf { it.workspace == origin }
            }
        target?.let { refresh(it) }
    }

    private fun invalidateTargets(targets: Set<MediaTarget>): Unit = seeks.invalidate(targets)

    private suspend fun refresh(target: MediaTarget): MediaPlayback? {
        val ticket = store.beginRead(target) ?: return null
        val result = requestResult {
            client.withSession(target.workspace.token) {
                if (store.reading(target) == null) throw WorkspaceChangedException()
                getMediaInputStatus(target.inputName)
            }
        }
        return store.accept(target, ticket, result)
    }
}
