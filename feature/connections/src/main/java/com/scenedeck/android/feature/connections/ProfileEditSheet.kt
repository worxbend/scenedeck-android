package com.scenedeck.android.feature.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.ProfileInputValidator
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.studioTextFieldColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.model.ConnectionError

/** Add/edit profile form in a modal sheet: validation, password, test connection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    editor: ProfileEditor,
    testState: TestConnectionState,
    saving: Boolean = false,
    saveFailed: Boolean = false,
    onTest: (host: String, port: Int, password: String?) -> Unit,
    onResetTest: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (id: Long?, name: String, host: String, port: Int, password: String?) -> Unit,
) {
    if (editor is ProfileEditor.Hidden) return
    val editing = (editor as? ProfileEditor.Edit)?.profile
    val prefill = (editor as? ProfileEditor.Add)?.prefill

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        ProfileEditForm(
            profileId = editing?.id,
            initialName = editing?.name ?: prefill?.suggestedName.orEmpty(),
            initialHost = editing?.host ?: prefill?.host.orEmpty(),
            initialPort = (editing?.port ?: prefill?.port ?: 4455).toString(),
            initialPassword = prefill?.password.orEmpty(),
            testState = testState,
            saving = saving,
            saveFailed = saveFailed,
            onTest = onTest,
            onResetTest = onResetTest,
            onSave = onSave,
        )
    }
}

@Composable
internal fun ProfileEditForm(
    profileId: Long?,
    initialName: String,
    initialHost: String,
    initialPort: String,
    initialPassword: String,
    testState: TestConnectionState,
    saving: Boolean = false,
    saveFailed: Boolean = false,
    onTest: (host: String, port: Int, password: String?) -> Unit,
    onResetTest: () -> Unit,
    onSave: (id: Long?, name: String, host: String, port: Int, password: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(initialPort) }
    // Credentials must never be serialized into the Activity saved-state Bundle.
    var password by remember(profileId, initialPassword) { mutableStateOf(initialPassword) }
    var attemptedSave by rememberSaveable { mutableStateOf(false) }
    var attemptedTest by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val titleRes = if (profileId == null) R.string.add_connection else R.string.edit_connection
    val nameError = ProfileInputValidator.validateName(name).takeIf { attemptedSave }
    val hostError =
        if (attemptedSave || attemptedTest) ProfileInputValidator.validateHost(host) else null
    val portError =
        if (attemptedSave || attemptedTest) ProfileInputValidator.validatePort(port) else null

    val onTestClick = {
        attemptedTest = true
        if (
            ProfileInputValidator.validateHost(host) == null &&
                ProfileInputValidator.validatePort(port) == null
        ) {
            onTest(host.trim(), port.toInt(), password.takeIf { it.isNotBlank() })
        }
    }
    val onSaveClick = {
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
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudioPageHeader(
            title = stringResource(titleRes),
            subtitle = stringResource(R.string.connection_form_subtitle),
            icon = SceneDeckIcons.Connections,
        )
        StudioSectionHeader(stringResource(R.string.connection_identity))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.profile_name)) },
            placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
            leadingIcon = { Icon(SceneIcon.MONITOR.imageVector, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                    }
                ),
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it) } },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = studioTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        StudioSectionHeader(stringResource(R.string.connection_server))
        OutlinedTextField(
            value = host,
            onValueChange = {
                host = it
                onResetTest()
            },
            label = { Text(stringResource(R.string.host_label)) },
            leadingIcon = { Icon(SceneIcon.GLOBE.imageVector, contentDescription = null) },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                    }
                ),
            placeholder = { Text(stringResource(R.string.host_placeholder)) },
            isError = hostError != null,
            supportingText = { Text(hostError ?: stringResource(R.string.host_help)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = studioTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = port,
            onValueChange = {
                port = it.filter(Char::isDigit)
                onResetTest()
            },
            label = { Text(stringResource(R.string.port_label)) },
            leadingIcon = { Icon(SceneDeckIcons.Connections, contentDescription = null) },
            placeholder = { Text("4455") },
            isError = portError != null,
            supportingText = { Text(portError ?: stringResource(R.string.port_help)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                    }
                ),
            shape = MaterialTheme.shapes.large,
            colors = studioTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        StudioSectionHeader(stringResource(R.string.connection_security))
        PasswordInput(
            profileId,
            password,
            onValueChange = {
                password = it
                onResetTest()
            },
        )

        TestConnectionResult(testState)

        ProfileSaveFailure(saveFailed)
        ProfileFormActions(testState, onTestClick, onSaveClick, saving)
    }
}

@Composable
private fun ProfileSaveFailure(failed: Boolean) {
    if (failed)
        Text(stringResource(R.string.profile_save_failed), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ProfileFormActions(
    testState: TestConnectionState,
    onTest: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onTest,
            enabled = !saving && testState !is TestConnectionState.Testing,
        ) {
            Text(stringResource(R.string.test_connection))
        }
        Button(modifier = Modifier.weight(1f), onClick = onSave, enabled = !saving) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun PasswordInput(profileId: Long?, password: String, onValueChange: (String) -> Unit) {
    var showPassword by remember(profileId) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = password,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.password_optional)) },
        leadingIcon = { Icon(SceneIcon.LOCK.imageVector, contentDescription = null) },
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        supportingText = {
            if (profileId != null && password.isBlank()) {
                Text(stringResource(R.string.password_keep_saved))
            } else {
                Text(stringResource(R.string.password_help))
            }
        },
        singleLine = true,
        visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    imageVector =
                        if (showPassword) SceneIcon.LOCK_OPEN.imageVector
                        else SceneIcon.LOCK.imageVector,
                    contentDescription =
                        stringResource(
                            if (showPassword) R.string.password_hide else R.string.password_show
                        ),
                )
            }
        },
        shape = MaterialTheme.shapes.large,
        colors = studioTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestConnectionResult(state: TestConnectionState) {
    when (state) {
        TestConnectionState.Idle -> Unit
        TestConnectionState.Testing ->
            Text(
                text = stringResource(R.string.testing_connection),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        is TestConnectionState.Success ->
            Text(
                text =
                    stringResource(
                        R.string.test_success,
                        state.version.obsVersion,
                        state.version.obsWebSocketVersion,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

        is TestConnectionState.Failure ->
            Text(
                text =
                    when (val error = state.error) {
                        is ConnectionError.Auth -> stringResource(R.string.test_auth_failed)
                        is ConnectionError.Unreachable -> stringResource(R.string.test_unreachable)
                        is ConnectionError.Protocol ->
                            stringResource(R.string.test_protocol, error.message)
                        is ConnectionError.Closed ->
                            stringResource(R.string.test_closed, error.code)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun ProfileEditorPreview() {
    com.scenedeck.android.core.designsystem.theme.SceneDeckTheme {
        androidx.compose.material3.Surface {
            ProfileEditForm(
                profileId = null,
                initialName = "Studio desk",
                initialHost = "192.168.1.20",
                initialPort = "4455",
                initialPassword = "",
                testState = TestConnectionState.Idle,
                onTest = { _, _, _ -> },
                onResetTest = {},
                onSave = { _, _, _, _, _ -> },
            )
        }
    }
}
