package com.scenedeck.android.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class PresetTestFixture {
    private val androidContext = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName
        get() = "${javaClass.simpleName}.db"

    protected val context = MutableWorkspaceContext()
    protected val origin = TEST_WORKSPACE
    protected lateinit var database: SceneDeckDatabase
    protected lateinit var repository: PresetRepository
    protected var duringWrite: (() -> Unit)? = null
    protected val steps =
        listOf(
            PresetStep.Scene(" Camera ", PresetSceneDestination.PREVIEW),
            PresetStep.Mute("Mic/Aux", true),
            PresetStep.Volume("Music", 0.25),
            PresetStep.Media("Clip", PresetMediaState.PLAYING),
            PresetStep.Output(PresetOutput.RECORDING, true),
            PresetStep.Scene(" Camera ", PresetSceneDestination.PROGRAM),
            PresetStep.Mute("Mic/Aux", false),
        )

    @Before
    fun setupPresets() {
        androidContext.deleteDatabase(databaseName)
        reopen()
    }

    @After
    fun closePresets() {
        database.close()
        androidContext.deleteDatabase(databaseName)
    }

    protected fun reopen() {
        database =
            Room.databaseBuilder(androidContext, SceneDeckDatabase::class.java, databaseName)
                .setQueryCallback(
                    { sql, _ ->
                        if (sql.startsWith("INSERT") && sql.contains("action_presets")) {
                            val callback = duringWrite
                            duringWrite = null
                            callback?.invoke()
                        }
                    },
                    Executor { it.run() },
                )
                .build()
        repository = PresetRepository(database, context)
    }

    protected fun draft(name: String = "Interview") = PresetDraft(name = name, steps = steps)
}
