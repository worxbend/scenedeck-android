package com.scenedeck.android.feature.shortcuts

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.ui.shortcutChordOrNull

@Composable
internal fun ShortcutCaptureDialog(dismiss: () -> Unit, select: (ShortcutChord) -> Unit) {
    val focus = remember { FocusRequester() }
    val capture = remember { ShortcutKeyCapture() }
    var notice by remember {
        mutableStateOf(
            "Press a key, with optional Ctrl, Alt, Shift or Meta. For a compatible pedal, press it once."
        )
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dismiss()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        modifier =
            Modifier.focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    when (val result = capture.key(event.nativeKeyEvent)) {
                        is CapturedKey.Recorded -> {
                            select(result.chord)
                            true
                        }
                        CapturedKey.Reserved -> {
                            notice = "This key is reserved. Use A–Z, 0–9, F1–F12 or a numpad digit."
                            false
                        }
                        CapturedKey.Ignored -> false
                    }
                }
                .focusable(),
        title = { Text("Record shortcut key") },
        text = { Text(notice, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel capture") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private sealed interface CapturedKey {
    data class Recorded(val chord: ShortcutChord) : CapturedKey

    data object Reserved : CapturedKey

    data object Ignored : CapturedKey
}

private class ShortcutKeyCapture {
    private var accepted = false

    fun key(event: KeyEvent): CapturedKey {
        val initial =
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled
        if (accepted || !initial || KeyEvent.isModifierKey(event.keyCode))
            return CapturedKey.Ignored
        val chord = event.shortcutChordOrNull()
        return if (chord == null) CapturedKey.Reserved
        else {
            accepted = true
            CapturedKey.Recorded(chord)
        }
    }
}
