package com.scenedeck.android.feature.connections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

internal const val QR_PAIRING_AVAILABLE = false

/** Defensive close for a restored scanner state; camera pairing is omitted in this build. */
@Composable
@Suppress("UnusedParameter")
fun QrScannerScreen(onDetected: (ObswsTarget) -> Unit, onClose: () -> Unit) {
    LaunchedEffect(Unit) { onClose() }
}
