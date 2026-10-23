package com.scenedeck.android.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.ProfileInputValidator
import com.scenedeck.android.core.designsystem.components.StudioPasswordField
import com.scenedeck.android.core.designsystem.components.studioTextFieldColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionError

@Composable
internal fun ConnectStep(
    connectState: OnboardingConnectState,
    onConnect: (name: String, host: String, port: Int, password: String?) -> Unit,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(OnboardingDraftProfile.DEFAULT_NAME) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("4455") }
    // Credentials must never be serialized into the Activity saved-state Bundle.
    var password by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val connecting = connectState is OnboardingConnectState.Connecting
    val submitConnection = {
        attempted = true
        if (!connecting && ProfileInputValidator.isValid(name, host, port)) {
            onConnect(name, host.trim(), port.toInt(), optionalPassword(password))
        }
    }

    LaunchedEffect(connectState) {
        if (connectState is OnboardingConnectState.Connected) onSuccess()
    }

    Spacer(Modifier.height(48.dp))
    Text(
        text = stringResource(R.string.connect_to_obs),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.connect_to_obs_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileNameField(name, enabled = !connecting, onValueChange = { name = it })
        HostField(
            host,
            error = validationError(attempted, host, ProfileInputValidator::validateHost),
            enabled = !connecting,
            onValueChange = { host = it },
        )
        PortField(
            port,
            error = validationError(attempted, port, ProfileInputValidator::validatePort),
            enabled = !connecting,
            onValueChange = { port = it.filter(Char::isDigit) },
        )
        StudioPasswordField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.password_optional),
            supportingText = stringResource(R.string.password_help),
            enabled = !connecting,
            onDone = submitConnection,
        )
    }
    Spacer(Modifier.height(16.dp))

    if (connectState is OnboardingConnectState.Failed) {
        Text(
            text = connectionErrorMessage(connectState.error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
    }

    ConnectActions(connecting = connecting, onBack = onBack, onConnect = submitConnection)
}

@Composable
private fun ProfileNameField(name: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = name,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.profile_name)) },
        leadingIcon = { Icon(SceneIcon.MONITOR.imageVector, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = studioTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HostField(
    host: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = host,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.host_label)) },
        placeholder = { Text(stringResource(R.string.host_placeholder)) },
        leadingIcon = { Icon(SceneIcon.GLOBE.imageVector, contentDescription = null) },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        isError = error != null,
        supportingText = { Text(error ?: stringResource(R.string.host_help)) },
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = studioTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PortField(
    port: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = port,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.port_label)) },
        placeholder = { Text("4455") },
        leadingIcon = { Icon(SceneDeckIcons.Connections, contentDescription = null) },
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        isError = error != null,
        supportingText = { Text(error ?: stringResource(R.string.port_help)) },
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = studioTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectActions(connecting: Boolean, onBack: () -> Unit, onConnect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = !connecting) {
            Text(stringResource(R.string.action_back))
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onConnect, enabled = !connecting) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(if (connecting) "Connecting" else "Connect")
        }
    }
}

@PreviewLightDark
@Composable
private fun OnboardingConnectPreview() {
    SceneDeckTheme {
        Surface {
            Column(Modifier.padding(24.dp)) {
                ConnectStep(
                    connectState = OnboardingConnectState.Failed(ConnectionError.Auth()),
                    onConnect = { _, _, _, _ -> },
                    onSuccess = {},
                    onBack = {},
                )
            }
        }
    }
}

private fun connectionErrorMessage(error: ConnectionError): String =
    when (error) {
        is ConnectionError.Auth -> "Authentication failed — check the password"
        is ConnectionError.Unreachable ->
            "Can't reach OBS — check the address and that the WebSocket server is enabled"
        is ConnectionError.Protocol -> "Protocol error: ${error.message}"
        is ConnectionError.Closed -> "Server closed the connection (${error.code})"
    }

private fun optionalPassword(password: String): String? = password.takeIf { it.isNotBlank() }

private fun validationError(
    attempted: Boolean,
    value: String,
    validate: (String) -> String?,
): String? = if (attempted) validate(value) else null
