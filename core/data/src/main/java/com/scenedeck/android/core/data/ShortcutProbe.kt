package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.isInputWithoutAudio
import com.scenedeck.android.core.obs.isResourceNotFound
import com.scenedeck.android.core.obs.withSession

/** Fresh reads only. No stale UI values, target retargeting, controls or request-name strings. */
internal class ShortcutProbe(
    private val client: ObsClient,
    private val workspaces: WorkspaceContext,
) {
    suspend fun resolve(
        origin: WorkspaceSession,
        action: ShortcutAction,
        policy: ShortcutPolicy,
    ): ShortcutTarget {
        requireCurrent(origin)
        checkControl(action.control)
        val target =
            when (action) {
                is ShortcutAction.Scene ->
                    ShortcutTarget.Scene(action.name, read(origin) { getStudioModeEnabled() })
                ShortcutAction.TakePreview -> {
                    requireShortcutState(
                        read(origin) { getStudioModeEnabled() },
                        ShortcutIssue.STUDIO_OFF,
                    )
                    ShortcutTarget.Take(read(origin) { getCurrentPreviewScene() })
                }
                is ShortcutAction.Mute -> ShortcutTarget.Mute(action.inputName, action.muted)
                is ShortcutAction.Stream -> ShortcutTarget.Stream(action.active)
                is ShortcutAction.Recording -> ShortcutTarget.Recording(action.active)
            }
        checkPolicy(target, policy)
        return target
    }

    suspend fun matches(
        origin: WorkspaceSession,
        target: ShortcutTarget,
        policy: ShortcutPolicy,
    ): Boolean {
        requireCurrent(origin)
        checkControl(target.control)
        checkPolicy(target, policy)
        return when (target) {
            is ShortcutTarget.Scene -> sceneMatches(origin, target)
            is ShortcutTarget.Take -> takeMatches(origin, target)
            is ShortcutTarget.Mute ->
                read(origin) { getInputMute(target.inputName) } == target.muted
            is ShortcutTarget.Stream -> {
                val status = read(origin) { getStreamStatus() }
                requireShortcutState(
                    !status.reconnecting || !target.active,
                    ShortcutIssue.NOT_READY,
                )
                (status.active || status.reconnecting) == target.active
            }
            is ShortcutTarget.Recording -> {
                val status = read(origin) { getRecordStatus() }
                requireShortcutState(!status.paused || status.active, ShortcutIssue.UNAVAILABLE)
                status.active == target.active
            }
        }
    }

    suspend fun confirmed(plan: ShortcutPlan): Boolean =
        when (val target = plan.target) {
            // A normal Studio transition may exchange program and preview. Confirm the captured
            // target.
            is ShortcutTarget.Take ->
                read(plan.origin) { getCurrentProgramScene() } == target.previewName
            else -> matches(plan.origin, target, plan.policy)
        }

    fun requireCurrent(origin: WorkspaceSession) {
        val ready = client.connectionState.value as? ConnectionState.Ready
        requireShortcutState(
            workspaces.isCurrent(origin) &&
                ready?.token == origin.token &&
                !ready.collectionChanging,
            ShortcutIssue.WORKSPACE_CHANGED,
        )
    }

    private suspend fun sceneMatches(
        origin: WorkspaceSession,
        target: ShortcutTarget.Scene,
    ): Boolean {
        requireScene(origin, target.name)
        requireShortcutState(
            read(origin) { getStudioModeEnabled() } == target.preview,
            ShortcutIssue.TARGET_CHANGED,
        )
        val current =
            read(origin) {
                if (target.preview) getCurrentPreviewScene() else getCurrentProgramScene()
            }
        requireShortcutState(current.isNotBlank(), ShortcutIssue.UNAVAILABLE)
        return current == target.name
    }

    private suspend fun takeMatches(
        origin: WorkspaceSession,
        target: ShortcutTarget.Take,
    ): Boolean {
        requireShortcutState(read(origin) { getStudioModeEnabled() }, ShortcutIssue.STUDIO_OFF)
        requireScene(origin, target.previewName)
        requireShortcutState(
            read(origin) { getCurrentPreviewScene() } == target.previewName,
            ShortcutIssue.TARGET_CHANGED,
        )
        val program = read(origin) { getCurrentProgramScene() }
        requireShortcutState(program.isNotBlank(), ShortcutIssue.UNAVAILABLE)
        return program == target.previewName
    }

    private suspend fun requireScene(origin: WorkspaceSession, name: String) {
        requireShortcutState(name.isNotBlank() && name.length <= 1_024, ShortcutIssue.UNAVAILABLE)
        requireShortcutState(
            read(origin) { getSceneList().scenes.any { it.name == name } },
            ShortcutIssue.MISSING,
        )
    }

    private fun checkControl(control: ObsControl) {
        val available =
            (client.connectionState.value as? ConnectionState.Ready)
                ?.sessionInfo
                ?.capabilities
                ?.controls
                .orEmpty()
        requireShortcutState(control in available, ShortcutIssue.UNSUPPORTED)
    }

    private fun checkPolicy(target: ShortcutTarget, policy: ShortcutPolicy) {
        if (target is ShortcutTarget.Mute) {
            requireShortcutState(target.inputName !in policy.lockedInputs, ShortcutIssue.LOCKED)
            requireShortcutState(
                policy.allowedInputs.isEmpty() || target.inputName in policy.allowedInputs,
                ShortcutIssue.NOT_ALLOWED,
            )
        }
    }

    private suspend fun <T> read(origin: WorkspaceSession, action: suspend ObsClient.() -> T): T {
        requireCurrent(origin)
        val value = requestResult {
            client.withSession(origin.token) { action(client) }
        }
            .getOrElse {
                requireCurrent(origin)
                throw it.shortcutReadFailure()
            }
        requireCurrent(origin)
        return value
    }
}

internal fun requireShortcutState(value: Boolean, issue: ShortcutIssue) {
    if (!value) throw ShortcutStopped(issue)
}

private fun Throwable.shortcutReadFailure(): Throwable =
    when {
        this is WorkspaceChangedException -> ShortcutStopped(ShortcutIssue.WORKSPACE_CHANGED)
        this is ObsRequestFailedException && isResourceNotFound ->
            ShortcutStopped(ShortcutIssue.MISSING)
        this is ObsRequestFailedException && isInputWithoutAudio ->
            ShortcutStopped(ShortcutIssue.NOT_READY)
        else -> ShortcutStopped(ShortcutIssue.UNAVAILABLE)
    }

private val ShortcutAction.control: ObsControl
    get() =
        when (this) {
            is ShortcutAction.Scene -> ObsControl.FOLLOW_STUDIO_SCENE
            ShortcutAction.TakePreview -> ObsControl.STUDIO_TRANSITION
            is ShortcutAction.Mute -> ObsControl.INPUT_MUTE
            is ShortcutAction.Stream -> ObsControl.STREAM
            is ShortcutAction.Recording -> ObsControl.RECORDING
        }

private val ShortcutTarget.control: ObsControl
    get() =
        when (this) {
            is ShortcutTarget.Scene -> ObsControl.FOLLOW_STUDIO_SCENE
            is ShortcutTarget.Take -> ObsControl.STUDIO_TRANSITION
            is ShortcutTarget.Mute -> ObsControl.INPUT_MUTE
            is ShortcutTarget.Stream -> ObsControl.STREAM
            is ShortcutTarget.Recording -> ObsControl.RECORDING
        }
