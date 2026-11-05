package com.scenedeck.android.demo

import androidx.room.withTransaction
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.database.ConnectionProfileEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.demo.DemoObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class DemoPhase {
    STARTING,
    READY,
    FAILED,
    CLOSED,
}

/** One retained Activity owner; an obsolete Activity cannot close a newer demo visit. */
@Singleton
class DemoRuntime
@Inject
constructor(
    private val process: AppProcess,
    private val database: SceneDeckDatabase,
    private val settings: SettingsRepository,
    private val session: ObsSessionHolder,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var owner: Any? = null
    private var command: Job? = null
    private var seeded = false
    private val phase = MutableStateFlow(DemoPhase.CLOSED)
    val state = phase.asStateFlow()

    fun open(visit: Any) =
        synchronized(lock) {
            check(process == AppProcess.DEMO) { "Demo requires its isolated process" }
            if (owner === visit && phase.value != DemoPhase.FAILED) return@synchronized
            val previous = command
            previous?.cancel()
            owner = visit
            phase.value = DemoPhase.STARTING
            // Invalidate commands before waiting for any old bootstrap work.
            session.disconnect()
            command =
                scope
                    .launch(start = CoroutineStart.LAZY) {
                        previous?.join()
                        start(visit)
                    }
                    .also { it.start() }
        }

    fun close(visit: Any) =
        synchronized(lock) {
            if (owner !== visit) return@synchronized
            owner = null
            command?.cancel()
            (session.client as? DemoObsClient)?.invalidate()
            phase.value = DemoPhase.CLOSED
            // ActiveConnection invalidation is synchronous; disconnect is serialized by its owner.
            session.disconnect()
        }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Storage/bootstrap failures are sanitized; cancellation propagates.
    private suspend fun start(visit: Any) {
        try {
            withTimeout(15_000) {
                ensureProfile()
                currentCoroutineContext().ensureActive()
                val ready =
                    session.connectAndAwait(PROFILE_ID) as? ConnectionState.Ready
                        ?: error("Demo did not become ready")
                if (!seeded) seed(ready)
                currentCoroutineContext().ensureActive()
                synchronized(lock) { if (owner === visit) phase.value = DemoPhase.READY }
            }
        } catch (cancelled: CancellationException) {
            // A timeout is also cancellation: leave a retryable state unless this visit was closed.
            failed(visit)
            throw cancelled
        } catch (_: Exception) {
            failed(visit)
        }
    }

    private fun failed(visit: Any) =
        synchronized(lock) {
            if (owner === visit) {
                phase.value = DemoPhase.FAILED
                (session.client as? DemoObsClient)?.invalidate()
                session.disconnect()
            }
        }

    private suspend fun ensureProfile() {
        database.withTransaction {
            if (database.connectionProfileDao().byId(PROFILE_ID) == null) {
                database
                    .connectionProfileDao()
                    .insert(
                        ConnectionProfileEntity(
                            id = PROFILE_ID,
                            name = "DEMO · Local studio",
                            host = DemoObsClient.HOST,
                            port = 4455,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
            }
        }
    }

    private suspend fun seed(ready: ConnectionState.Ready) {
        val client = session.client
        client.withSession(ready.token) {
            val collection = client.getSceneCollectionList().currentCollection
            val scenes = client.getSceneList().scenes
            database.withTransaction {
                database
                    .workspaceSceneDao()
                    .upsertAll(
                        scenes.mapIndexed { index, scene ->
                            SceneRegistryEntity(
                                sceneName = scene.name,
                                role = if (index < 3) "PRIMARY" else "SECONDARY",
                                sortOrder = index,
                                connectionProfileId = PROFILE_ID,
                                sceneCollectionName = collection,
                                localAlias = scene.name.removePrefix("Demo · "),
                            )
                        }
                    )
            }
            settings.update {
                it.copy(
                    onboardingCompleted = true,
                    confirmStartStream = true,
                    confirmStartRecord = true,
                )
            }
        }
        seeded = true
    }

    private companion object {
        const val PROFILE_ID = 1L
    }
}
