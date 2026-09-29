package com.scenedeck.android.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.GraphCanvas
import com.scenedeck.android.core.designsystem.components.GraphEdgeSpec
import com.scenedeck.android.core.designsystem.components.GraphNodeSpec
import com.scenedeck.android.core.designsystem.components.InventoryRow
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots: InventoryRow variants + a small GraphCanvas fixture (dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class M5ComponentsRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inventoryRowsDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    Column(modifier = Modifier.width(420.dp).padding(8.dp)) {
                        InventoryRow(
                            name = "Cam 1",
                            icon = SceneIcon.CAMERA.imageVector,
                            roleLabel = "Primary",
                            accentColor = Color(0xFF7E57C2),
                        )
                        InventoryRow(
                            name = "Nested Audio",
                            icon = SceneIcon.MUSIC.imageVector,
                            roleLabel = "Module",
                        )
                        InventoryRow(
                            name = "Ghost Scene",
                            icon = SceneIcon.FILM.imageVector,
                            roleLabel = "Archive",
                            stale = true,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("inventory_rows_dark.png")
    }

    @Test
    fun graphCanvasFixtureDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                val colors = SceneDeckTheme.colors
                Surface {
                    GraphCanvas(
                        nodes = listOf(
                            GraphNodeSpec("Starting Soon", colors.program),
                            GraphNodeSpec("Cam 1", colors.program),
                            GraphNodeSpec("Nested Audio", colors.preview),
                            GraphNodeSpec("Scratch", colors.warning, inCycle = true),
                        ),
                        edges = listOf(
                            GraphEdgeSpec("Starting Soon", "Nested Audio", Color(0xFF8A8A92)),
                            GraphEdgeSpec("Cam 1", "Nested Audio", Color(0xFF8A8A92)),
                            GraphEdgeSpec("Starting Soon", "Scratch", colors.recording),
                        ),
                        onNodeClick = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("graph_canvas_fixture_dark.png")
    }
}
