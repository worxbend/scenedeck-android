package com.scenedeck.android.feature.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.ConnectionProfile
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.model.ConnectionState

@Composable
internal fun ConnectionActions(onAdd: () -> Unit, onScan: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.add_manually))
        }
        if (QR_PAIRING_AVAILABLE) {
            OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.scan_qr))
            }
        }
    }
}

/** Connections page (FEATURE_SPEC §1): named profiles, quick connect, optional QR pairing. */
@Composable
fun ConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profileList.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val lastUsedProfileId by viewModel.lastUsedProfileId.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()

    // Plain remember: ProfileEditor wraps ConnectionProfile/ObswsTarget (not saveable).
    var editor by remember { mutableStateOf<ProfileEditor>(ProfileEditor.Hidden) }
    var scanning by rememberSaveable { mutableStateOf(false) }

    if (scanning && QR_PAIRING_AVAILABLE) {
        QrScannerScreen(
            onDetected = { target ->
                scanning = false
                viewModel.resetTestState()
                editor = ProfileEditor.Add(prefill = target)
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        StudioPageHeader(
            title = stringResource(R.string.connections_title),
            subtitle = stringResource(R.string.connections_subtitle),
            icon = SceneDeckIcons.Connections,
        )
        Spacer(Modifier.height(20.dp))

        ConnectionActions(
            onAdd = {
                viewModel.resetTestState()
                viewModel.clearOperationError()
                editor = ProfileEditor.Add(prefill = null)
            },
            onScan = { scanning = true },
        )
        Spacer(Modifier.height(24.dp))
        ProfileDeleteFailure(operationState)

        if (profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.no_profiles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isLastUsed = profile.id == lastUsedProfileId,
                        connectionState = connectionState,
                        onConnect = { viewModel.connect(profile.id) },
                        onDisconnect = viewModel::disconnect,
                        onEdit = {
                            viewModel.resetTestState()
                            viewModel.clearOperationError()
                            editor = ProfileEditor.Edit(profile)
                        },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }

    val dismissEditor = {
        if (operationState != ProfileOperationState.Saving) {
            viewModel.resetTestState()
            viewModel.clearOperationError()
            editor = ProfileEditor.Hidden
        }
    }
    val currentEditor = editor
    if (currentEditor !is ProfileEditor.Hidden) {
        ProfileEditSheet(
            editor = currentEditor,
            testState = testState,
            saving = operationState == ProfileOperationState.Saving,
            saveFailed =
                operationState == ProfileOperationState.Failure(ProfileOperationError.SAVE),
            onTest = { host, port, password ->
                viewModel.testConnection(
                    host,
                    port,
                    password,
                    (currentEditor as? ProfileEditor.Edit)?.profile?.id,
                )
            },
            onResetTest = viewModel::resetTestState,
            onDismiss = dismissEditor,
            onSave = { id, name, host, port, password ->
                viewModel.resetTestState()
                viewModel.saveProfile(id, ProfileDraft(name, host, port, password)) {
                    editor = ProfileEditor.Hidden
                }
            },
        )
    }
}

@Composable
private fun ProfileDeleteFailure(state: ProfileOperationState) {
    if (state == ProfileOperationState.Failure(ProfileOperationError.DELETE)) {
        Text(
            stringResource(R.string.profile_delete_failed),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun ProfileCard(
    profile: ConnectionProfile,
    isLastUsed: Boolean,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val active = isLastUsed && connectionState is ConnectionState.Ready
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_message, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
            ),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            ProfileCardHeader(profile, active, onEdit, onDelete = { confirmDelete = true })
            ProfileStatusBadge(isLastUsed, active)
            Spacer(Modifier.height(12.dp))
            ProfileConnectAction(active, isLastUsed, connectionState, onConnect, onDisconnect)
        }
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun ProfileCardPreview() {
    com.scenedeck.android.core.designsystem.theme.SceneDeckTheme {
        androidx.compose.material3.Surface {
            Column(Modifier.padding(20.dp)) {
                ProfileCard(
                    profile = ConnectionProfile(1, "Studio desk", "192.168.1.20", 4455, 0),
                    isLastUsed = true,
                    connectionState = ConnectionState.Disconnected,
                    onConnect = {},
                    onDisconnect = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
    }
}

@Composable
private fun ProfileConnectAction(
    active: Boolean,
    isLastUsed: Boolean,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val busy =
        connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Identifying ||
            connectionState is ConnectionState.Reconnecting
    val showProgress = busy && isLastUsed
    val connectLabel = if (showProgress) R.string.profile_connecting else R.string.profile_connect
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (active) {
            OutlinedButton(onClick = onDisconnect) {
                Text(stringResource(R.string.action_disconnect))
            }
        } else {
            Button(onClick = onConnect, enabled = !busy) {
                if (showProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(connectLabel))
            }
        }
    }
}

@Composable
private fun ProfileStatusBadge(isLastUsed: Boolean, active: Boolean) {
    if (isLastUsed) {
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text =
                    if (active) stringResource(R.string.profile_connected)
                    else stringResource(R.string.last_used),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ProfileCardHeader(
    profile: ConnectionProfile,
    active: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StudioIconWell(
            icon = SceneDeckIcons.Connections,
            tone = if (active) StudioTone.SUCCESS else StudioTone.PRIMARY,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${profile.host}:${profile.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    SceneDeckIcons.More,
                    contentDescription = stringResource(R.string.profile_actions, profile.name),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    enabled = !active,
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
