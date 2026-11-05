package com.scenedeck.android.shortcuts

import android.view.KeyEvent
import androidx.compose.runtime.staticCompositionLocalOf
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutPhase
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.ShortcutRunState
import com.scenedeck.android.core.data.ShortcutRunner
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.ui.feedback
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal val LocalShortcutHost = staticCompositionLocalOf<ShortcutHost?> { null }

internal data class ShortcutNotice(val id: String, val text: String)

internal data class ShortcutHostState(
    val confirmation: ShortcutRunState? = null,
    val notice: ShortcutNotice? = null,
)

/** Activity-thread owner. Navigation/background cancellation reaches preparation and execution. */
internal class ShortcutHost(
    private val repository: ShortcutRepository,
    private val runner: ShortcutRunner,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow(ShortcutHostState())
    val state = current.asStateFlow()
    val textInput = ShortcutTextInputGuard()
    val admission = ShortcutAdmission(::interrupt)
    private val keys = ShortcutKeyRouter()
    private var bindings: ShortcutState.Ready? = null
    private var subscription: Job? = null
    private var active: Invocation? = null

    init {
        scope.launch { runner.state.collect { publish() } }
    }

    fun foreground(value: Boolean) {
        admission.foreground(value)
        if (!value) admission.focus(false)
        keys.clear()
        subscription?.cancel()
        bindings = null
        subscription =
            if (value)
                scope.launch {
                    repository.state.collect { bindings = it as? ShortcutState.Ready }
                }
            else null
    }

    fun focus(value: Boolean) {
        admission.focus(value)
        if (!value && runner.state.value?.phase != ShortcutPhase.CONFIRMATION) interrupt()
    }

    fun dispatch(event: KeyEvent, childHandled: Boolean, textWasEditing: Boolean = false): Boolean {
        val ready = bindings?.takeIf { repository.workspaces.isCurrent(it.origin) }
        val editable = textInput.editing || textWasEditing
        val enabled = admission.eligible && !editable && ready != null
        val context =
            if (enabled) ShortcutKeyContext(admission.activation, requireNotNull(ready)) else null
        return keys.dispatch(event, childHandled, context, ::invoke)
    }

    fun confirm(id: String) {
        if (admission.foreground && current.value.confirmation?.id == id) runner.confirm(id)
    }

    fun cancel(id: String) {
        if (current.value.confirmation?.id == id) interrupt()
    }

    fun noticeShown(id: String) {
        if (current.value.notice?.id == id) current.value = current.value.copy(notice = null)
    }

    private fun invoke(origin: WorkspaceSession, binding: ShortcutBinding): Boolean {
        if (!admission.eligible || !repository.workspaces.isCurrent(origin)) return false
        if (active != null) {
            current.value =
                current.value.copy(
                    notice =
                        ShortcutNotice(
                            UUID.randomUUID().toString(),
                            "A shortcut is already pending. Finish or cancel it first.",
                        )
                )
        } else {
            val invocation = Invocation(origin, binding, runner.state.value?.id)
            active = invocation
            invocation.job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        runner.run(origin, binding)
                    } finally {
                        publish()
                        if (active === invocation) active = null
                    }
                }
            publish()
        }
        return true
    }

    private fun interrupt() {
        keys.clear()
        current.value = current.value.copy(confirmation = null)
        active?.let {
            it.interrupted = true
            it.job?.cancel()
        }
    }

    private fun publish() {
        val invocation = active ?: return
        val run = runner.state.value?.takeIf(invocation::owns) ?: return
        val confirmation = run.takeIf {
            it.phase == ShortcutPhase.CONFIRMATION && !invocation.interrupted
        }
        val notice = run.feedback()?.let { ShortcutNotice(run.id, it) }
        current.value =
            current.value.copy(confirmation = confirmation, notice = notice ?: current.value.notice)
    }

    private class Invocation(
        val origin: WorkspaceSession,
        val binding: ShortcutBinding,
        val previousId: String?,
    ) {
        var job: Job? = null
        var interrupted = false

        fun owns(run: ShortcutRunState) =
            run.id != previousId && run.origin == origin && run.binding == binding
    }
}
