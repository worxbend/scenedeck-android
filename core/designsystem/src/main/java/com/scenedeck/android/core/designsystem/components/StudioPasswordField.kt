package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.scenedeck.android.core.designsystem.R
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector

/**
 * Shared OBS password input for connection forms: masked by default with a Show/Hide toggle.
 *
 * The caller owns [value] and must keep it out of saved-state Bundles (plain `remember`, never
 * `rememberSaveable`). Visibility is also plain `remember`, so it resets to hidden whenever
 * [visibilityKey] changes or the form leaves composition.
 */
@Composable
fun StudioPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visibilityKey: Any? = null,
    onDone: (() -> Unit)? = null,
) {
    var showPassword by remember(visibilityKey) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(SceneIcon.LOCK.imageVector, contentDescription = null) },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onDone?.invoke()
                }
            ),
        supportingText = { Text(supportingText) },
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }, enabled = enabled) {
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
        modifier = modifier.fillMaxWidth(),
    )
}
