package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionEvent
import kotlinx.coroutines.flow.Flow

interface ShortcutExecutionSource {
    val events: Flow<ObsSessionEvent>

    fun interruptions(origin: WorkspaceSession, binding: ShortcutBinding): Flow<ShortcutIssue>

    fun policyChanges(policy: ShortcutPolicy): Flow<ShortcutIssue>

    suspend fun prepare(origin: WorkspaceSession, binding: ShortcutBinding): ShortcutPlan

    suspend fun validate(plan: ShortcutPlan)

    /** False means a fresh read already matched. onDispatch precedes the only write attempt. */
    suspend fun write(plan: ShortcutPlan, onDispatch: () -> Unit): Boolean

    suspend fun confirmed(plan: ShortcutPlan): Boolean
}
