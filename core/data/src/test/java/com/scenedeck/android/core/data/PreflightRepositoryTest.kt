package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.PreflightConfigEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreflightRepositoryTest {
    private val androidContext =
        ApplicationProvider.getApplicationContext<android.content.Context>()
    private val context = MutableWorkspaceContext()
    private lateinit var database: SceneDeckDatabase
    private lateinit var repository: PreflightRepository
    private val origin = TEST_WORKSPACE
    private var invalidateDuringWrite = false

    @Before
    fun open() {
        androidContext.deleteDatabase("preflight-test")
        reopen()
    }

    private fun reopen() {
        database =
            Room.databaseBuilder(androidContext, SceneDeckDatabase::class.java, "preflight-test")
                .setQueryCallback(
                    { sql, _ ->
                        if (
                            invalidateDuringWrite &&
                                sql.startsWith("INSERT") &&
                                sql.contains("preflight_config")
                        ) {
                            invalidateDuringWrite = false
                            context.unavailable()
                        }
                    },
                    java.util.concurrent.Executor { it.run() },
                )
                .build()
        repository = PreflightRepository(database, context)
    }

    @After
    fun close() {
        database.close()
        androidContext.deleteDatabase("preflight-test")
    }

    @Test
    fun setupAndManualCompletionSurviveReopenWithoutCrossingScope(): Unit = runBlocking {
        val initial = repository.snapshot(origin)
        val draft =
            initial.copy(
                requiredScenes = listOf("Camera", "Camera"),
                microphone = "Mic/Aux",
                minimumDiskMb = 4_096,
                manualChecks = listOf(ManualPreflightCheck("lights", " Lights ready ", true)),
            )
        repository.save(origin, initial, draft)
        val saved = repository.snapshot(origin)
        assertEquals(listOf("Camera"), saved.requiredScenes)
        assertEquals(
            ManualPreflightCheck("lights", "Lights ready", true),
            saved.manualChecks.single(),
        )
        database.close()
        reopen()
        assertEquals(saved, repository.snapshot(origin))
        listOf(WorkspaceKey(8, "Studio"), WorkspaceKey(7, "Travel")).forEach { key ->
            val other = origin.copy(key = key)
            context.select(other)
            assertEquals(PreflightConfig(key.collectionName), repository.snapshot(other))
            assertTrue(
                runCatching { repository.save(origin, saved, initial) }.exceptionOrNull()
                    is WorkspaceChangedException
            )
        }
        context.select(origin)
        assertEquals(saved, repository.snapshot(origin))
    }

    @Test
    fun staleEditsRejectAndChangedManualWordingClearsConfirmation(): Unit = runBlocking {
        val initial = repository.snapshot(origin)
        val first =
            initial.copy(
                manualChecks = listOf(ManualPreflightCheck("lights", "Lights ready", true))
            )
        repository.save(origin, initial, first)
        assertTrue(
            runCatching { repository.save(origin, initial, initial.copy(microphone = "Wrong")) }
                .exceptionOrNull() is PreflightEditException
        )
        repository.save(
            origin,
            first,
            first.copy(
                manualChecks = listOf(first.manualChecks.single().copy(label = "Sound ready"))
            ),
        )
        assertFalse(repository.snapshot(origin).manualChecks.single().complete)
    }

    @Test
    fun invalidAndCorruptSetupNeverBecomesDefaultSuccess(): Unit = runBlocking {
        val initial = repository.snapshot(origin)
        val invalid =
            listOf(
                initial.copy(minimumDiskMb = 0),
                initial.copy(expectedCollection = ""),
                initial.copy(requiredScenes = List(101) { "Camera$it" }),
                initial.copy(version = 2),
                initial.copy(
                    manualChecks =
                        listOf(ManualPreflightCheck("same", "A"), ManualPreflightCheck("same", "B"))
                ),
            )
        invalid.forEach {
            assertTrue(runCatching { repository.save(origin, initial, it) }.isFailure)
        }
        assertEquals(initial, repository.snapshot(origin))
        database
            .preflightConfigDao()
            .save(
                PreflightConfigEntity(
                    origin.key.profileId,
                    origin.key.collectionName,
                    "broken private details",
                )
            )
        assertEquals(
            PreflightSetupState.Failed(origin),
            repository.state.first { it is PreflightSetupState.Failed },
        )
    }

    @Test
    fun workspaceReplacementDuringWriteRollsBackTheConfiguration(): Unit = runBlocking {
        val initial = repository.snapshot(origin)
        invalidateDuringWrite = true
        val result = runCatching {
            repository.save(origin, initial, initial.copy(microphone = "Mic"))
        }
        assertTrue(result.exceptionOrNull() is WorkspaceChangedException)
        assertFalse(invalidateDuringWrite)
        context.select(origin)
        assertEquals(initial, repository.snapshot(origin))
    }
}
