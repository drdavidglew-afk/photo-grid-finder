package com.photogridfinder.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Header background: deep green with Ordnance Survey style grid lines (every 5th line bolder). */
class GridHeaderView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val bg = Paint()
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1AFFFFFF
        strokeWidth = 1f * density
    }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x38FFFFFF
        strokeWidth = 1.5f * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            context.getColor(R.color.header_top),
            context.getColor(R.color.header_bottom),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        val step = 18f * density
        var i = 0
        var x = step * 0.5f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, if (i % 5 == 0) bold else thin)
            x += step
            i++
        }
        i = 0
        var y = step * 0.5f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, if (i % 5 == 0) bold else thin)
            y += step
            i++
        }
    }
}
