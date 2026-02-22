package com.anonymous.gitlaneapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import com.anonymous.gitlaneapp.engine.CommitGraphEngine
import kotlin.math.min

/**
 * Modern Git Graph UI Component
 *
 * Draws curved Bézier connections between branch lanes and commit nodes,
 * plus a GitHub-style +++--- diff-stat bar beside each commit.
 * Supports pinch-to-zoom and pan.
 */
@Composable
fun GitGraphVisualizer(
    nodes: List<CommitGraphEngine.GraphNode>,
    onNodeSelected: (CommitGraphEngine.GraphNode) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    val laneWidth      = 40f
    val verticalSpacing = 120f
    val startX         = 60f
    val statBarStartX  = startX + (nodes.maxOfOrNull { it.lane + 1 } ?: 1) * laneWidth + 20f
    val statBarMaxW    = 72f   // total max width for the bar
    val statBarHeight  = 8f

    // Pre-compute max total changes so we can scale bars proportionally
    val maxChanges = nodes.maxOfOrNull {
        (it.commit.additions + it.commit.deletions).coerceAtLeast(1)
    }?.toFloat() ?: 1f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = state)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    ) {
        val path = Path()

        // 1. Draw Paths (Edges)
        nodes.forEachIndexed { index, node ->
            val nodeX = startX + node.lane * laneWidth
            val nodeY = index * verticalSpacing + (verticalSpacing / 2)

            node.children.forEach { childSha ->
                val childNode  = nodes.find { it.commit.sha == childSha } ?: return@forEach
                val childIndex = nodes.indexOf(childNode)
                val childX     = startX + childNode.lane * laneWidth
                val childY     = childIndex * verticalSpacing + (verticalSpacing / 2)

                path.reset()
                path.moveTo(nodeX, nodeY)

                val controlY1 = nodeY - (verticalSpacing * 0.4f)
                val controlY2 = childY + (verticalSpacing * 0.4f)
                path.cubicTo(nodeX, controlY1, childX, controlY2, childX, childY)

                drawPath(
                    path = path,
                    color = Color(node.color).copy(alpha = 0.6f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }

        // 2. Draw Nodes
        nodes.forEachIndexed { index, node ->
            val nodeX = startX + node.lane * laneWidth
            val nodeY = index * verticalSpacing + (verticalSpacing / 2)

            // Outer ring
            drawCircle(color = Color.White, radius = 12f, center = Offset(nodeX, nodeY))

            // Inner core — red for conflict, branch color otherwise
            drawCircle(
                color = if (node.commit.isConflict) Color(0xFFF85149) else Color(node.color),
                radius = 8f,
                center = Offset(nodeX, nodeY)
            )

            // Conflict 'X' overlay
            if (node.commit.isConflict) {
                val s = 6f
                drawLine(Color.White, Offset(nodeX - s, nodeY - s), Offset(nodeX + s, nodeY + s), strokeWidth = 3f)
                drawLine(Color.White, Offset(nodeX + s, nodeY - s), Offset(nodeX - s, nodeY + s), strokeWidth = 3f)
            }

            // 3. Diff stat bar  +++----
            val adds = node.commit.additions
            val dels = node.commit.deletions
            val total = (adds + dels).toFloat().coerceAtLeast(1f)
            val ratio = min(total / maxChanges, 1f)          // scale relative to max
            val barW  = statBarMaxW * ratio                   // total bar width this commit
            val addW  = barW * (adds / total)
            val delW  = barW * (dels / total)

            val barY = nodeY - statBarHeight / 2f

            if (addW > 0f) {
                drawRect(
                    color = Color(0xFF3FB950),    // GitHub green
                    topLeft = Offset(statBarStartX, barY),
                    size = androidx.compose.ui.geometry.Size(addW, statBarHeight)
                )
            }
            if (delW > 0f) {
                drawRect(
                    color = Color(0xFFF85149),    // GitHub red
                    topLeft = Offset(statBarStartX + addW, barY),
                    size = androidx.compose.ui.geometry.Size(delW, statBarHeight)
                )
            }
        }
    }
}
