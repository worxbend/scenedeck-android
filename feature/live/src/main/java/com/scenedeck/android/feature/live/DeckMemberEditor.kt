package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons

@Composable
internal fun DeckMemberEditor(
    page: DeckEditorPage.Members,
    available: List<String>,
    enabled: Boolean,
    onChange: (DeckEditorPage.Members) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember(page.target) { mutableStateOf("") }
    val members = page.draft.toSet()
    val known = available.toSet()
    val rows =
        (page.draft + available.filter { it !in members }).filter {
            it.contains(query.trim(), ignoreCase = true)
        }
    Column(modifier) {
        StudioSectionHeader("Scenes in ${page.target.name}")
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("Find scene to add or remove") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            "Selected scenes appear first. Use their menu to move them; clear search to reorder.",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn {
            items(rows, key = { it }) { name ->
                val index = page.draft.indexOf(name)
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(memberDescription(index, name in known)) },
                    leadingContent = {
                        Checkbox(checked = index >= 0, onCheckedChange = null, enabled = enabled)
                    },
                    modifier =
                        Modifier.heightIn(min = 56.dp).toggleable(
                            value = index >= 0,
                            enabled = enabled,
                            role = Role.Checkbox,
                        ) {
                            onChange(
                                page.copy(draft = if (it) page.draft + name else page.draft - name)
                            )
                        },
                    trailingContent = {
                        if (index >= 0)
                            DeckItemMenu("Order for $name", enabled && query.isBlank()) {
                                Action("Move up", index > 0) {
                                    onChange(
                                        page.copy(draft = moveMember(page.draft, index, index - 1))
                                    )
                                }
                                Action("Move down", index < page.draft.lastIndex) {
                                    onChange(
                                        page.copy(draft = moveMember(page.draft, index, index + 1))
                                    )
                                }
                            }
                    },
                )
            }
        }
    }
}

private fun memberDescription(index: Int, available: Boolean): String {
    val position = if (index >= 0) "${index + 1} · " else ""
    return position + if (available) "Available in OBS" else "Unavailable in OBS"
}

internal fun moveMember(names: List<String>, from: Int, to: Int): List<String> =
    names.toMutableList().apply {
        add(to, removeAt(from))
    }

internal class DeckMenuScope(private val close: () -> Unit) {
    @Composable
    fun Action(label: String, enabled: Boolean = true, run: () -> Unit) {
        DropdownMenuItem(
            text = { Text(label) },
            enabled = enabled,
            onClick = {
                close()
                run()
            },
        )
    }
}

@Composable
internal fun DeckItemMenu(
    label: String,
    enabled: Boolean,
    content: @Composable DeckMenuScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(SceneDeckIcons.More, label)
        }
        DropdownMenu(expanded, { expanded = false }) {
            DeckMenuScope { expanded = false }.content()
        }
    }
}
