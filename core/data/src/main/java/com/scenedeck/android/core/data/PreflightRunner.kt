package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

enum class PreflightStatus {
    PASS,
    FAIL,
    UNKNOWN,
}

data class PreflightFinding(
    val id: String,
    val title: String,
    val status: PreflightStatus,
    val detail: String,
)

data class PreflightReport(
    val origin: WorkspaceSession,
    val completedAtMs: Long,
    val findings: List<PreflightFinding>,
)

/** Read-only, finite checks. A failed read is unknown and never erases another check's result. */
class PreflightRunner
@Inject
constructor(private val client: ObsClient, private val workspaces: WorkspaceContext) {
    internal var nowMs: () -> Long = System::currentTimeMillis

    suspend fun run(
        origin: WorkspaceSession,
        config: PreflightConfig,
        checkSignal: Boolean,
    ): PreflightReport {
        val setup = config.checked()
        workspaces.requireCurrent(origin)
        val findings =
            client.withSession(origin.token) {
                coroutineScope {
                    val scenes = async { sceneChecks(setup) }
                    val disk = async { diskCheck(setup) }
                    val microphone = async { microphoneCheck(setup) }
                    val signal = async {
                        if (checkSignal) observePreflightSignal(client, origin, setup.microphone)
                        else null
                    }
                    listOf(collectionCheck(origin, setup)) +
                        scenes.await() +
                        disk.await() +
                        microphone.await() +
                        listOfNotNull(signal.await()) +
                        manualChecks(setup)
                }
            }
        workspaces.requireCurrent(origin)
        return PreflightReport(origin, nowMs(), findings)
    }

    private suspend fun sceneChecks(config: PreflightConfig): List<PreflightFinding> {
        if (config.requiredScenes.isEmpty())
            return listOf(
                PreflightFinding(
                    "scenes",
                    "Required scenes",
                    PreflightStatus.UNKNOWN,
                    "Choose required scenes in Setup.",
                )
            )
        val names = read { client.getSceneList().scenes.map { it.name }.toSet() }
        return config.requiredScenes.map { name ->
            PreflightFinding(
                "scene:$name",
                name,
                when {
                    names == null -> PreflightStatus.UNKNOWN
                    name in names -> PreflightStatus.PASS
                    else -> PreflightStatus.FAIL
                },
                when {
                    names == null -> "Couldn't read the OBS scene list."
                    name in names -> "Present in OBS."
                    else -> "Missing from this OBS collection."
                },
            )
        }
    }

    private suspend fun diskCheck(config: PreflightConfig): PreflightFinding {
        val mb = read {
            client.getStats().availableDiskSpaceMb
        }
            ?.takeIf { it.isFinite() && it >= 0 }
        return PreflightFinding(
            "disk",
            "Recording disk space",
            when {
                mb == null -> PreflightStatus.UNKNOWN
                mb < config.minimumDiskMb -> PreflightStatus.FAIL
                else -> PreflightStatus.PASS
            },
            if (mb == null) "Free space is unavailable."
            else "${mb.toLong()} MB available · minimum ${config.minimumDiskMb} MB.",
        )
    }

    private suspend fun microphoneCheck(config: PreflightConfig): PreflightFinding {
        val name = config.microphone
        val muted = name?.let { read { client.getInputMute(it) } }
        return PreflightFinding(
            "microphone",
            "Microphone control",
            when (muted) {
                null -> PreflightStatus.UNKNOWN
                true -> PreflightStatus.FAIL
                false -> PreflightStatus.PASS
            },
            when {
                name == null -> "Choose a microphone in Setup."
                muted == null ->
                    "Couldn't read mute state for $name. Check the input name and audio support."
                muted -> "$name is muted."
                else -> "$name is unmuted. This does not prove an audible signal."
            },
        )
    }

    private suspend fun <T> read(action: suspend () -> T): T? =
        withTimeoutOrNull(8_000) { requestResult { action() }.getOrNull() }
}

private fun collectionCheck(origin: WorkspaceSession, config: PreflightConfig) =
    PreflightFinding(
        "collection",
        "Scene collection",
        if (origin.key.collectionName == config.expectedCollection) PreflightStatus.PASS
        else PreflightStatus.FAIL,
        "Connected: ${origin.key.collectionName} · expected: ${config.expectedCollection}.",
    )

private fun manualChecks(config: PreflightConfig) =
    config.manualChecks.map {
        PreflightFinding(
            "manual:${it.id}",
            it.label,
            if (it.complete) PreflightStatus.PASS else PreflightStatus.UNKNOWN,
            if (it.complete) "Manually confirmed." else "Not yet confirmed in the checklist.",
        )
    }
