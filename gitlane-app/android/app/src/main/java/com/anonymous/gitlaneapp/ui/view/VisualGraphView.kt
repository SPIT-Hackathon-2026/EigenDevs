package com.anonymous.gitlaneapp.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * VisualGraphView
 * 
 * Draws the git graph lines and nodes for a single commit row.
 * Lanes are calculated based on the commit's relationships.
 */
class VisualGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private var activeLanes = emptyList<Int>()
    private var parentLanes = emptyList<Int>()
    private var currentLane = 0
    private var nodeColor = Color.parseColor("#64FFDA")
    private var isConflict = false
    private var isFirst = false

    fun setData(lane: Int, activeLanes: List<Int>, parents: List<Int>, color: Int, isConflict: Boolean = false, isFirst: Boolean = false) {
        this.currentLane = lane
        this.activeLanes = activeLanes
        this.parentLanes = parents
        this.nodeColor = color
        this.isConflict = isConflict
        this.isFirst = isFirst
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val laneWidth = 40f
        val startOffset = 40f
        val centerY = h / 2

        linePaint.strokeWidth = 4f
        linePaint.alpha = 150

        // 1. Draw all active lanes passing through (background lines)
        activeLanes.forEach { l ->
            val lX = startOffset + l * laneWidth
            linePaint.color = Color.GRAY // Passing branches are subtle
            linePaint.alpha = 80
            canvas.drawLine(lX, 0f, lX, h, linePaint)
        }

        // 2. Draw current lane connection lines (more prominent)
        val centerX = startOffset + currentLane * laneWidth
        linePaint.color = nodeColor
        linePaint.alpha = 255
        linePaint.strokeWidth = 6f
        
        // Line from top to node
        if (!isFirst) {
            canvas.drawLine(centerX, 0f, centerX, centerY, linePaint)
        }

        // Lines to parents (Diagonal if merge/branch)
        parentLanes.forEach { pLane ->
            val pX = startOffset + pLane * laneWidth
            canvas.drawLine(centerX, centerY, pX, h, linePaint)
        }

        // 3. Draw the node (Circle or bold Cross)
        nodePaint.color = nodeColor
        if (isConflict) {
            val size = 12f
            nodePaint.strokeWidth = 6f
            nodePaint.style = Paint.Style.STROKE
            canvas.drawLine(centerX - size, centerY - size, centerX + size, centerY + size, nodePaint)
            canvas.drawLine(centerX + size, centerY - size, centerX - size, centerY + size, nodePaint)
            nodePaint.style = Paint.Style.FILL
        } else {
            canvas.drawCircle(centerX, centerY, 12f, nodePaint)
            canvas.drawCircle(centerX, centerY, 12f, nodeStrokePaint)
        }
    }
}
