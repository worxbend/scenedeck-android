package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After

internal abstract class ShortcutExecutionTestFixture : ShortcutTestFixture() {
    protected val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    protected val client = ExecutingShortcutClient()
    protected val settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
    protected val clock = MonotonicClock { System.nanoTime() / 1_000_000 }

    @After
    fun stopExecution(): Unit = runBlocking { background.coroutineContext.job.cancelAndJoin() }

    protected fun controls(): Controls {
        val mixer = MixerRepository(client, settings, context, background, clock)
        val active = ActiveConnection().apply { confirm(begin(origin.key.profileId)) }
        val stats = StatsRepository(client, background, clock)
        val recording = RecordingToolsRepository(client, stats, active, clock, background)
        val source = SharedShortcutExecutionSource(client, repository, settings, mixer, recording)
        return Controls(mixer, recording, source, ShortcutRunner(source, clock))
    }

    protected suspend fun saved(action: ShortcutAction): ShortcutBinding =
        repository.save(
            origin,
            null,
            draft().copy(action = action),
        )

    protected data class Controls(
        val mixer: MixerRepository,
        val recording: RecordingToolsRepository,
        val source: ShortcutExecutionSource,
        val runner: ShortcutRunner,
    )
}

internal class ExecutingShortcutClient : FakeObsClient() {
    val calls = mutableListOf<String>()
    var program = "Other"
    var muted = false
    var muteFailure: Throwable? = null
    var afterProgramRead: suspend () -> Unit = {}
    var beforeMuteComplete: suspend (Boolean) -> Unit = {}
    val toolEntered = CompletableDeferred<Unit>()
    private val toolDone = CompletableDeferred<Unit>()

    init {
        setReady(
            capabilities =
                ObsCapabilities(splitRecording = true, controls = ObsControl.entries.toSet())
        )
        sceneListSnapshot =
            SceneListSnapshot(
                program,
                listOf(SceneSummary(" Camera ", 1), SceneSummary("Other", 0)),
            )
        previewScene = " Camera "
    }

    override suspend fun getCurrentProgramScene(): String {
        val value = program
        afterProgramRead()
        return value
    }

    override suspend fun setCurrentProgramScene(sceneName: String) {
        calls += "program:$sceneName"
        program = sceneName
    }

    override suspend fun setCurrentPreviewScene(sceneName: String) {
        calls += "preview:$sceneName"
        previewScene = sceneName
    }

    override suspend fun triggerStudioModeTransition() {
        calls += "take:$previewScene"
        val old = program
        program = previewScene
        previewScene = old
        emit(ObsEvent.CurrentPreviewSceneChanged(old))
    }

    override suspend fun getSpecialInputs() = SpecialInputs(mic1 = "Mic")

    override suspend fun getSceneItemList(sceneName: String) =
        emptyList<com.scenedeck.android.core.model.SceneItemInfo>()

    override suspend fun getInputVolume(inputName: String) = 1.0

    override suspend fun getInputMute(inputName: String): Boolean {
        muteFailure?.let { throw it }
        if (inputName !in setOf("Mic", "Off program"))
            throw ObsRequestFailedException("GetInputMute", "ResourceNotFound", "private")
        return muted
    }

    override suspend fun setInputMute(inputName: String, muted: Boolean) {
        calls += "mute:$inputName:$muted"
        beforeMuteComplete(muted)
        this.muted = muted
    }

    override suspend fun startStream() {
        calls += "stream:true"
        streamStatusResponse = streamStatusResponse.copy(active = true)
    }

    override suspend fun stopStream() {
        calls += "stream:false"
        streamStatusResponse = streamStatusResponse.copy(active = false)
    }

    override suspend fun startRecord() {
        calls += "record:true"
        recordStatusResponse = recordStatusResponse.copy(active = true)
    }

    override suspend fun stopRecord() {
        calls += "record:false"
        recordStatusResponse = recordStatusResponse.copy(active = false)
    }

    override suspend fun splitRecordFile() {
        toolEntered.complete(Unit)
        toolDone.await()
    }
}
