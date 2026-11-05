package com.scenedeck.android.feature.inventory

import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.RegistryExportCodec
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.singleWorkspaceRegistry
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RegistryTransferFailureTest {
    @Test
    fun failedImportKeepsPreviewForRetry(): Unit = runBlocking {
        val dao = ImportDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transfer =
            RegistryTransfer(
                singleWorkspaceRegistry(dao),
                scope,
                MutableSharedFlow(),
            )
        try {
            transfer.stage(payload(), TEST_WORKSPACE)
            dao.failure = IllegalStateException("storage unavailable")
            transfer.confirmImport()
            withTimeout(5_000) { dao.started.await() }
            withTimeout(5_000) { transfer.importing.first { !it } }
            assertEquals(1, dao.writes)
            assertNotNull(transfer.importPreview.value)
            dao.failure = null
            transfer.confirmImport()
            withTimeout(5_000) { transfer.importPreview.first { it == null } }
            assertNull(transfer.importPreview.value)
            assertEquals(2, dao.writes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateConfirmDoesNotStartAnotherImport(): Unit = runBlocking {
        val dao = ImportDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transfer =
            RegistryTransfer(
                singleWorkspaceRegistry(dao),
                scope,
                MutableSharedFlow(),
            )
        try {
            transfer.stage(payload(), TEST_WORKSPACE)
            dao.release = CompletableDeferred()
            transfer.confirmImport()
            transfer.confirmImport()
            withTimeout(5_000) { dao.started.await() }
            assertEquals(1, dao.writes)
            dao.release!!.complete(Unit)
            withTimeout(5_000) { transfer.importing.first { !it } }
            assertEquals(1, dao.writes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun importPreviewCannotFollowAReconnectedSession(): Unit = runBlocking {
        val dao = ImportDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val registry = singleWorkspaceRegistry(dao)
        val transfer = RegistryTransfer(registry, scope, MutableSharedFlow())
        try {
            transfer.stage(payload(), TEST_WORKSPACE)
            assertNotNull(transfer.importPreview.value)
            (registry.workspaces as MutableWorkspaceContext).select(
                TEST_WORKSPACE.copy(targetGeneration = 2)
            )
            transfer.confirmImport()
            withTimeout(5_000) { transfer.importPreview.first { it == null } }
            assertEquals(0, dao.writes)
        } finally {
            scope.cancel()
        }
    }

    private fun payload() =
        RegistryExportCodec.encode(
            listOf(SceneRegistryEntry("Camera", SceneRole.PRIMARY, null, null, 0))
        )

    private class ImportDao : SceneRegistryDao {
        var failure: Exception? = null
        var release: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        var writes = 0

        override fun observeAll(): Flow<List<SceneRegistryEntity>> = flowOf(emptyList())

        override suspend fun byName(sceneName: String): SceneRegistryEntity? = null

        override suspend fun upsert(entity: SceneRegistryEntity) = write()

        override suspend fun upsertAll(entities: List<SceneRegistryEntity>) = write()

        private suspend fun write() {
            writes++
            started.complete(Unit)
            release?.await()
            failure?.let { throw it }
        }

        override suspend fun deleteStale(validSceneNames: List<String>) = Unit

        override suspend fun delete(sceneName: String) = Unit
    }
}
