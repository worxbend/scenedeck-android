package com.scenedeck.android.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.ProfileInputValidator
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionError

private const val STEP_WELCOME = 0
private const val STEP_CONNECT = 1
private const val STEP_SUCCESS = 2

/**
 * First-run wizard (FEATURE_SPEC §1/§9, milestone M2): welcome hero → connect step (manual profile,
 * real handshake) → success with OBS version + scene count. Also serves as the pushed Help route.
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
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            step == STEP_WELCOME ->
                WelcomeStep(
                    onSetup = { step = STEP_CONNECT },
                    onSkip = onSkip,
                )

            step == STEP_CONNECT ->
                ConnectStep(
                    connectState = connectState,
                    onConnect = { name, host, port, password ->
                        viewModel.connect(name, host, port, password)
                    },
                    onSuccess = { step = STEP_SUCCESS },
                    onBack = { step = STEP_WELCOME },
                )

            else ->
                SuccessStep(
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
internal fun WelcomeStep(onSetup: () -> Unit, onSkip: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Icon(
            imageVector = SceneDeckIcons.Scenes,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(24.dp).size(56.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = "SceneDeck",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_tagline),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            WelcomeFeature(
                SceneDeckIcons.Scenes,
                stringResource(R.string.welcome_scenes),
                stringResource(R.string.welcome_scenes_desc),
            )
            WelcomeFeature(
                SceneDeckIcons.Mixer,
                stringResource(R.string.welcome_audio),
                stringResource(R.string.welcome_audio_desc),
            )
            WelcomeFeature(
                SceneDeckIcons.Stats,
                stringResource(R.string.welcome_stats),
                stringResource(R.string.welcome_stats_desc),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.onboarding_setup))
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip) {
        Text(stringResource(R.string.onboarding_skip))
    }
}

@Composable
private fun WelcomeFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    var password by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val submitConnection = {
        attempted = true
        if (ProfileInputValidator.isValid(name, host, port)) {
            onConnect(name, host.trim(), port.toInt(), optionalPassword(password))
        }
    }
    val hostError = validationError(attempted, host, ProfileInputValidator::validateHost)
    val portError = validationError(attempted, port, ProfileInputValidator::validatePort)
    val connecting = connectState is OnboardingConnectState.Connecting

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

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.profile_name)) },
        singleLine = true,
        enabled = !connecting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text(stringResource(R.string.host_label)) },
        placeholder = { Text(stringResource(R.string.host_placeholder)) },
        isError = hostError != null,
        supportingText = {
            hostError?.let { Text(it) }
        },
        singleLine = true,
        enabled = !connecting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = port,
        onValueChange = { port = it.filter(Char::isDigit) },
        label = { Text(stringResource(R.string.port_label)) },
        isError = portError != null,
        supportingText = {
            portError?.let { Text(it) }
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
        label = { Text(stringResource(R.string.password_optional)) },
        singleLine = true,
        enabled = !connecting,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))

    if (connectState is OnboardingConnectState.Failed) {
        Text(
            text = connectionErrorMessage(connectState.error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = !connecting) {
            Text(stringResource(R.string.action_back))
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = submitConnection,
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

    Spacer(Modifier.height(48.dp))
    Text(
        text = stringResource(R.string.connected_title),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text =
            if (connected != null) {
                "OBS ${connected.version.obsVersion} — ${connected.sceneCount} scenes found."
            } else {
                "OBS is ready."
            },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(48.dp))
    Button(onClick = onStart) {
        Text(stringResource(R.string.onboarding_done))
    }
}

@PreviewLightDark
@Composable
private fun OnboardingWelcomePreview() {
    SceneDeckTheme {
        Surface {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeStep(onSetup = {}, onSkip = {})
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
