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

/**
 * Modern Git Graph UI Component
 * 
 * Draws curved Bézier connections between branch lanes and commit nodes.
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

    val laneWidth = 40f
    val verticalSpacing = 120f
    val startX = 60f

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

            // Draw towards children (lines flowing from bottom to top in our timeline)
            node.children.forEach { childSha ->
                val childNode = nodes.find { it.commit.sha == childSha } ?: return@forEach
                val childIndex = nodes.indexOf(childNode)
                val childX = startX + childNode.lane * laneWidth
                val childY = childIndex * verticalSpacing + (verticalSpacing / 2)

                path.reset()
                path.moveTo(nodeX, nodeY)
                
                // Use Cubic Bézier for smooth GitKraken-style curves
                val controlY1 = nodeY - (verticalSpacing * 0.4f)
                val controlY2 = childY + (verticalSpacing * 0.4f)
                
                path.cubicTo(
                    nodeX, controlY1,
                    childX, controlY2,
                    childX, childY
                )

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
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = Offset(nodeX, nodeY)
            )

            // Inner core
            drawCircle(
                color = if (node.commit.isConflict) Color(0xFFF85149) else Color(node.color),
                radius = 8f,
                center = Offset(nodeX, nodeY)
            )
            
            // Conflict 'X' overlay if applicable
            if (node.commit.isConflict) {
                val s = 6f
                drawLine(
                    color = Color.White,
                    start = Offset(nodeX - s, nodeY - s),
                    end = Offset(nodeX + s, nodeY + s),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(nodeX + s, nodeY - s),
                    end = Offset(nodeX - s, nodeY + s),
                    strokeWidth = 3f
                )
            }
        }
    }
}
