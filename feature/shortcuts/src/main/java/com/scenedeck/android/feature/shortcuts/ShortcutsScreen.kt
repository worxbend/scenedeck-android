package com.scenedeck.android.feature.shortcuts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

@Composable
fun ShortcutsScreen(
    modifier: Modifier = Modifier,
    viewModel: ShortcutsViewModel = hiltViewModel(),
    onConnections: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = state.edits.editor
    BackHandler(editor != null) {
        editor?.let { viewModel.onAction(ShortcutsAction.Edit(it.id, ShortcutEdit.Close)) }
    }
    ShortcutsContent(state, viewModel::onAction, onConnections, modifier)
}

@Composable
internal fun ShortcutsContent(
    state: ShortcutsUiState,
    onAction: (ShortcutsAction) -> Unit,
    onConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var help by remember { mutableStateOf(false) }
    val editor = state.edits.editor
    val origin = editor?.origin ?: state.workspace.ready?.origin
    val spacing = SceneDeckTheme.spacing
    Column(modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        key(editor?.id ?: "list") {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.pageGutter),
                verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
            ) {
                item {
                    StudioPageHeader(
                        "Shortcuts",
                        origin?.let(state.workspace::title) ?: "Keyboard & foot pedals",
                        SceneDeckIcons.Shortcuts,
                    )
                }
                item { TextButton(onClick = { help = true }) { Text("Shortcut help") } }
                state.edits.message?.let { message ->
                    item {
                        Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (editor == null) shortcutList(state, onAction, onConnections)
                else shortcutEditor(state, editor, onAction)
            }
        }
    }
    ShortcutEditDialogs(state, onAction)
    if (help) ShortcutHelp { help = false }
}
