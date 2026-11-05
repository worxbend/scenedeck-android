package com.scenedeck.android.feature.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scenedeck.android.core.data.MAX_SCENE_ALIAS_LENGTH
import com.scenedeck.android.core.designsystem.components.SceneAliasField
import kotlinx.coroutines.launch

@Composable
internal fun SceneAliasDialog(
    scene: InventoryScene,
    onDismiss: () -> Unit,
    onSave: suspend (String?) -> Boolean,
) {
    var alias by rememberSaveable(scene.name) { mutableStateOf(scene.entry?.localAlias.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Local scene label") },
        text = {
            Column {
                SceneAliasField(
                    alias,
                    {
                        alias = it
                        failed = false
                    },
                    scene.name,
                    MAX_SCENE_ALIAS_LENGTH,
                    enabled = !saving,
                )
                if (failed)
                    Text(
                        "Couldn't save this label. Reopen the editor if the scene or workspace changed.",
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && alias.trim().length <= MAX_SCENE_ALIAS_LENGTH,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (onSave(alias)) onDismiss() else failed = true
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
