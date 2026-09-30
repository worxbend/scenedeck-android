package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import kotlin.math.max

/** One node of the rendered scene-dependency graph. */
data class GraphNodeSpec(
    val name: String,
    val color: Color,
    val isStale: Boolean = false,
    val inCycle: Boolean = false,
)

/** One styled edge of the rendered graph. */
data class GraphEdgeSpec(
    val from: String,
    val to: String,
    val color: Color,
)

/** Position of a node in the layered layout (layer = column, row = slot). */
data class GraphLayoutNode(val name: String, val layer: Int, val row: Int)

/**
 * Hand-rolled longest-path layering (no deps): relaxes layer[child] = parent+1 up to |V| passes, so
 * cycle edges simply stop relaxing. Rows sort by name for determinism (Roborazzi-stable).
 */
fun layeredGraphLayout(
    nodeNames: List<String>,
    edges: List<Pair<String, String>>,
): Map<String, GraphLayoutNode> {
    val layer = nodeNames.associateWith { 0 }.toMutableMap()
    repeat(nodeNames.size) {
        var changed = false
        edges.forEach { (from, to) ->
            val candidate = (layer[from] ?: 0) + 1
            if (candidate < nodeNames.size && candidate > (layer[to] ?: 0)) {
                layer[to] = candidate
                changed = true
            }
        }
        if (!changed) return@repeat
    }
    val byLayer = nodeNames.groupBy { layer[it] ?: 0 }
    val result = mutableMapOf<String, GraphLayoutNode>()
    byLayer.forEach { (layerIndex, names) ->
        names.sorted().forEachIndexed { row, name ->
            result[name] = GraphLayoutNode(name, layerIndex, row)
        }
    }
    return result
}

private val NODE_WIDTH = 150.dp
private val NODE_HEIGHT = 56.dp
private val LAYER_GAP = 90.dp
private val ROW_GAP = 24.dp

/**
 * Scene dependency DAG visual (docs/DESIGN_SYSTEM.md §7 / FEATURE_SPEC §7): curved edges on a
 * Canvas behind role-colored node cards. Tap a node for details.
 */
@Composable
fun GraphCanvas(
    nodes: List<GraphNodeSpec>,
    edges: List<GraphEdgeSpec>,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout =
        layeredGraphLayout(
            nodeNames = nodes.map { it.name },
            edges = edges.map { it.from to it.to },
        )
    val maxLayer = layout.values.maxOfOrNull { it.layer } ?: 0
    val maxRow = layout.values.maxOfOrNull { it.row } ?: 0
    val contentWidth = (maxLayer + 1) * (NODE_WIDTH + LAYER_GAP).value + LAYER_GAP.value
    val contentHeight = (maxRow + 1) * (NODE_HEIGHT + ROW_GAP).value + ROW_GAP.value

    Box(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .size(
                    width = max(contentWidth, 320f).dp,
                    height = max(contentHeight, 240f).dp,
                )
    ) {
        GraphEdges(edges, layout)
        nodes.forEach { node ->
            val pos = layout[node.name] ?: return@forEach
            Surface(
                onClick = { onNodeClick(node.name) },
                modifier =
                    Modifier.offset(
                            x = (pos.layer * (NODE_WIDTH + LAYER_GAP).value).dp,
                            y = (pos.row * (NODE_HEIGHT + ROW_GAP).value).dp,
                        )
                        .size(width = NODE_WIDTH, height = NODE_HEIGHT),
                shape = MaterialTheme.shapes.medium,
                color = node.color,
                border =
                    if (node.inCycle) {
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            SceneDeckTheme.colors.recording,
                        )
                    } else {
                        null
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (node.isStale) "${node.name} (stale)" else node.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(NODE_WIDTH - 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphEdges(edges: List<GraphEdgeSpec>, layout: Map<String, GraphLayoutNode>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        fun centerPx(name: String): Offset {
            val pos = layout[name] ?: return Offset.Zero
            return Offset(
                x = pos.layer * (NODE_WIDTH + LAYER_GAP).toPx() + NODE_WIDTH.toPx() / 2,
                y = pos.row * (NODE_HEIGHT + ROW_GAP).toPx() + NODE_HEIGHT.toPx() / 2,
            )
        }
        edges.forEach { edge ->
            val from = centerPx(edge.from)
            val to = centerPx(edge.to)
            if (from == Offset.Zero || to == Offset.Zero) return@forEach
            val start = Offset(from.x + NODE_WIDTH.toPx() / 2, from.y)
            val end = Offset(to.x - NODE_WIDTH.toPx() / 2, to.y)
            val control = (end.x - start.x) / 2
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(
                        start.x + control,
                        start.y,
                        end.x - control,
                        end.y,
                        end.x,
                        end.y,
                    )
                }
            drawPath(
                path,
                color = edge.color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
            // Arrowhead dot at the target.
            drawCircle(color = edge.color, radius = 3.dp.toPx(), center = end)
        }
    }
}
