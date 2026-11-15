package pt.enquadra

import android.graphics.*
import java.util.Calendar
import java.util.Date
import kotlin.math.*
import kotlin.random.Random

class P(
    val a: HashMap<String, Int> = HashMap(),
    var sh: IntArray = intArrayOf(0, 0, 0),
    var hi: IntArray = intArrayOf(0, 0, 0),
    var frame: String = "plain",
    var date: Boolean = false
) {
    fun g(k: String): Int = a[k] ?: 0
    fun copy(): P = P(HashMap(a), sh, hi, frame, date)
}

class Look(val name: String, val a: Map<String, Int>, val sh: IntArray = intArrayOf(0, 0, 0), val hi: IntArray = intArrayOf(0, 0, 0)) {
    fun toP(old: P?): P = P(HashMap(a), sh, hi, old?.frame ?: "plain", old?.date ?: false)
}

class Cap(val zoom: Float = 1f, val iso: Int = 0, val et: Long = 0L, val date: Date = Date())

val LOOKS = listOf(
    Look("Original", mapOf()),
    Look("Mel", mapOf("temp" to 28, "fade" to 10, "contrast" to 8, "sat" to 8, "grain" to 22, "halation" to 30, "vig" to 15), intArrayOf(10, 4, 0), intArrayOf(8, 4, -4)),
    Look("Sienna", mapOf("temp" to 38, "tint" to 6, "contrast" to 16, "sat" to -8, "fade" to 16, "grain" to 30, "halation" to 40, "vig" to 28, "leak" to 18), intArrayOf(14, 4, -2), intArrayOf(10, 2, -6)),
    Look("Arquivo", mapOf("temp" to -4, "sat" to -28, "contrast" to 6, "fade" to 24, "grain" to 36, "dust" to 30), intArrayOf(-2, 6, 8), intArrayOf(8, 6, 0)),
    Look("Cobalto", mapOf("temp" to -22, "tint" to -4, "sat" to 10, "contrast" to 14, "grain" to 20, "glow" to 20), intArrayOf(-4, 6, 16), intArrayOf(6, 6, 2)),
    Look("Verde", mapOf("temp" to -8, "tint" to -14, "sat" to 14, "contrast" to 10, "fade" to 8, "grain" to 26), intArrayOf(-4, 8, 2), intArrayOf(4, 6, -4)),
    Look("Ouro", mapOf("temp" to 20, "sat" to 20, "contrast" to 16, "grain" to 24, "halation" to 20, "vig" to 20), intArrayOf(0, 0, 0), intArrayOf(10, 4, -4)),
    Look("P&B", mapOf("sat" to -100, "contrast" to 24, "grain" to 44, "vig" to 30, "fade" to 6))
)
val ADJ = listOf("exposure" to "Exposição", "contrast" to "Contraste", "highlights" to "Realces", "shadows" to "Sombras", "temp" to "Temperatura", "tint" to "Tonalidade", "fade" to "Desvanecer", "sat" to "Saturação")
val FX = listOf("grain" to "Grão", "halation" to "Halation", "glow" to "Brilho", "leak" to "Fugas de luz", "dust" to "Pó e riscos", "vig" to "Vinheta")
val FR = listOf("plain" to "Simples", "white" to "Branca", "paper" to "Papel", "black" to "Preta", "negative" to "Negativo", "specs" to "Specs")

object Film {
    fun scaled(b: Bitmap, L: Int): Bitmap {
        var t = b
        while (max(t.width, t.height) > L * 2) t = Bitmap.createScaledBitmap(t, t.width / 2, t.height / 2, true)
        val k = L.toFloat() / max(t.width, t.height)
        if (k >= 1f) return t
        return Bitmap.createScaledBitmap(t, max(1, (t.width * k).roundToInt()), max(1, (t.height * k).roundToInt()), true)
    }

    private fun soft(src: Bitmap, steps: Int): Bitmap {
        var t = src
        repeat(steps) { t = Bitmap.createScaledBitmap(t, max(1, t.width / 2), max(1, t.height / 2), true) }
        return Bitmap.createScaledBitmap(t, src.width, src.height, true)
    }

    fun render(b: Bitmap, p: P, seed: Int): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var s = seed.toLong() and 0xffffffffL
        fun rnd(): Float {
            s = (s * 1664525L + 1013904223L) and 0xffffffffL
            return (s / 4294967296.0).toFloat()
        }
        val ex = 2.0.pow(p.g("exposure") / 50.0).toFloat()
        val cc = p.g("contrast") * 2.55f
        val cf = 259f * (cc + 255f) / (255f * (259f - cc))
        val gr = p.g("grain") * .9f
        val sa = 1f + p.g("sat") / 100f
        val fd = p.g("fade").toFloat()
        val temp = p.g("temp").toFloat()
        val tint = p.g("tint").toFloat()
        val shd = p.g("shadows").toFloat()
        val hil = p.g("highlights").toFloat()
        val hal = p.g("halation")
        val hd = if (hal > 0) IntArray(w * h) else null
        val k = 1f - fd / 260f
        val f = fd * .35f
        for (i in px.indices) {
            val c = px[i]
            var r = ((c shr 16) and 255) * ex
            var g = ((c shr 8) and 255) * ex
            var bl = (c and 255) * ex
            r += temp * .45f + tint * .12f
            g -= tint * .3f
            bl += tint * .12f - temp * .45f
            val l = ((.299f * r + .587f * g + .114f * bl) / 255f).coerceIn(0f, 1f)
            val a = shd * .6f * (1 - l) * (1 - l) + hil * .6f * l * l
            r += a; g += a; bl += a
            r = cf * (r - 128f) + 128f
            g = cf * (g - 128f) + 128f
            bl = cf * (bl - 128f) + 128f
            r = r * k + f; g = g * k + f; bl = bl * k + f
            val y = .299f * r + .587f * g + .114f * bl
            r = y + (r - y) * sa
            g = y + (g - y) * sa
            bl = y + (bl - y) * sa
            r += p.sh[0] * (1 - l) + p.hi[0] * l
            g += p.sh[1] * (1 - l) + p.hi[1] * l
            bl += p.sh[2] * (1 - l) + p.hi[2] * l
            val n = (rnd() - .5f) * gr * (1.2f - abs(l - .5f))
            val ri = (r + n).toInt().coerceIn(0, 255)
            val gi = (g + n).toInt().coerceIn(0, 255)
            val bi = (bl + n).toInt().coerceIn(0, 255)
            px[i] = (0xff shl 24) or (ri shl 16) or (gi shl 8) or bi
            if (hd != null) {
                val q = max(0f, (y / 255f - .72f) / .28f)
                hd[i] = (0xff shl 24) or ((255 * q).toInt().coerceIn(0, 255) shl 16) or ((80 * q).toInt().coerceIn(0, 255) shl 8) or (25 * q).toInt().coerceIn(0, 255)
            }
        }
        val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val scr = Paint(Paint.FILTER_BITMAP_FLAG)
        scr.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        if (hd != null) {
            val hb = Bitmap.createBitmap(hd, w, h, Bitmap.Config.ARGB_8888)
            scr.alpha = (255 * min(1f, hal / 60f)).toInt()
            cv.drawBitmap(soft(hb, 4), 0f, 0f, scr)
        }
        val gl = p.g("glow")
        if (gl > 0) {
            scr.alpha = (255 * min(1f, gl / 150f)).toInt()
            cv.drawBitmap(soft(out.copy(Bitmap.Config.ARGB_8888, false), 3), 0f, 0f, scr)
        }
        val lkv = p.g("leak")
        if (lkv > 0) {
            val gx = if ((seed and 1) == 1) w.toFloat() else 0f
            val gy = h * (((seed shr 2) and 3) / 4f + .15f)
            val rr = max(w, h) * .7f
            val al = lkv / 100f
            val pt = Paint()
            pt.shader = RadialGradient(
                gx, gy, rr,
                intArrayOf(Color.argb((255 * al * .8f).toInt(), 255, 110, 40), Color.argb((255 * al * .3f).toInt(), 255, 60, 90), Color.argb(0, 255, 60, 90)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
            )
            pt.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pt)
        }
        val du = p.g("dust")
        if (du > 0) {
            val n = (du.toLong() * w * h / 200000L).toInt()
            val pt = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until n) {
                pt.color = if (rnd() > .3f) Color.argb(140, 255, 255, 255) else Color.argb(100, 0, 0, 0)
                cv.drawCircle(rnd() * w, rnd() * h, rnd() * 1.4f * (w / 700f) + .3f, pt)
            }
            pt.color = Color.argb(65, 255, 255, 255)
            pt.strokeWidth = max(.8f, w / 900f)
            for (i in 0 until n / 12) {
                val x = rnd() * w
                val y = rnd() * h
                cv.drawLine(x, y, x + (rnd() - .5f) * w * .02f, y + rnd() * h * .08f, pt)
            }
        }
        val vg = p.g("vig")
        if (vg > 0) {
            val rr = hypot(w.toFloat(), h.toFloat()) / 2f
            val f0 = (min(w, h) * .3f) / rr
            val pt = Paint()
            pt.shader = RadialGradient(
                w / 2f, h / 2f, rr,
                intArrayOf(Color.TRANSPARENT, Color.argb((255 * vg / 100f * .85f).toInt(), 0, 0, 0)),
                floatArrayOf(f0, 1f), Shader.TileMode.CLAMP
            )
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pt)
        }
        return out
    }

    fun compose(c: Bitmap, p: P, cap: Cap): Bitmap {
        val w = c.width
        val h = c.height
        val m = min(w, h).toFloat()
        val cal = Calendar.getInstance()
        cal.time = cap.date
        val yy = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH) + 1
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        fun p2(n: Int) = n.toString().padStart(2, '0')
        if (p.date) {
            val cv = Canvas(c)
            val pt = Paint(Paint.ANTI_ALIAS_FLAG)
            pt.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            pt.textSize = m * .04f
            pt.textAlign = Paint.Align.RIGHT
            pt.color = Color.parseColor("#FF8A1F")
            pt.setShadowLayer(m * .012f, 0f, 0f, Color.parseColor("#FF6A00"))
            cv.drawText("’" + (yy % 100).toString().padStart(2, '0') + " " + p2(mo) + " " + p2(dd), w - m * .04f, h - m * .04f, pt)
        }
        val cfg: FloatArray
        val bg: Int
        when (p.frame) {
            "white" -> { cfg = floatArrayOf(.05f, .05f, .05f, .05f); bg = Color.parseColor("#FBFAF6") }
            "paper" -> { cfg = floatArrayOf(.06f, .06f, .06f, .06f); bg = Color.parseColor("#EDE4CF") }
            "black" -> { cfg = floatArrayOf(.05f, .05f, .05f, .05f); bg = Color.parseColor("#0A0A0A") }
            "negative" -> { cfg = floatArrayOf(.13f, .05f, .13f, .05f); bg = Color.parseColor("#14110E") }
            "specs" -> { cfg = floatArrayOf(.05f, .05f, .16f, .05f); bg = Color.parseColor("#FBFAF6") }
            else -> return c
        }
        val t = (cfg[0] * m).roundToInt()
        val l = (cfg[1] * m).roundToInt()
        val b = (cfg[2] * m).roundToInt()
        val r = (cfg[3] * m).roundToInt()
        val ow = w + l + r
        val oh = h + t + b
        val o = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(o)
        cv.drawColor(bg)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        if (p.frame == "paper") {
            val rd = Random(7)
            pt.color = Color.argb(18, 120, 100, 60)
            for (i in 0 until 400) {
                val x = rd.nextFloat() * ow
                val y = rd.nextFloat() * oh
                cv.drawRect(x, y, x + 1f + rd.nextFloat() * 2f, y + 1f + rd.nextFloat() * 2f, pt)
            }
        }
        cv.drawBitmap(c, l.toFloat(), t.toFloat(), null)
        if (p.frame == "negative") {
            pt.color = Color.argb(217, 217, 210, 196)
            val hw = m * .035f
            val hh = m * .022f
            var x = hw
            while (x < ow - hw) {
                cv.drawRoundRect(RectF(x, t * .22f, x + hw, t * .22f + hh), 3f, 3f, pt)
                cv.drawRoundRect(RectF(x, oh - t * .22f - hh, x + hw, oh - t * .22f), 3f, 3f, pt)
                x += hw * 2
            }
            pt.color = Color.parseColor("#E89A2A")
            pt.typeface = Typeface.MONOSPACE
            pt.textSize = m * .03f
            pt.textAlign = Paint.Align.LEFT
            cv.drawText("▸ 14A", l.toFloat(), oh - t * .4f, pt)
        }
        if (p.frame == "specs") {
            val sh = if (cap.et > 0) "1/" + (1e9 / cap.et).roundToInt() else "AUTO"
            val iso = if (cap.iso > 0) cap.iso.toString() else "AUTO"
            pt.color = Color.parseColor("#2A2722")
            pt.typeface = Typeface.MONOSPACE
            pt.textSize = m * .032f
            pt.textAlign = Paint.Align.LEFT
            val yb = h + t + b * .5f
            cv.drawText(String.format("%.1fx  ISO %s  %s", cap.zoom, iso, sh), l.toFloat(), yb, pt)
            pt.textAlign = Paint.Align.RIGHT
            cv.drawText(p2(dd) + "." + p2(mo) + "." + yy, (ow - r).toFloat(), yb, pt)
        }
        return o
    }
}
