package com.scenedeck.android.feature.inventory

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shot of the Inventory screen with roles/stale/accent variants (dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class InventoryScreenRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun inventoryScreenDark() =
        capture(ThemeFamily.SCENEDECK, darkTheme = true, name = "inventory_screen_dark.png")

    @Test
    fun inventoryScreenObsDark() =
        capture(ThemeFamily.OBS, darkTheme = true, name = "inventory_screen_obs_dark.png")

    @Test
    fun inventoryScreenNordDark() =
        capture(ThemeFamily.NORD, darkTheme = true, name = "inventory_screen_nord_dark.png")

    @Test
    fun inventoryScreenHighContrastDark() =
        capture(
            ThemeFamily.HIGH_CONTRAST,
            darkTheme = true,
            name = "inventory_screen_high_contrast_dark.png",
        )

    @Test
    fun inventoryScreenSceneDeckLight() =
        capture(
            ThemeFamily.SCENEDECK,
            darkTheme = false,
            name = "inventory_screen_scenedeck_light.png",
        )

    private fun capture(family: ThemeFamily, darkTheme: Boolean, name: String) {
        val state =
            InventoryUiState(
                connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
                scenes =
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
                            "Ghost",
                            SceneRegistryEntry("Ghost", SceneRole.MODULE, null, null, 6),
                            stale = true,
                        ),
                    ),
                unassignedCount = 1,
            )
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme) {
                Surface {
                    InventoryContent(
                        uiState = state,
                        snackbarHostState = SnackbarHostState(),
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
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }
}
