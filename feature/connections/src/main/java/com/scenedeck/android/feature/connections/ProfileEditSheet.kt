package com.scenedeck.android.feature.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.ProfileInputValidator
import com.scenedeck.android.core.model.ConnectionError

/** Add/edit profile form in a modal sheet: validation, password, test connection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    editor: ProfileEditor,
    testState: TestConnectionState,
    onTest: (host: String, port: Int, password: String?) -> Unit,
    onResetTest: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (id: Long?, name: String, host: String, port: Int, password: String?) -> Unit,
) {
    if (editor is ProfileEditor.Hidden) return
    val editing = (editor as? ProfileEditor.Edit)?.profile
    val prefill = (editor as? ProfileEditor.Add)?.prefill

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProfileEditForm(
            profileId = editing?.id,
            initialName = editing?.name ?: prefill?.suggestedName.orEmpty(),
            initialHost = editing?.host ?: prefill?.host.orEmpty(),
            initialPort = (editing?.port ?: prefill?.port)?.toString().orEmpty(),
            initialPassword = prefill?.password.orEmpty(),
            testState = testState,
            onTest = onTest,
            onResetTest = onResetTest,
            onSave = onSave,
        )
    }
}

@Composable
private fun ProfileEditForm(
    profileId: Long?,
    initialName: String,
    initialHost: String,
    initialPort: String,
    initialPassword: String,
    testState: TestConnectionState,
    onTest: (host: String, port: Int, password: String?) -> Unit,
    onResetTest: () -> Unit,
    onSave: (id: Long?, name: String, host: String, port: Int, password: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(initialPort) }
    var password by rememberSaveable { mutableStateOf(initialPassword) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var attemptedSave by rememberSaveable { mutableStateOf(false) }

    val nameError = if (attemptedSave) ProfileInputValidator.validateName(name) else null
    val hostError = if (attemptedSave) ProfileInputValidator.validateHost(host) else null
    val portError = if (attemptedSave) ProfileInputValidator.validatePort(port) else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (profileId == null) "Add connection" else "Edit connection",
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Profile name") },
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            placeholder = { Text("192.168.1.20") },
            isError = hostError != null,
            supportingText = { hostError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit) },
            label = { Text("Port") },
            placeholder = { Text("4455") },
            isError = portError != null,
            supportingText = { portError?.let { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onResetTest()
            },
            label = { Text("Password (optional)") },
            supportingText = {
                if (profileId != null && password.isBlank()) {
                    Text("Leave blank to keep the saved password")
                }
            },
            singleLine = true,
            visualTransformation =
                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    text = if (showPassword) "Hide" else "Show",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showPassword = !showPassword }
                        .padding(horizontal = 12.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        TestConnectionResult(testState)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    if (ProfileInputValidator.validateHost(host) == null &&
                        ProfileInputValidator.validatePort(port) == null
                    ) {
                        onTest(host.trim(), port.toInt(), password.takeIf { it.isNotBlank() })
                    }
                },
                enabled = testState !is TestConnectionState.Testing,
            ) {
                Text("Test connection")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    attemptedSave = true
                    if (ProfileInputValidator.isValid(name, host, port)) {
                        onSave(
                            profileId,
                            name.trim(),
                            host.trim(),
                            port.toInt(),
                            password.takeIf { it.isNotBlank() },
                        )
                    }
                },
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun TestConnectionResult(state: TestConnectionState) {
    when (state) {
        TestConnectionState.Idle -> Unit
        TestConnectionState.Testing -> Text(
            text = "Testing connection…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is TestConnectionState.Success -> Text(
            text = "Connected — OBS ${state.version.obsVersion} " +
                "(WebSocket ${state.version.obsWebSocketVersion})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        is TestConnectionState.Failure -> Text(
            text = when (val error = state.error) {
                is ConnectionError.Auth -> "Authentication failed — check the password"
                is ConnectionError.Unreachable ->
                    "Host unreachable — check host/port and that the OBS WebSocket server is enabled"
                is ConnectionError.Protocol -> "Protocol error: ${error.message}"
                is ConnectionError.Closed -> "Server closed the connection (${error.code})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
