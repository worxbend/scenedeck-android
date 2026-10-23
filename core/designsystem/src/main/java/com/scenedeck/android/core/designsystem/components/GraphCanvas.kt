package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import kotlin.math.max

/** One node of the rendered scene-dependency graph. */
data class GraphNodeSpec(
    val name: String,
    val color: Color,
    val isStale: Boolean = false,
    val inCycle: Boolean = false,
    /** Label color; must contrast with [color]. */
    val contentColor: Color = Color.White,
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

private val MAX_NODE_WIDTH = 150.dp
private val MIN_NODE_WIDTH = 100.dp
private val NODE_HEIGHT = 56.dp
private val MAX_LAYER_GAP = 90.dp
private val MIN_LAYER_GAP = 32.dp
private val ROW_GAP = 24.dp
private const val MIN_CANVAS_HEIGHT = 240f

/** Column geometry of the layered layout, in dp. */
data class GraphColumnMetrics(val nodeWidth: Float, val layerGap: Float, val contentWidth: Float) {
    /** Left edge of a node in [layer]. */
    fun nodeX(layer: Int): Float = layer * (nodeWidth + layerGap)
}

/**
 * Sizes node columns to fit [availableWidth] (dp) when possible: nodes shrink towards
 * [MIN_NODE_WIDTH] and gaps towards [MIN_LAYER_GAP] before the canvas has to scroll horizontally.
 * The content ends at the last column's right edge, so every node is either fully visible or
 * reachable by scrolling, never cut off at the viewport.
 */
fun graphColumnMetrics(layerCount: Int, availableWidth: Float): GraphColumnMetrics {
    val layers = layerCount.coerceAtLeast(1)
    val gaps = layers - 1
    val nodeWidth =
        ((availableWidth - gaps * MIN_LAYER_GAP.value) / layers).coerceIn(
            MIN_NODE_WIDTH.value,
            MAX_NODE_WIDTH.value,
        )
    val layerGap =
        if (gaps == 0) {
            0f
        } else {
            ((availableWidth - layers * nodeWidth) / gaps).coerceIn(
                MIN_LAYER_GAP.value,
                MAX_LAYER_GAP.value,
            )
        }
    return GraphColumnMetrics(nodeWidth, layerGap, layers * nodeWidth + gaps * layerGap)
}

/**
 * Scene dependency DAG visual (docs/DESIGN_SYSTEM.md §7 / FEATURE_SPEC §7): curved edges on a
 * Canvas behind role-colored node cards. Tap a node for details. Columns fit the available width
 * when they can; wider graphs scroll inside the canvas.
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
    val contentHeight = (maxRow + 1) * (NODE_HEIGHT + ROW_GAP).value - ROW_GAP.value

    BoxWithConstraints(modifier) {
        val metrics = graphColumnMetrics(maxLayer + 1, maxWidth.value)
        val overflows = metrics.contentWidth > maxWidth.value
        Column {
            if (overflows) {
                Text(
                    text = "Scroll sideways to see all ${maxLayer + 1} columns",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            GraphScrollArea(nodes, edges, layout, metrics, contentHeight, onNodeClick)
        }
    }
}

@Composable
private fun GraphScrollArea(
    nodes: List<GraphNodeSpec>,
    edges: List<GraphEdgeSpec>,
    layout: Map<String, GraphLayoutNode>,
    metrics: GraphColumnMetrics,
    contentHeight: Float,
    onNodeClick: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .size(
                    width = metrics.contentWidth.dp,
                    height = max(contentHeight, MIN_CANVAS_HEIGHT).dp,
                )
    ) {
        GraphEdges(edges, layout, metrics)
        nodes.forEach { node ->
            val pos = layout[node.name] ?: return@forEach
            GraphNode(
                node = node,
                width = metrics.nodeWidth.dp,
                onClick = { onNodeClick(node.name) },
                modifier =
                    Modifier.offset(
                        x = metrics.nodeX(pos.layer).dp,
                        y = (pos.row * (NODE_HEIGHT + ROW_GAP).value).dp,
                    ),
            )
        }
    }
}

@Composable
private fun GraphNode(
    node: GraphNodeSpec,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = width, height = NODE_HEIGHT),
        shape = MaterialTheme.shapes.medium,
        color = node.color,
        border = if (node.inCycle) BorderStroke(2.dp, SceneDeckTheme.colors.recording) else null,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (node.isStale) "${node.name} (stale)" else node.name,
                style = MaterialTheme.typography.labelLarge,
                color = node.contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width - 16.dp),
            )
        }
    }
}

@Composable
private fun GraphEdges(
    edges: List<GraphEdgeSpec>,
    layout: Map<String, GraphLayoutNode>,
    metrics: GraphColumnMetrics,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val nodeWidth = metrics.nodeWidth.dp.toPx()
        fun centerPx(name: String): Offset {
            val pos = layout[name] ?: return Offset.Zero
            return Offset(
                x = metrics.nodeX(pos.layer).dp.toPx() + nodeWidth / 2,
                y = pos.row * (NODE_HEIGHT + ROW_GAP).toPx() + NODE_HEIGHT.toPx() / 2,
            )
        }
        edges.forEach { edge ->
            val from = centerPx(edge.from)
            val to = centerPx(edge.to)
            if (from == Offset.Zero || to == Offset.Zero) return@forEach
            val start = Offset(from.x + nodeWidth / 2, from.y)
            val end = Offset(to.x - nodeWidth / 2, to.y)
            val control = (end.x - start.x) / 2
            val path =
                Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(start.x + control, start.y, end.x - control, end.y, end.x, end.y)
                }
            drawPath(path, color = edge.color, style = Stroke(width = 2.dp.toPx()))
            // Arrowhead dot at the target.
            drawCircle(color = edge.color, radius = 3.dp.toPx(), center = end)
        }
    }
}
