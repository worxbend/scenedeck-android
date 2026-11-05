package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@Singleton
class SharedShortcutExecutionSource
@Inject
constructor(
    private val client: ObsClient,
    private val shortcuts: ShortcutRepository,
    private val settings: SettingsRepository,
    private val mixer: MixerRepository,
    private val recording: RecordingToolsRepository,
) : ShortcutExecutionSource {
    private val probe = ShortcutProbe(client, shortcuts.workspaces)
    override val events = client.eventsWithContext

    override fun interruptions(
        origin: WorkspaceSession,
        binding: ShortcutBinding,
    ): Flow<ShortcutIssue> =
        merge(
                shortcuts.workspaces.state.map { contextIssue(origin) },
                client.connectionState.map { contextIssue(origin) },
                shortcuts.state.map { it.changed(origin, binding) },
                recording.state.map { if (busy(binding)) ShortcutIssue.BUSY else null },
            )
            .filterNotNull()

    override fun policyChanges(policy: ShortcutPolicy): Flow<ShortcutIssue> =
        settings.settings
            .map { if (it.shortcutPolicy() != policy) ShortcutIssue.POLICY_CHANGED else null }
            .filterNotNull()

    override suspend fun prepare(origin: WorkspaceSession, binding: ShortcutBinding): ShortcutPlan {
        binding.encodeShortcut()
        requireSaved(origin, binding)
        val policy = settings.settings.first().shortcutPolicy()
        val target = probe.resolve(origin, binding.action, policy)
        val plan =
            ShortcutPlan(origin, binding, policy, target, probe.matches(origin, target, policy))
        validate(plan)
        return plan
    }

    override suspend fun validate(plan: ShortcutPlan) {
        requireShortcutState(plan.matchesBinding(), ShortcutIssue.BINDING_CHANGED)
        requireSaved(plan.origin, plan.binding)
        requireShortcutState(
            settings.settings.first().shortcutPolicy() == plan.policy,
            ShortcutIssue.POLICY_CHANGED,
        )
        requireShortcutState(!busy(plan.binding), ShortcutIssue.BUSY)
        probe.requireCurrent(plan.origin)
    }

    override suspend fun write(plan: ShortcutPlan, onDispatch: () -> Unit): Boolean {
        validate(plan)
        val matches = probe.matches(plan.origin, plan.target, plan.policy)
        validate(plan)
        if (matches) return false
        currentCoroutineContext().ensureActive()
        onDispatch()
        client.withSession(plan.origin.token) { apply(plan) }
        probe.requireCurrent(plan.origin)
        return true
    }

    override suspend fun confirmed(plan: ShortcutPlan) = probe.confirmed(plan)

    private suspend fun requireSaved(origin: WorkspaceSession, binding: ShortcutBinding) {
        probe.requireCurrent(origin)
        requireShortcutState(
            binding.enabled &&
                binding.workspace == origin.key &&
                shortcuts.snapshot(origin).find { it.id == binding.id } == binding,
            ShortcutIssue.BINDING_CHANGED,
        )
        requireShortcutState(!busy(binding), ShortcutIssue.BUSY)
        probe.requireCurrent(origin)
    }

    private suspend fun apply(plan: ShortcutPlan) {
        when (val target = plan.target) {
            is ShortcutTarget.Scene ->
                if (target.preview) client.setCurrentPreviewScene(target.name)
                else client.setCurrentProgramScene(target.name)
            is ShortcutTarget.Take -> client.triggerStudioModeTransition()
            is ShortcutTarget.Mute ->
                mixer.setInputMute(plan.origin, target.inputName, target.muted)
            is ShortcutTarget.Stream ->
                if (target.active) client.startStream() else client.stopStream()
            is ShortcutTarget.Recording ->
                if (target.active) client.startRecord() else client.stopRecord()
        }
    }

    private fun busy(binding: ShortcutBinding) =
        binding.action is ShortcutAction.Recording && recording.state.value.busy

    private fun contextIssue(origin: WorkspaceSession): ShortcutIssue? {
        val ready = client.connectionState.value as? ConnectionState.Ready
        return if (
            !shortcuts.workspaces.isCurrent(origin) ||
                ready?.token != origin.token ||
                ready.collectionChanging
        )
            ShortcutIssue.WORKSPACE_CHANGED
        else null
    }
}

private fun ShortcutState.changed(
    origin: WorkspaceSession,
    binding: ShortcutBinding,
): ShortcutIssue? =
    when (this) {
        is ShortcutState.Ready ->
            if (this.origin != origin || bindings.find { it.id == binding.id } != binding)
                ShortcutIssue.BINDING_CHANGED
            else null
        is ShortcutState.Failed -> ShortcutIssue.UNAVAILABLE
        else -> null
    }
