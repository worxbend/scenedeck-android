package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneCollectionListSnapshot
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRepositoryTest {
    @Test
    fun pendingProfileCannotBorrowPreviousReadySession() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.connect(7, 1)
        runCurrent()
        val old = requireNotNull(fixture.repository.currentSession())
        val attempt = fixture.active.begin(8)

        // These checks run before combine/collectLatest gets another scheduler turn.
        assertNull(fixture.repository.currentSession())
        assertFalse(fixture.repository.isCurrent(old))
        runCurrent()
        assertEquals(WorkspaceState.Unavailable, fixture.repository.state.value)
        assertEquals(1, fixture.client.lookups)

        fixture.client.setReady(2)
        runCurrent()
        assertNull(fixture.repository.currentSession())
        fixture.active.confirm(attempt)
        runCurrent()
        assertEquals(WorkspaceKey(8, "Studio"), fixture.repository.currentSession()?.key)
    }

    @Test
    fun failedAndCancelledOldAttemptsCannotClearReplacement() = runTest {
        val fixture = Fixture(backgroundScope)
        val obsolete = fixture.active.begin(7)
        fixture.connect(8, 2)
        fixture.active.confirm(obsolete)
        fixture.active.end(obsolete)
        runCurrent()
        assertEquals(WorkspaceKey(8, "Studio"), fixture.repository.currentSession()?.key)
    }

    @Test
    fun collectionChangeInvalidatesImmediatelyAndResolvesNewNamespace() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.connect(7, 1)
        runCurrent()
        val old = requireNotNull(fixture.repository.currentSession())
        fixture.client.setReady(1, 1, collectionChanging = true)
        assertNull(fixture.repository.currentSession())
        runCurrent()
        assertEquals(1, fixture.client.lookups)
        assertEquals(WorkspaceState.Unavailable, fixture.repository.state.value)

        fixture.client.response = { collections("Show B") }
        fixture.client.setReady(1, 2)
        runCurrent()
        assertEquals(WorkspaceKey(7, "Show B"), fixture.repository.currentSession()?.key)
        assertFalse(fixture.repository.isCurrent(old))
        assertEquals(2, fixture.client.lookups)
    }

    @Test
    fun automaticReconnectKeepsProfileButRediscoversCollection() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.connect(7, 1)
        runCurrent()
        val old = requireNotNull(fixture.repository.currentSession())
        fixture.client.setDisconnected()
        assertNull(fixture.repository.currentSession())
        runCurrent()
        fixture.client.response = { collections("Other") }
        fixture.client.setReady(2)
        runCurrent()
        val replacement = requireNotNull(fixture.repository.currentSession())
        assertEquals(old.targetGeneration, replacement.targetGeneration)
        assertEquals(WorkspaceKey(7, "Other"), replacement.key)
        assertFalse(fixture.repository.isCurrent(old))
    }

    @Test
    fun failedLookupIsUnknownAndRetryIsCoalesced() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.client.response = { throw IOException("private server detail") }
        fixture.connect(7, 1)
        runCurrent()
        assertEquals(WorkspaceState.Failed, fixture.repository.state.value)
        assertNull(fixture.repository.currentSession())
        assertFalse(fixture.repository.state.value.toString().contains("private"))

        val response = CompletableDeferred<SceneCollectionListSnapshot>()
        fixture.client.response = { response.await() }
        fixture.repository.retry()
        fixture.repository.retry()
        runCurrent()
        fixture.repository.retry()
        runCurrent()
        assertEquals(2, fixture.client.lookups)
        assertEquals(WorkspaceState.Resolving, fixture.repository.state.value)
        response.complete(collections("Recovered"))
        runCurrent()
        assertEquals(WorkspaceKey(7, "Recovered"), fixture.repository.currentSession()?.key)
    }

    @Test
    fun blankCollectionIsNotAValidWorkspace() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.client.response = { collections("") }
        fixture.connect(7, 1)
        runCurrent()
        assertEquals(WorkspaceState.Failed, fixture.repository.state.value)
        assertNull(fixture.repository.currentSession())
    }

    @Test
    fun lateDiscoveryCannotActivateAnObsoleteProfile() = runTest {
        val fixture = Fixture(backgroundScope)
        val gate = CompletableDeferred<SceneCollectionListSnapshot>()
        fixture.client.response = { withContext(NonCancellable) { gate.await() } }
        fixture.connect(7, 1)
        runCurrent()
        fixture.client.response = { collections("Replacement") }
        fixture.connect(8, 2)
        runCurrent()
        assertNull(fixture.repository.currentSession())
        gate.complete(collections("Obsolete"))
        runCurrent()
        assertEquals(WorkspaceKey(8, "Replacement"), fixture.repository.currentSession()?.key)
        assertEquals(2, fixture.client.lookups)
    }

    @Test
    fun disconnectInvalidatesEvenBeforeClientClosesSocket() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.connect(7, 1)
        runCurrent()
        assertTrue(fixture.repository.currentSession() != null)
        fixture.active.begin(null)
        assertNull(fixture.repository.currentSession())
        runCurrent()
        assertEquals(WorkspaceState.Unavailable, fixture.repository.state.value)
    }

    private class Fixture(scope: CoroutineScope) {
        val active = ActiveConnection()
        val client = CollectionClient()
        val repository = WorkspaceRepository(client, active, scope)

        fun connect(profileId: Long, sessionId: Long) {
            val attempt = active.begin(profileId)
            client.setReady(sessionId)
            active.confirm(attempt)
        }
    }

    private class CollectionClient : FakeObsClient() {
        var lookups = 0
        var response: suspend () -> SceneCollectionListSnapshot = { collections("Studio") }

        override suspend fun getSceneCollectionList(): SceneCollectionListSnapshot {
            lookups++
            return response()
        }
    }

    companion object {
        private fun collections(name: String) = SceneCollectionListSnapshot(name, listOf(name))
    }
}
