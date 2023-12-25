package com.example.flutter_arcore

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class DrawView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val boundaryPaint: Paint = Paint()
    private val textPaint: Paint = Paint()

    private var boundingRect: Rect = Rect()
    private var labelText: String = ""
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    init {
        boundaryPaint.color = Color.YELLOW
        boundaryPaint.strokeWidth = 10f
        boundaryPaint.style = Paint.Style.STROKE

        textPaint.color = Color.YELLOW
        textPaint.textSize = 32f
        textPaint.style = Paint.Style.FILL
    }

    fun setData(rect: Rect, text: String, imageWidth: Int, imageHeight: Int) {
        boundingRect = rect
        labelText = text
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun drawBoundingBox(boundingBox: Rect) {
        // Draw the bounding box using boundaryPaint
        // This method could be called separately to draw a bounding box without changing other properties
        // For example, you might call it from another part of your code based on some condition.
        // You can customize the drawing logic here.
        // Example: draw a bounding box around the provided Rect
        boundingRect = boundingBox
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate the top offset to center the canvas
        val topOffset = (height - imageHeight) / 2f

        // Draw the text at the center of the bounding rectangle
        canvas.drawText(labelText, boundingRect.centerX().toFloat(), boundingRect.centerY().toFloat() + topOffset, textPaint)

        // Draw the bounding rectangle with the adjusted top offset
        canvas.drawRect(
            boundingRect.left.toFloat(),
            boundingRect.top.toFloat() + topOffset,
            boundingRect.right.toFloat(),
            boundingRect.bottom.toFloat() + topOffset,
            boundaryPaint
        )
    }
}
