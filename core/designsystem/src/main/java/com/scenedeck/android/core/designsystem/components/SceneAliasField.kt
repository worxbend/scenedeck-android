package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A local label never hides the literal scene name used to address OBS. */
@Composable
fun SceneAliasField(
    value: String,
    onValueChange: (String) -> Unit,
    obsName: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text("Local label") },
        isError = value.trim().length > maxLength,
        supportingText = {
            Column {
                Text("OBS · $obsName")
                Text("Leave blank to use the OBS name · ${value.trim().length}/$maxLength")
            }
        },
    )
}
