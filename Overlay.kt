package pt.enquadra

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Grelha de terços + moldura amarela; o dedo desenha a moldura ou toca para focar. */
class Overlay(c: Context) : View(c) {
    var frame: RectF? = null
    var onStart: (() -> Unit)? = null
    var onEnd: ((RectF?, Float, Float) -> Unit)? = null
    private val pg = Paint().apply { color = Color.parseColor("#55FFFFFF"); strokeWidth = 2f; style = Paint.Style.STROKE }
    private val pf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E7C43A"); strokeWidth = 5f; style = Paint.Style.STROKE }
    private var x0 = 0f
    private var y0 = 0f
    private var moved = false

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..2) {
            cv.drawLine(w * i / 3, 0f, w * i / 3, h, pg)
            cv.drawLine(0f, h * i / 3, w, h * i / 3, pg)
        }
        val f = frame ?: return
        cv.drawRect(f.left * w, f.top * h, f.right * w, f.bottom * h, pf)
        val cx = (f.left + f.right) / 2 * w
        val cy = (f.top + f.bottom) / 2 * h
        cv.drawLine(cx - 20, cy, cx + 20, cy, pf)
        cv.drawLine(cx, cy - 20, cx, cy + 20, pf)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val x = (e.x / w).coerceIn(0f, 1f)
        val y = (e.y / h).coerceIn(0f, 1f)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { x0 = x; y0 = y; moved = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(x - x0) > .04f || abs(y - y0) > .04f) {
                    if (!moved) { moved = true; onStart?.invoke() }
                    frame = RectF(min(x0, x), min(y0, y), max(x0, x), max(y0, y))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val f = frame
                if (moved && f != null && f.width() > .08f && f.height() > .08f) onEnd?.invoke(f, x, y)
                else onEnd?.invoke(null, x, y)
            }
        }
        return true
    }
}
