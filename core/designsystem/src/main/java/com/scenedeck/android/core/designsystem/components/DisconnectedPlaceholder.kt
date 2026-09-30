package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.R
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState

/** Shared offline surface; content stays reachable on short screens and at large font sizes. */
@Composable
fun DisconnectedPlaceholder(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val waiting =
        connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Identifying ||
            connectionState is ConnectionState.Reconnecting
    val title = connectionTitle(connectionState)
    val detail =
        when {
            waiting -> stringResource(R.string.obs_waiting_detail)
            connectionState is ConnectionState.Failed &&
                connectionState.error is ConnectionError.Auth ->
                stringResource(R.string.obs_auth_detail)
            connectionState is ConnectionState.Failed -> stringResource(R.string.obs_failed_detail)
            else -> stringResource(R.string.obs_offline_detail)
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                SceneDeckIcons.Connections,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(24.dp).size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (waiting) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onConnect, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
            Text(
                stringResource(
                    if (waiting || connectionState is ConnectionState.Failed)
                        R.string.obs_open_connections
                    else R.string.obs_connect_action
                )
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OfflinePreview() {
    SceneDeckTheme {
        Surface { DisconnectedPlaceholder(ConnectionState.Disconnected, {}) }
    }
}

@Composable
private fun connectionTitle(connectionState: ConnectionState): String {
    return when (connectionState) {
        is ConnectionState.Connecting,
        is ConnectionState.Identifying -> stringResource(R.string.obs_connecting)
        is ConnectionState.Reconnecting ->
            stringResource(R.string.obs_reconnecting, connectionState.attempt)
        is ConnectionState.Failed -> stringResource(R.string.obs_failed)
        else -> stringResource(R.string.obs_offline_title)
    }
}
