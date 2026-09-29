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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.EdgeVerdict
import com.scenedeck.android.core.data.SceneGraph
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.GraphCanvas
import com.scenedeck.android.core.designsystem.components.GraphEdgeSpec
import com.scenedeck.android.core.designsystem.components.GraphNodeSpec
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
        is ConnectionState.Ready -> GraphContent(
            graph = uiState.graph,
            selectedNode = selectedNode,
            onNodeClick = viewModel::selectNode,
            onDismissDetail = { viewModel.selectNode(null) },
            parentsOf = viewModel::parentsOf,
            childrenOf = viewModel::childrenOf,
            modifier = modifier,
        )

        else -> DisconnectedPlaceholder(
            connectionState = connection,
            onConnect = onNavigateToConnections,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Scene graph",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Scene-source dependencies colored by role rules. Tap a node for details.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (graph == null || graph.nodes.isEmpty()) {
            Text(
                text = "No scenes to graph.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val colors = SceneDeckTheme.colors
            val outline = MaterialTheme.colorScheme.outlineVariant
            GraphCanvas(
                nodes = graph.nodes.map { node ->
                    GraphNodeSpec(
                        name = node.name,
                        color = roleColor(node.role, colors),
                        isStale = node.isStale,
                        inCycle = node.name in graph.cycleMembers,
                    )
                },
                edges = graph.edges.map { edge ->
                    GraphEdgeSpec(
                        from = edge.from,
                        to = edge.to,
                        color = when (edge.verdict) {
                            EdgeVerdict.OK -> outline
                            EdgeVerdict.SUSPICIOUS -> colors.warning
                            EdgeVerdict.FORBIDDEN -> colors.recording
                        },
                    )
                },
                onNodeClick = onNodeClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (selectedNode != null) {
        ModalBottomSheet(onDismissRequest = onDismissDetail) {
            val node = graph?.nodes?.firstOrNull { it.name == selectedNode }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = selectedNode, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Role: ${node?.role ?: SceneRole.PRIMARY}" +
                        if (node?.isStale == true) " · stale" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Depends on: ${childrenOf(selectedNode).ifEmpty { listOf("—") }.joinToString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Referenced by: ${parentsOf(selectedNode).ifEmpty { listOf("—") }.joinToString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismissDetail) { Text("Close") }
                }
            }
        }
    }
}

private fun roleColor(role: SceneRole, colors: com.scenedeck.android.core.designsystem.theme.SceneDeckColors): Color =
    when (role) {
        SceneRole.PRIMARY -> colors.program
        SceneRole.SECONDARY -> colors.ready
        SceneRole.MODULE -> colors.preview
        SceneRole.RAW -> colors.idle
        SceneRole.DEBUG -> colors.warning
        SceneRole.ARCHIVE -> colors.idle.copy(alpha = 0.5f)
    }
