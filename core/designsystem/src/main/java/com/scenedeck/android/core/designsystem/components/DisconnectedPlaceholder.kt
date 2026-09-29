package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.R
import com.scenedeck.android.core.model.ConnectionState

/** Designed offline state (docs/ARCHITECTURE.md — error & offline UX). */
@Composable
fun DisconnectedPlaceholder(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ill_disconnected),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
        Spacer(Modifier.height(24.dp))
        when (connectionState) {
            is ConnectionState.Connecting, is ConnectionState.Identifying -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Connecting to OBS…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            is ConnectionState.Reconnecting -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Reconnecting (attempt ${connectionState.attempt})…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            is ConnectionState.Failed -> {
                Text(
                    text = "Connection failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Check the host, port and password, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onConnect) { Text("Open connections") }
            }

            ConnectionState.Disconnected, is ConnectionState.Ready -> {
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect to an OBS instance to drive your scenes from here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onConnect) { Text("Connect to OBS") }
            }
        }
    }
}
