package com.scenedeck.android.core.obs.internal

import com.rejeq.ktobs.AuthError
import com.rejeq.ktobs.EventOpCode
import com.rejeq.ktobs.ObsAuthException
import com.rejeq.ktobs.ObsCloseReason
import com.rejeq.ktobs.ObsEventSubs
import com.rejeq.ktobs.ObsSession
import com.rejeq.ktobs.ktor.ObsSessionBuilder
import com.rejeq.ktobs.ktor.runDefaultReceiver
import com.rejeq.ktobs.request.general.getVersion
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsNotConnectedException
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException

private const val BASE_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5
private const val CLOSE_REASON_TIMEOUT_MS = 1_000L

/** Exponential backoff: 1 s, 2 s, 4 s, 8 s, 16 s, then capped at 30 s. */
internal fun exponentialBackoffMillis(attempt: Int): Long {
    require(attempt >= 1) { "attempt must be >= 1, was $attempt" }
    val shift = (attempt - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
    return minOf(BASE_BACKOFF_MS shl shift, MAX_BACKOFF_MS)
}

private class SessionLostException(
    cause: Throwable?,
    val closeReason: ObsCloseReason?,
) : Exception("OBS WebSocket session ended unexpectedly", cause)

private data class SessionEnd(
    val failure: Throwable?,
    val closeReason: ObsCloseReason?,
)

/**
 * Owns the session state machine for [KtobsObsClient]: connect/disconnect, the Hello/Identify
 * handshake via ktobs, the reconnect loop with exponential backoff, and terminal-failure mapping
 * onto [connectionState].
 */
@Suppress("TooGenericExceptionCaught") // socket failures surface as typed ConnectionError
internal class ObsSessionRunner(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val requestSemaphore: Semaphore,
    private val dispatchEvent: (ObsSession, EventOpCode) -> Unit,
) {

    /** Event subscription bitmask applied to each new session. */
    var eventSubs: ObsEventSubs = ObsEventSubs.All + ObsEventSubs.InputVolumeMeters

    /** Backoff policy (attempt → delay); injectable for tests. */
    var backoffMillis: (attempt: Int) -> Long = ::exponentialBackoffMillis

    /** Timeout for the handshake and the initial GetVersion round-trip. */
    var connectTimeoutMs: Long = 10_000

    @Volatile
    var session: ObsSession? = null
        private set

    @Volatile private var disconnectRequested = false
    private val sessionMutex = Mutex()
    private var sessionJob: Job? = null
    private var closeSignal: CompletableDeferred<Unit>? = null
    private var connectOutcome: CompletableDeferred<Unit>? = null

    suspend fun connect(host: String, port: Int, password: String?) {
        val outcome = CompletableDeferred<Unit>()
        sessionMutex.withLock {
            disconnectLocked()
            disconnectRequested = false
            connectOutcome = outcome
            sessionJob =
                scope
                    .launch { sessionLoop(host, port, password) }
                    .also { job ->
                        job.invokeOnCompletion { cause ->
                            if (cause != null) {
                                connectionState.value = ConnectionState.Disconnected
                                outcome.completeExceptionally(cause)
                            }
                        }
                    }
        }
        // Suspends until the first attempt reaches Ready or Failed.
        try {
            outcome.await()
        } catch (e: CancellationException) {
            // Cancelling a pending connect must not leave an orphan OBS session.
            withContext(NonCancellable) {
                sessionMutex.withLock {
                    if (connectOutcome === outcome) disconnectLocked()
                }
            }
            throw e
        }
    }

    suspend fun disconnect() {
        sessionMutex.withLock { disconnectLocked() }
    }

    private suspend fun disconnectLocked() {
        disconnectRequested = true
        // A disconnect supersedes an in-flight connect: fail its waiter instead of completing
        // it as if the handshake had succeeded (state is Disconnected, not Ready).
        connectOutcome?.completeExceptionally(ObsNotConnectedException())
        closeSignal?.complete(Unit)
        val job = sessionJob
        sessionJob = null
        job?.cancelAndJoin()
        session = null
        connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun sessionLoop(host: String, port: Int, password: String?) {
        val progress = AttemptProgress()
        connectionState.value = ConnectionState.Connecting
        while (currentCoroutineContext().isActive) {
            if (attemptSession(host, port, password, progress)) return
            progress.attempt++
            connectionState.value = ConnectionState.Reconnecting(progress.attempt)
            delay(backoffMillis(progress.attempt))
        }
    }

    /** Tracks reconnect attempts and whether a session was ever fully established. */
    private class AttemptProgress {
        var attempt = 0
        var connected = false
    }

    /**
     * Runs one session attempt; returns true when the loop must stop (explicit disconnect or
     * terminal failure), false when a retry is due.
     */
    private suspend fun attemptSession(
        host: String,
        port: Int,
        password: String?,
        progress: AttemptProgress,
    ): Boolean =
        try {
            runSession(host, port, password) {
                progress.connected = true
                progress.attempt = 0
                connectOutcome?.complete(Unit)
            }
            // Normal return: disconnect() was requested.
            true
        } catch (e: TimeoutCancellationException) {
            // Timeout is a retryable socket failure, not caller cancellation.
            recordSessionFailure(e, progress.connected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordSessionFailure(e, progress.connected)
        }

    /** Initial and authentication failures are terminal; established socket loss retries. */
    private fun recordSessionFailure(failure: Exception, connected: Boolean): Boolean {
        if (connected && failure !is ObsAuthException) return false
        connectionState.value = ConnectionState.Failed(failure.toConnectionError())
        connectOutcome?.complete(Unit)
        return true
    }

    private fun newSessionBuilder(
        host: String,
        port: Int,
        password: String?,
        socketEnded: CompletableDeferred<SessionEnd>,
    ): ObsSessionBuilder =
        ObsSessionBuilder(httpClient).apply {
            this.host = host
            this.port = port
            this.password = password
            this.eventSubs = this@ObsSessionRunner.eventSubs
            this.onEvent = { event -> dispatchEvent(this, event) }
            this.receiver = { s ->
                var failure: Throwable? = null
                var closeReason: ObsCloseReason? = null
                try {
                    runDefaultReceiver(s)
                } catch (e: ClosedReceiveChannelException) {
                    failure = e
                    closeReason =
                        withTimeoutOrNull(CLOSE_REASON_TIMEOUT_MS) { s.ws.getCloseReason() }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Exception) {
                    failure = t
                    throw t
                } finally {
                    socketEnded.complete(SessionEnd(failure, closeReason))
                }
            }
        }

    /**
     * Opens the WebSocket, performs Hello/Identify, then suspends until the socket ends. Returns
     * normally only when [disconnect] was requested; throws on unexpected loss. [onReady] fires
     * once the session is fully up.
     */
    private suspend fun runSession(
        host: String,
        port: Int,
        password: String?,
        onReady: () -> Unit,
    ): Unit = coroutineScope {
        connectionState.value = ConnectionState.Identifying

        val sessionDeferred = CompletableDeferred<ObsSession>()
        val socketEnded = CompletableDeferred<SessionEnd>()
        val closeSignal = CompletableDeferred<Unit>()
        this@ObsSessionRunner.closeSignal = closeSignal
        val builder = newSessionBuilder(host, port, password, socketEnded)
        val connectJob = launchConnectJob(builder, sessionDeferred, socketEnded, closeSignal)

        try {
            awaitReadySession(sessionDeferred, onReady)
            val end = socketEnded.await()
            if (!disconnectRequested) {
                throw SessionLostException(end.failure, end.closeReason)
            }
        } finally {
            session = null
            this@ObsSessionRunner.closeSignal = null
            connectJob.cancel()
        }
    }

    private fun CoroutineScope.launchConnectJob(
        builder: ObsSessionBuilder,
        sessionDeferred: CompletableDeferred<ObsSession>,
        socketEnded: CompletableDeferred<SessionEnd>,
        closeSignal: CompletableDeferred<Unit>,
    ): Job = launch {
        try {
            builder.connect {
                sessionDeferred.complete(this)
                closeSignal.await()
            }
            socketEnded.complete(SessionEnd(failure = null, closeReason = null))
        } catch (e: CancellationException) {
            sessionDeferred.completeExceptionally(e)
            socketEnded.complete(SessionEnd(failure = null, closeReason = null))
            throw e
        } catch (t: Exception) {
            sessionDeferred.completeExceptionally(t)
            socketEnded.complete(SessionEnd(failure = t, closeReason = null))
        }
    }

    /** Awaits the handshake, proves the request lane and publishes [ConnectionState.Ready]. */
    private suspend fun awaitReadySession(
        sessionDeferred: CompletableDeferred<ObsSession>,
        onReady: () -> Unit,
    ) {
        val s = withTimeout(connectTimeoutMs) { sessionDeferred.await() }
        session = s
        // Prove the request lane and collect session info in one round-trip.
        val version =
            withTimeout(connectTimeoutMs) {
                requestSemaphore.withPermit { s.getVersion().toDomain() }
            }
        connectionState.value = ConnectionState.Ready(version)
        onReady()
    }
}

private fun ObsAuthException.toConnectionError(): ConnectionError =
    when (val k = kind) {
        is AuthError.InvalidRpc ->
            ConnectionError.Protocol("Unsupported OBS RPC version ${k.endpointVersion}")
        is AuthError.Unexpected -> ConnectionError.Auth(k.reason?.message)
        else -> ConnectionError.Auth(message)
    }

private fun Throwable.toConnectionError(): ConnectionError =
    when (this) {
        is ObsAuthException -> toConnectionError()
        is SessionLostException ->
            closeReason?.let { ConnectionError.Closed(it.code.toInt(), it.message) }
                ?: ConnectionError.Unreachable(cause ?: this)
        is SerializationException -> ConnectionError.Protocol(message ?: "Protocol decode error")
        is TimeoutCancellationException -> ConnectionError.Unreachable(this)
        else -> ConnectionError.Unreachable(this)
    }
