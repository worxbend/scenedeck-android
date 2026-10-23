package com.scenedeck.android.feature.inventory

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.UiDensity
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo

/**
 * Inventory fixture shared by the preview and golden shots: explicit roles, an implicit
 * (unassigned) Primary and long-named stale entries.
 */
internal fun inventoryPreviewState(): InventoryUiState {
    val scenes =
        listOf(
            InventoryScene(
                "Cam 1",
                SceneRegistryEntry("Cam 1", SceneRole.PRIMARY, 0xFF7E57C2, "CAMERA", 0),
                stale = false,
            ),
            InventoryScene("Screen", null, stale = false),
            InventoryScene(
                "Quiet A",
                SceneRegistryEntry("Quiet A", SceneRole.ARCHIVE, null, null, 5),
                stale = false,
            ),
            InventoryScene(
                "Be Right Back Loop",
                SceneRegistryEntry("Be Right Back Loop", SceneRole.MODULE, null, null, 6),
                stale = true,
            ),
            InventoryScene(
                "Outro Credits Roll",
                SceneRegistryEntry("Outro Credits Roll", null, 0xFF26A69A, null, 7),
                stale = true,
            ),
        )
    return InventoryUiState(
        connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
        scenes = scenes,
        unassignedCount = scenes.count { it.unassigned },
    )
}

@Composable
internal fun InventoryFixture(state: InventoryUiState = inventoryPreviewState()) {
    InventoryContent(
        uiState = state,
        snackbarHostState = remember { SnackbarHostState() },
        onSetRole = { _, _ -> },
        onSetAccent = { _, _ -> },
        onSetIcon = { _, _ -> },
        onRemoveStale = {},
        onReorder = {},
        onBulkAssign = {},
        onExport = {},
        onImport = {},
    )
}

@PreviewLightDark
@Composable
private fun InventoryScreenPreview() {
    SceneDeckTheme { Surface { InventoryFixture() } }
}

@PreviewLightDark
@Composable
private fun InventoryScreenCompactPreview() {
    SceneDeckTheme(density = UiDensity.COMPACT) { Surface { InventoryFixture() } }
}
