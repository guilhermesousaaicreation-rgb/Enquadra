package pt.enquadra

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.*

/** Procura o melhor enquadramento e segue o motivo (análise em baixa resolução). */
class Guide {
    val W = 64
    var H = 85
    var tpl: FloatArray? = null
    private var tw = 0
    private var th = 0

    fun lum(b: Bitmap): Pair<FloatArray, FloatArray> {
        H = max(8, (W * b.height / b.width.toFloat()).roundToInt())
        val mid = Bitmap.createScaledBitmap(b, 256, max(8, (256 * b.height / b.width.toFloat()).roundToInt()), true)
        val s = Bitmap.createScaledBitmap(mid, W, H, true)
        val px = IntArray(W * H)
        s.getPixels(px, 0, W, 0, 0, W, H)
        val L = FloatArray(W * H)
        val S = FloatArray(W * H)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 255
            val g = (c shr 8) and 255
            val bl = c and 255
            L[i] = .3f * r + .59f * g + .11f * bl
            S[i] = (max(r, max(g, bl)) - min(r, min(g, bl))).toFloat()
        }
        return Pair(L, S)
    }

    fun scan(L: FloatArray, S: FloatArray, prev: RectF?): RectF {
        val sal = FloatArray(W * H)
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            val i = y * W + x
            sal[i] = hypot(L[i + 1] - L[i - 1], L[i + W] - L[i - W]) + S[i] * .6f
        }
        val ww = (W * .55f).roundToInt()
        val wh = (H * .55f).roundToInt()
        var best = -1f
        var bx = 0
        var by = 0
        var y = 0
        while (y <= H - wh) {
            var x = 0
            while (x <= W - ww) {
                var s = 0f
                var j = 0
                while (j < wh) {
                    var i = 0
                    while (i < ww) { s += sal[(y + j) * W + x + i]; i += 2 }
                    j += 2
                }
                val cx = (x + ww / 2f) / W - .5f
                val cy = (y + wh / 2f) / H - .5f
                s *= 1f - (cx * cx + cy * cy)
                if (s > best) { best = s; bx = x; by = y }
                x += 2
            }
            y += 2
        }
        val n = RectF(bx / W.toFloat(), by / H.toFloat(), (bx + ww) / W.toFloat(), (by + wh) / H.toFloat())
        if (prev == null) return n
        fun lp(a: Float, b: Float) = a + (b - a) * .4f
        return RectF(lp(prev.left, n.left), lp(prev.top, n.top), lp(prev.right, n.right), lp(prev.bottom, n.bottom))
    }

    fun setTpl(L: FloatArray, f: RectF) {
        val x = (f.left * W).roundToInt()
        val y = (f.top * H).roundToInt()
        tw = max(4, (f.width() * W).roundToInt())
        th = max(4, (f.height() * H).roundToInt())
        val p = FloatArray(tw * th)
        for (j in 0 until th) for (i in 0 until tw) {
            p[j * tw + i] = L[min(H - 1, y + j) * W + min(W - 1, x + i)]
        }
        tpl = p
    }

    fun track(L: FloatArray, f: RectF): RectF {
        val t = tpl ?: return f
        val px = (f.left * W).roundToInt()
        val py = (f.top * H).roundToInt()
        var best = Float.MAX_VALUE
        var bx = px
        var by = py
        for (y in max(0, py - 12)..min(H - th, py + 12)) for (x in max(0, px - 12)..min(W - tw, px + 12)) {
            var s = 0f
            var j = 0
            while (j < th) {
                var i = 0
                while (i < tw) { s += abs(L[(y + j) * W + x + i] - t[j * tw + i]); i += 2 }
                j += 2
            }
            if (s < best) { best = s; bx = x; by = y }
        }
        val l = bx / W.toFloat()
        val tp = by / H.toFloat()
        return RectF(l, tp, l + f.width(), tp + f.height())
    }
}
