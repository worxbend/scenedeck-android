package com.scenedeck.android.feature.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.EdgeVerdict
import com.scenedeck.android.core.data.SceneGraph
import com.scenedeck.android.core.data.SceneGraphEdge
import com.scenedeck.android.core.data.SceneGraphNode
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.GraphCanvas
import com.scenedeck.android.core.designsystem.components.GraphEdgeSpec
import com.scenedeck.android.core.designsystem.components.GraphNodeSpec
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckColors
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState

/** Graph page — scene dependency DAG with role-rule edge classification (§7). */
@Composable
fun GraphScreen(
    modifier: Modifier = Modifier,
    viewModel: GraphViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()

    when (val connection = uiState.connection) {
        is ConnectionState.Ready ->
            GraphContent(
                graph = uiState.graph,
                selectedNode = selectedNode,
                onNodeClick = viewModel::selectNode,
                onDismissDetail = { viewModel.selectNode(null) },
                parentsOf = viewModel::parentsOf,
                childrenOf = viewModel::childrenOf,
                modifier = modifier,
            )

        else ->
            DisconnectedPlaceholder(
                connectionState = connection,
                onConnect = onNavigateToConnections,
            )
    }
}

/** Spoken edge count; always the number of drawn (deduplicated) edges. */
internal fun graphConnectionsLabel(graph: SceneGraph): String =
    if (graph.edges.size == 1) "1 connection" else "${graph.edges.size} connections"

@Composable
internal fun GraphContent(
    graph: SceneGraph?,
    selectedNode: String?,
    onNodeClick: (String) -> Unit,
    onDismissDetail: () -> Unit,
    parentsOf: (String) -> List<String>,
    childrenOf: (String) -> List<String>,
    modifier: Modifier = Modifier,
) {
    val spacing = SceneDeckTheme.spacing
    Column(
        modifier =
            modifier.fillMaxSize().statusBarsPadding().padding(horizontal = spacing.pageGutter)
    ) {
        Spacer(Modifier.height(spacing.sectionGap))
        StudioPageHeader("Scene graph", "Explore scene-source dependencies", SceneDeckIcons.Graph)
        Spacer(Modifier.height(spacing.sectionGap))

        if (graph == null || graph.nodes.isEmpty()) {
            Text(
                text = "No scenes to graph.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SceneGraphCanvas(graph, onNodeClick)
        }
    }

    if (selectedNode != null) {
        NodeDetailSheet(
            graph = graph,
            selectedNode = selectedNode,
            onDismiss = onDismissDetail,
            parentsOf = parentsOf,
            childrenOf = childrenOf,
        )
    }
}

@Composable
private fun SceneGraphCanvas(graph: SceneGraph, onNodeClick: (String) -> Unit) {
    val colors = SceneDeckTheme.colors
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outlineVariant
    GraphCanvas(
        nodes =
            graph.nodes.map { node ->
                val (fill, label) = roleColors(node.role, scheme, colors)
                GraphNodeSpec(
                    name = node.name,
                    color = fill,
                    contentColor = label,
                    isStale = node.isStale,
                    inCycle = node.name in graph.cycleMembers,
                )
            },
        edges =
            graph.edges.map { edge ->
                GraphEdgeSpec(
                    from = edge.from,
                    to = edge.to,
                    color =
                        when (edge.verdict) {
                            EdgeVerdict.OK -> outline
                            EdgeVerdict.SUSPICIOUS -> colors.warning
                            EdgeVerdict.FORBIDDEN -> colors.recording
                        },
                )
            },
        onNodeClick = onNodeClick,
        modifier =
            Modifier.fillMaxWidth().semantics {
                contentDescription =
                    "Scene dependency graph, ${graph.nodes.size} scenes, " +
                        "${graphConnectionsLabel(graph)}. Double-tap a node for details."
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailSheet(
    graph: SceneGraph?,
    selectedNode: String,
    onDismiss: () -> Unit,
    parentsOf: (String) -> List<String>,
    childrenOf: (String) -> List<String>,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val node = graph?.nodes?.firstOrNull { it.name == selectedNode }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = selectedNode, style = MaterialTheme.typography.titleLarge)
            Text(
                text =
                    "Role: ${node?.role ?: SceneRole.PRIMARY}" +
                        if (node?.isStale == true) " · stale" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    "Depends on: ${childrenOf(selectedNode).ifEmpty { listOf("—") }.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text =
                    "Referenced by: ${parentsOf(selectedNode).ifEmpty { listOf("—") }.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

/**
 * Node fill and label colors per role. Roles use the theme's accent containers and neutral tones,
 * never the program/LIVE red, which stays reserved for on-air state and graph errors (forbidden
 * edges, cycles). Debug keeps the amber warning tone.
 */
internal fun roleColors(
    role: SceneRole,
    scheme: ColorScheme,
    colors: SceneDeckColors,
): Pair<Color, Color> =
    when (role) {
        SceneRole.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        SceneRole.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        SceneRole.MODULE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SceneRole.RAW -> scheme.surfaceContainerHighest to scheme.onSurface
        SceneRole.DEBUG -> colors.warning to Color.Black
        SceneRole.ARCHIVE -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }

/** Preview/golden fixture: every role, a duplicate-free set of OK/suspicious/forbidden edges. */
internal fun graphPreviewFixture(): SceneGraph =
    SceneGraph(
        nodes =
            listOf(
                SceneGraphNode("Camera", SceneRole.PRIMARY),
                SceneGraphNode("Screen", SceneRole.SECONDARY),
                SceneGraphNode("Lower Third", SceneRole.MODULE),
                SceneGraphNode("Mic Rig", SceneRole.RAW),
                SceneGraphNode("Scratch", SceneRole.DEBUG),
                SceneGraphNode("Old Intro", SceneRole.ARCHIVE, isStale = true),
            ),
        edges =
            listOf(
                SceneGraphEdge("Camera", "Lower Third", EdgeVerdict.OK),
                SceneGraphEdge("Screen", "Lower Third", EdgeVerdict.OK),
                SceneGraphEdge("Lower Third", "Mic Rig", EdgeVerdict.OK),
                SceneGraphEdge("Camera", "Scratch", EdgeVerdict.FORBIDDEN),
                SceneGraphEdge("Screen", "Camera", EdgeVerdict.SUSPICIOUS),
            ),
    )

@PreviewLightDark
@Composable
private fun GraphEmptyPreview() {
    SceneDeckTheme {
        Surface {
            GraphContent(
                graph = null,
                selectedNode = null,
                onNodeClick = {},
                onDismissDetail = {},
                parentsOf = { emptyList() },
                childrenOf = { emptyList() },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GraphRolesPreview() {
    SceneDeckTheme {
        Surface {
            GraphContent(
                graph = graphPreviewFixture(),
                selectedNode = null,
                onNodeClick = {},
                onDismissDetail = {},
                parentsOf = { emptyList() },
                childrenOf = { emptyList() },
            )
        }
    }
}
