package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeShortcutSource : ShortcutExecutionSource {
    override val events = MutableSharedFlow<ObsSessionEvent>(extraBufferCapacity = 16)
    val changes = MutableSharedFlow<ShortcutIssue>(extraBufferCapacity = 16)
    val policies = MutableSharedFlow<ShortcutIssue>(extraBufferCapacity = 16)

    override fun interruptions(origin: WorkspaceSession, binding: ShortcutBinding) = changes

    override fun policyChanges(policy: ShortcutPolicy) = policies

    var initialMatches = false
    var matchesBeforeWrite = false
    var writes = 0
    var confirmations = 0
    var onPrepare: suspend () -> Unit = {}
    var onValidate: suspend () -> Unit = {}
    var onWrite: suspend () -> Unit = {}
    var onConfirm: suspend () -> Boolean = { true }

    override suspend fun prepare(origin: WorkspaceSession, binding: ShortcutBinding): ShortcutPlan {
        onPrepare()
        val target =
            when (val action = binding.action) {
                is ShortcutAction.Scene -> ShortcutTarget.Scene(action.name, false)
                ShortcutAction.TakePreview -> ShortcutTarget.Take("Preview")
                is ShortcutAction.Mute -> ShortcutTarget.Mute(action.inputName, action.muted)
                is ShortcutAction.Stream -> ShortcutTarget.Stream(action.active)
                is ShortcutAction.Recording -> ShortcutTarget.Recording(action.active)
            }
        return ShortcutPlan(
            origin,
            binding,
            ShortcutPolicy(emptySet(), emptySet(), OutputSafety()),
            target,
            initialMatches,
        )
    }

    override suspend fun validate(plan: ShortcutPlan) = onValidate()

    override suspend fun write(plan: ShortcutPlan, onDispatch: () -> Unit): Boolean {
        onValidate()
        if (matchesBeforeWrite) return false
        onDispatch()
        writes++
        onWrite()
        return true
    }

    override suspend fun confirmed(plan: ShortcutPlan): Boolean {
        confirmations++
        return onConfirm()
    }
}

internal fun shortcut(action: ShortcutAction = ShortcutAction.Mute("Mic", true)) =
    ShortcutBinding(
        TEST_WORKSPACE.key,
        "binding",
        0,
        ShortcutChord("F8"),
        action,
        true,
    )
