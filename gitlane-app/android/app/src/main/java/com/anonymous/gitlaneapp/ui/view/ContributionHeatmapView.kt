package com.anonymous.gitlaneapp.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draws a GitHub-style 52×7 contribution heatmap with SQUARE cells.
 *
 * Cell size is derived from the available height so that each cell is perfectly
 * square. The measured width is then calculated from that cell size.
 */
class ContributionHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cols = 52
    private val rows = 7
    private val gapPx = context.resources.displayMetrics.density * 3f  // 3dp gap

    // Levels 0–4 colours: empty cells are slightly visible so green marks pop
    private val levelColors = intArrayOf(
        Color.parseColor("#1C2128"), // 0 – empty (visible dark grid)
        Color.parseColor("#0E4429"), // 1 – low green
        Color.parseColor("#006D32"), // 2 – medium green
        Color.parseColor("#26A641"), // 3 – bright green
        Color.parseColor("#39D353")  // 4 – high (GitHub green)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect  = RectF()

    /** 364-length array of contribution levels (0–4). */
    private var data: IntArray = IntArray(364) { 0 }

    fun setData(contributions: IntArray) {
        data = contributions.copyOf(364)
        requestLayout()  // recalc size in case not yet measured
        invalidate()
    }

    // ─── Square-cell measurement ───────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        // Determine final height
        val finalH = when (hMode) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> hSize
            else -> (rows * 14 + (rows - 1) * gapPx.toInt()).coerceAtLeast(60)
        }

        // Square cell side = (height - total vertical gaps) / rows
        val cellSize = (finalH - gapPx * (rows - 1)) / rows

        // Compute the width we need for 52 square columns
        val neededW = (cellSize * cols + gapPx * (cols - 1)).toInt()

        val finalW = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> neededW.coerceAtMost(MeasureSpec.getSize(widthMeasureSpec))
            else -> neededW
        }
        setMeasuredDimension(finalW, finalH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        // Square cell derived from height
        val cellSize = (h - gapPx * (rows - 1)) / rows
        val radius   = cellSize * 0.3f  // 30% rounded corners

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val idx = col * rows + row
                val level = if (idx < data.size) data[idx].coerceIn(0, 4) else 0
                paint.color = levelColors[level]
                val left = col * (cellSize + gapPx)
                val top  = row * (cellSize + gapPx)
                rect.set(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }
}
