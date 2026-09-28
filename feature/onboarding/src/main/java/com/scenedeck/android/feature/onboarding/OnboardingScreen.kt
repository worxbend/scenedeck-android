package com.scenedeck.android.feature.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.ProfileInputValidator
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionError

private const val STEP_WELCOME = 0
private const val STEP_CONNECT = 1
private const val STEP_SUCCESS = 2

/**
 * First-run wizard (FEATURE_SPEC §1/§9, milestone M2): welcome hero → connect step
 * (manual profile, real handshake) → success with OBS version + scene count.
 * Also serves as the pushed Help route.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    onSkip: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            step == STEP_WELCOME -> WelcomeStep(
                onSetup = { step = STEP_CONNECT },
                onSkip = onSkip,
            )

            step == STEP_CONNECT -> ConnectStep(
                connectState = connectState,
                onConnect = { name, host, port, password ->
                    viewModel.connect(name, host, port, password)
                },
                onSuccess = { step = STEP_SUCCESS },
                onBack = { step = STEP_WELCOME },
            )

            else -> SuccessStep(
                state = connectState,
                onStart = {
                    viewModel.finish()
                    onFinished()
                },
            )
        }
    }
}

@Composable
private fun WelcomeStep(onSetup: () -> Unit, onSkip: () -> Unit) {
    Spacer(Modifier.height(96.dp))
    Icon(
        imageVector = SceneDeckIcons.Scenes,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp),
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "SceneDeck",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Stream from your PC. Control it from your phone.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(48.dp))
    Button(onClick = onSetup) {
        Text("Set up connection")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip) {
        Text("Skip for now")
    }
}

@Composable
private fun ConnectStep(
    connectState: OnboardingConnectState,
    onConnect: (name: String, host: String, port: Int, password: String?) -> Unit,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("My OBS") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("4455") }
    var password by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val connecting = connectState is OnboardingConnectState.Connecting

    LaunchedEffect(connectState) {
        if (connectState is OnboardingConnectState.Connected) onSuccess()
    }

    Spacer(Modifier.height(48.dp))
    Text(
        text = "Connect to OBS",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Enable the WebSocket server in OBS (Tools → WebSocket Server Settings), " +
            "then enter your PC's address.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Profile name") },
        singleLine = true,
        enabled = !connecting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text("Host") },
        placeholder = { Text("192.168.1.20") },
        isError = attempted && ProfileInputValidator.validateHost(host) != null,
        supportingText = {
            if (attempted) ProfileInputValidator.validateHost(host)?.let { Text(it) }
        },
        singleLine = true,
        enabled = !connecting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = port,
        onValueChange = { port = it.filter(Char::isDigit) },
        label = { Text("Port") },
        isError = attempted && ProfileInputValidator.validatePort(port) != null,
        supportingText = {
            if (attempted) ProfileInputValidator.validatePort(port)?.let { Text(it) }
        },
        singleLine = true,
        enabled = !connecting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password (optional)") },
        singleLine = true,
        enabled = !connecting,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))

    if (connectState is OnboardingConnectState.Failed) {
        Text(
            text = when (val error = connectState.error) {
                is ConnectionError.Auth -> "Authentication failed — check the password"
                is ConnectionError.Unreachable ->
                    "Can't reach OBS — check the address and that the WebSocket server is enabled"
                is ConnectionError.Protocol -> "Protocol error: ${error.message}"
                is ConnectionError.Closed -> "Server closed the connection (${error.code})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = !connecting) {
            Text("Back")
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                attempted = true
                if (ProfileInputValidator.isValid(name, host, port)) {
                    onConnect(name, host.trim(), port.toInt(), password.takeIf { it.isNotBlank() })
                }
            },
            enabled = !connecting,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(if (connecting) "Connecting" else "Connect")
        }
    }
}

@Composable
private fun SuccessStep(state: OnboardingConnectState, onStart: () -> Unit) {
    val connected = state as? OnboardingConnectState.Connected

    Spacer(Modifier.height(96.dp))
    Text(
        text = "You're connected",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (connected != null) {
            "OBS ${connected.version.obsVersion} — ${connected.sceneCount} scenes found."
        } else {
            "OBS is ready."
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(48.dp))
    Button(onClick = onStart) {
        Text("Start decking")
    }
}

@PreviewLightDark
@Composable
private fun OnboardingWelcomePreview() {
    SceneDeckTheme {
        WelcomeStep(onSetup = {}, onSkip = {})
    }
}
