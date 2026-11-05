package com.scenedeck.android.shortcuts

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.ShortcutRunState
import com.scenedeck.android.core.data.ShortcutTarget
import com.scenedeck.android.core.ui.label

@Composable
internal fun ShortcutHostFeedback(host: ShortcutHost) {
    val state by host.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it.text, Toast.LENGTH_LONG).show()
            host.noticeShown(it.id)
        }
    }
    state.confirmation?.let { run ->
        ShortcutConfirmation(
            run,
            confirm = { host.confirm(run.id) },
            dismiss = { host.cancel(run.id) },
        )
    }
}

@Composable
internal fun ShortcutConfirmation(run: ShortcutRunState, confirm: () -> Unit, dismiss: () -> Unit) {
    val action =
        when (val target = run.target) {
            is ShortcutTarget.Stream -> if (target.active) "Start stream" else "Stop stream"
            is ShortcutTarget.Recording ->
                if (target.active) "Start recording" else "Stop recording"
            else -> return
        }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("$action?") },
        text = {
            Column(
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${run.binding.chord.label()} requests this action in connection #${run.origin.key.profileId} " +
                        "· ${run.origin.key.collectionName}."
                )
                Text(
                    "Confirm this one action. Output Safety is checked again before sending it to OBS. " +
                        "This confirmation expires after 60 seconds."
                )
            }
        },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
