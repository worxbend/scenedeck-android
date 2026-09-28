package com.scenedeck.android.feature.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.ConnectionProfile
import com.scenedeck.android.core.model.ConnectionState

/** Connections page (FEATURE_SPEC §1): named profiles, quick connect, QR pairing. */
@Composable
fun ConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profileList.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val lastUsedProfileId by viewModel.lastUsedProfileId.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

    // Plain remember: ProfileEditor wraps ConnectionProfile/ObswsTarget (not saveable).
    var editor by remember { mutableStateOf<ProfileEditor>(ProfileEditor.Hidden) }
    var scanning by rememberSaveable { mutableStateOf(false) }

    if (scanning) {
        QrScannerScreen(
            onDetected = { target ->
                scanning = false
                editor = ProfileEditor.Add(prefill = target)
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Connections",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Named OBS connection profiles with quick-switch and automatic reconnect.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { editor = ProfileEditor.Add(prefill = null) }) {
                Text("Add manually")
            }
            OutlinedButton(onClick = { scanning = true }) {
                Text("Scan QR")
            }
        }
        Spacer(Modifier.height(24.dp))

        if (profiles.isEmpty()) {
            Text(
                text = "No profiles yet. Add your OBS host manually or scan an obsws:// QR code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isLastUsed = profile.id == lastUsedProfileId,
                        connectionState = connectionState,
                        onConnect = { viewModel.connect(profile.id) },
                        onDisconnect = viewModel::disconnect,
                        onEdit = { editor = ProfileEditor.Edit(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }

    val currentEditor = editor
    if (currentEditor !is ProfileEditor.Hidden) {
        ProfileEditSheet(
            editor = currentEditor,
            testState = testState,
            onTest = { host, port, password -> viewModel.testConnection(host, port, password) },
            onResetTest = viewModel::resetTestState,
            onDismiss = { editor = ProfileEditor.Hidden },
            onSave = { id, name, host, port, password ->
                viewModel.saveProfile(id, ProfileDraft(name, host, port, password))
                editor = ProfileEditor.Hidden
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    isLastUsed: Boolean,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${profile.host}:${profile.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLastUsed) {
                    Text(
                        text = "Last used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val busy = connectionState is ConnectionState.Connecting ||
                    connectionState is ConnectionState.Identifying ||
                    connectionState is ConnectionState.Reconnecting
                if (connectionState is ConnectionState.Ready) {
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                } else {
                    Button(onClick = onConnect, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (busy) "Connecting" else "Connect")
                    }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
