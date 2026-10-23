package com.scenedeck.android.feature.graph

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.EdgeVerdict
import com.scenedeck.android.core.data.SceneGraph
import com.scenedeck.android.core.data.SceneGraphEdge
import com.scenedeck.android.core.data.SceneGraphNode
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots of the scene graph: two-column fit on a phone and the all-roles fixture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GraphScreenRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    /** The e2e collection shape: four Primary scenes, two of them nesting Lower Third. */
    private val twoColumnGraph =
        SceneGraph(
            nodes =
                listOf(
                    SceneGraphNode("BRB", SceneRole.PRIMARY),
                    SceneGraphNode("Camera", SceneRole.PRIMARY),
                    SceneGraphNode("Intro", SceneRole.PRIMARY),
                    SceneGraphNode("Lower Third", SceneRole.MODULE),
                    SceneGraphNode("Screen", SceneRole.PRIMARY),
                ),
            edges =
                listOf(
                    SceneGraphEdge("Camera", "Lower Third", EdgeVerdict.OK),
                    SceneGraphEdge("Screen", "Lower Third", EdgeVerdict.OK),
                ),
        )

    private fun capture(graph: SceneGraph, darkTheme: Boolean, name: String) {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = darkTheme) {
                Surface {
                    GraphContent(
                        graph = graph,
                        selectedNode = null,
                        onNodeClick = {},
                        onDismissDetail = {},
                        parentsOf = { emptyList() },
                        childrenOf = { emptyList() },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun twoColumnsFitThePhoneWidth() {
        capture(twoColumnGraph, darkTheme = true, name = "graph_two_columns_dark.png")
        composeRule
            .onNodeWithContentDescription("5 scenes, 2 connections", substring = true)
            .assertIsDisplayed()
        // The right-column node is fully inside the viewport, not cut at the screen edge.
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val node = composeRule.onNodeWithText("Lower Third").getUnclippedBoundsInRoot()
        assertTrue(node.right < root.right - 16.dp)
    }

    @Test fun rolesFixtureDark() = capture(graphPreviewFixture(), true, "graph_roles_dark.png")

    @Test fun rolesFixtureLight() = capture(graphPreviewFixture(), false, "graph_roles_light.png")
}
