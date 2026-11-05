package com.scenedeck.android.core.data

/** Captured target; a Studio change must not reinterpret an admitted scene command. */
sealed interface ShortcutTarget {
    data class Scene(val name: String, val preview: Boolean) : ShortcutTarget

    data class Take(val previewName: String) : ShortcutTarget

    data class Mute(val inputName: String, val muted: Boolean) : ShortcutTarget

    data class Stream(val active: Boolean) : ShortcutTarget

    data class Recording(val active: Boolean) : ShortcutTarget
}

data class ShortcutPolicy(
    val lockedInputs: Set<String>,
    val allowedInputs: Set<String>,
    val outputSafety: OutputSafety,
)

data class ShortcutPlan(
    val origin: WorkspaceSession,
    val binding: ShortcutBinding,
    val policy: ShortcutPolicy,
    val target: ShortcutTarget,
    val initiallyMatches: Boolean,
)

enum class ShortcutPhase {
    CHECKING,
    CONFIRMATION,
    WRITING,
    VERIFYING,
    FINISHED,
}

enum class ShortcutOutcome {
    APPLIED,
    ALREADY_MATCHED,
    NOT_RUN,
    UNKNOWN,
}

enum class ShortcutIssue {
    WORKSPACE_CHANGED,
    BINDING_CHANGED,
    POLICY_CHANGED,
    TARGET_CHANGED,
    UNSUPPORTED,
    MISSING,
    LOCKED,
    NOT_ALLOWED,
    STUDIO_OFF,
    NOT_READY,
    UNAVAILABLE,
    BUSY,
    CANCELLED,
    EXPIRED,
    TIMEOUT,
    REJECTED,
}

data class ShortcutRunState(
    val id: String,
    val origin: WorkspaceSession,
    val binding: ShortcutBinding,
    val phase: ShortcutPhase = ShortcutPhase.CHECKING,
    val target: ShortcutTarget? = null,
    val outcome: ShortcutOutcome? = null,
    val issue: ShortcutIssue? = null,
)

internal class ShortcutStopped(val issue: ShortcutIssue) : IllegalStateException("Shortcut stopped")

internal fun UserSettings.shortcutPolicy() =
    ShortcutPolicy(lockedInputs.toSet(), audioAllowList.toSet(), outputSafety())

internal fun ShortcutPlan.matchesBinding(): Boolean =
    when (val action = binding.action) {
        is ShortcutAction.Scene -> target is ShortcutTarget.Scene && target.name == action.name
        ShortcutAction.TakePreview -> target is ShortcutTarget.Take
        is ShortcutAction.Mute -> target == ShortcutTarget.Mute(action.inputName, action.muted)
        is ShortcutAction.Stream -> target == ShortcutTarget.Stream(action.active)
        is ShortcutAction.Recording -> target == ShortcutTarget.Recording(action.active)
    }

internal fun ShortcutTarget.needsConfirmation(safety: OutputSafety): Boolean =
    when (this) {
        is ShortcutTarget.Stream -> OutputSafetyGate.streamAction(!active, safety)
        is ShortcutTarget.Recording -> OutputSafetyGate.recordAction(!active, safety)
        else -> OutputAction.PERFORM
    } == OutputAction.REQUIRE_CONFIRMATION
