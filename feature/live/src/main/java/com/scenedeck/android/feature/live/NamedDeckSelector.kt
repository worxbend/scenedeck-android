package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.DeckSelection
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.designsystem.components.studioFilterChipColors

@Composable
internal fun NamedDeckSelector(
    snapshot: NamedDeckSnapshot?,
    onSelect: (DeckSelection) -> Unit,
    onManage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = snapshot?.selection as? DeckSelection.Named
    val selectedName = snapshot?.decks?.find { it.id == selected?.id }?.name
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            enabled = snapshot != null,
            colors = studioFilterChipColors(),
            border = null,
            label = {
                Text(
                    selectedName ?: "My decks",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            },
        )
        DropdownMenu(expanded, { expanded = false }) {
            snapshot?.decks?.forEach { deck ->
                DropdownMenuItem(
                    text = { Text(deck.name) },
                    onClick = {
                        expanded = false
                        onSelect(DeckSelection.Named(deck.id))
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Manage decks") },
                onClick = {
                    expanded = false
                    onManage()
                },
            )
        }
    }
}
