package pt.enquadra

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val amber = Color.parseColor("#E7C43A")
    private lateinit var root: FrameLayout
    private lateinit var camScr: LinearLayout
    private lateinit var edScr: LinearLayout
    private lateinit var wrap: FrameLayout
    private lateinit var pv: PreviewView
    private lateinit var ov: Overlay
    private lateinit var hintTv: TextView
    private lateinit var shBtn: View
    private lateinit var scanBtn: Button
    private lateinit var pro: LinearLayout
    private lateinit var zoomRow: LinearLayout
    private lateinit var capture: ImageCapture
    private var cam: androidx.camera.core.Camera? = null
    private var ratio = 3f / 4f
    private var autoMode = true
    private var dragging = false
    private var torchOn = false
    private var zoomNow = 1f
    private var frame: RectF? = null
    private val guide = Guide()
    @Volatile private var lastIso = 0
    @Volatile private var lastEt = 0L

    // editor
    private lateinit var iv: ImageView
    private lateinit var tabsRow: LinearLayout
    private lateinit var ctl: ScrollView
    private var srcBmp: Bitmap? = null
    private var pb: Bitmap? = null
    private var tb: Bitmap? = null
    private var seed = 1
    private var cur = P()
    private var cap = Cap()
    private var lk = 1
    private var ver = 0
    private val worker = Executors.newSingleThreadExecutor()
    private val h = Handler(Looper.getMainLooper())

    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCam() else toast("Sem permissão da câmara. Podes importar fotos da galeria.")
    }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.fitsSystemWindows = true
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)
        buildCam()
        buildEd()
        show(0)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCam()
        else perm.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        worker.shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (edScr.visibility == View.VISIBLE) show(0) else super.onBackPressed()
    }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun tv(t: String, sz: Float = 14f) = TextView(this).apply { text = t; textSize = sz; setTextColor(Color.WHITE) }
    private fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat(); setColor(fill); setStroke(dp(1), stroke)
    }
    private fun btn(t: String, f: () -> Unit): Button = Button(this).apply {
        text = t; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        background = pill(Color.TRANSPARENT, Color.parseColor("#666666"))
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(14), dp(4), dp(14), dp(4))
        setOnClickListener { f() }
    }
    private fun show(n: Int) {
        camScr.visibility = if (n == 0) View.VISIBLE else View.GONE
        edScr.visibility = if (n == 1) View.VISIBLE else View.GONE
    }
    private fun slider(label: String, mx: Int, init: Int, fmt: (Int) -> String, on: (Int) -> Unit): View {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val l = tv(label, 13f)
        val vt = tv(fmt(init), 13f)
        val sb = SeekBar(this)
        sb.max = mx
        sb.progress = init
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { vt.text = fmt(p); on(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(l, LinearLayout.LayoutParams(dp(92), -2))
        row.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(vt, LinearLayout.LayoutParams(dp(54), -2))
        return row
    }

    // ---------- ecrã da câmara ----------
    private fun buildCam() {
        camScr = LinearLayout(this)
        camScr.orientation = LinearLayout.VERTICAL
        pro = LinearLayout(this)
        pro.orientation = LinearLayout.VERTICAL
        pro.visibility = View.GONE
        pro.setPadding(dp(16), dp(4), dp(16), dp(4))
        zoomRow = LinearLayout(this)
        zoomRow.gravity = Gravity.CENTER
        hintTv = tv("", 13f)
        hintTv.setTextColor(amber)
        hintTv.gravity = Gravity.CENTER
        hintTv.setPadding(dp(8), dp(8), dp(8), dp(8))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(6), dp(8), dp(6))
        for ((n, r) in listOf("3:4" to 3f / 4f, "4:5" to 4f / 5f, "1:1" to 1f, "9:16" to 9f / 16f)) {
            val lp = LinearLayout.LayoutParams(-2, dp(36))
            lp.marginEnd = dp(6)
            top.addView(btn(n) { ratio = r; frame = null; ov.frame = null; guide.tpl = null; autoMode = true; updateScanBtn(); layoutPreview() }, lp)
        }
        top.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val lpl = LinearLayout.LayoutParams(-2, dp(36))
        lpl.marginEnd = dp(6)
        top.addView(btn("Luz") { torchOn = !torchOn; cam?.cameraControl?.enableTorch(torchOn) }, lpl)
        top.addView(btn("Manual") { pro.visibility = if (pro.visibility == View.VISIBLE) View.GONE else View.VISIBLE }, LinearLayout.LayoutParams(-2, dp(36)))

        wrap = FrameLayout(this)
        pv = PreviewView(this)
        pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
        ov = Overlay(this)
        wrap.addView(pv, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        wrap.addView(ov, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        wrap.viewTreeObserver.addOnGlobalLayoutListener { layoutPreview() }
        ov.onStart = { dragging = true; autoMode = false; guide.tpl = null; updateScanBtn() }
        ov.onEnd = { r, x, y ->
            dragging = false
            if (r == null) {
                frame = null; ov.frame = null; guide.tpl = null; autoMode = true
                focus(x, y)
            } else {
                frame = r
                pv.bitmap?.let { val (l, _) = guide.lum(it); guide.setTpl(l, r) }
            }
            updateScanBtn()
            ov.invalidate()
        }

        shBtn = View(this)
        shBtn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(4), Color.WHITE) }
        shBtn.setOnClickListener { shoot() }
        scanBtn = btn("Auto") { autoMode = true; frame = null; ov.frame = null; guide.tpl = null; updateScanBtn() }
        val bot = LinearLayout(this)
        bot.gravity = Gravity.CENTER_VERTICAL
        bot.setPadding(dp(20), dp(8), dp(20), dp(16))
        val lg = LinearLayout.LayoutParams(0, dp(40), 1f)
        lg.marginEnd = dp(14)
        bot.addView(btn("Galeria") { picker.launch("image/*") }, lg)
        bot.addView(shBtn, LinearLayout.LayoutParams(dp(72), dp(72)))
        val ls = LinearLayout.LayoutParams(0, dp(40), 1f)
        ls.marginStart = dp(14)
        bot.addView(scanBtn, ls)

        camScr.addView(top, LinearLayout.LayoutParams(-1, -2))
        camScr.addView(wrap, LinearLayout.LayoutParams(-1, 0, 1f))
        camScr.addView(hintTv, LinearLayout.LayoutParams(-1, -2))
        camScr.addView(pro, LinearLayout.LayoutParams(-1, -2))
        camScr.addView(zoomRow, LinearLayout.LayoutParams(-1, -2))
        camScr.addView(bot, LinearLayout.LayoutParams(-1, -2))
        root.addView(camScr, FrameLayout.LayoutParams(-1, -1))
    }

    private fun layoutPreview() {
        val W = wrap.width
        val H = wrap.height
        if (W == 0 || H == 0) return
        var w = W
        var hh = (W / ratio).toInt()
        if (hh > H) { hh = H; w = (H * ratio).toInt() }
        for (v in listOf<View>(pv, ov)) {
            val lp = v.layoutParams as FrameLayout.LayoutParams
            if (lp.width != w || lp.height != hh) {
                lp.width = w; lp.height = hh; lp.gravity = Gravity.CENTER
                v.layoutParams = lp
            }
        }
    }

    private fun updateScanBtn() { scanBtn.alpha = if (autoMode) 1f else .5f }

    private fun glow(ok: Boolean) {
        val c = if (ok) amber else Color.WHITE
        (shBtn.background as GradientDrawable).apply { setColor(c); setStroke(dp(4), c) }
    }

    private fun updateHint() {
        val f = frame
        if (f == null) { hintTv.text = "Toca em Auto ou desenha a tua moldura no ecrã"; glow(false); return }
        val cx = (f.left + f.right) / 2 - .5f
        val cy = (f.top + f.bottom) / 2 - .5f
        val t = ArrayList<String>()
        if (cx > .07f) t.add("para a direita") else if (cx < -.07f) t.add("para a esquerda")
        if (cy > .07f) t.add("para baixo") else if (cy < -.07f) t.add("para cima")
        var z = ""
        if (!autoMode) {
            if (f.width() < .3f) z = " · aproxima ou usa zoom" else if (f.width() > .92f) z = " · afasta-te um pouco"
        }
        val ok = t.isEmpty()
        hintTv.text = if (ok) "Alinhado. Dispara$z" else "Move o telemóvel " + t.joinToString(" e ") + z
        glow(ok)
    }

    private val tick = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { }
            h.postDelayed(this, 250)
        }
    }

    private fun step() {
        if (camScr.visibility != View.VISIBLE || dragging || cam == null) return
        val bmp = pv.bitmap ?: return
        val (l, s) = guide.lum(bmp)
        if (autoMode) frame = guide.scan(l, s, frame)
        else {
            val f = frame
            if (f != null && guide.tpl != null) frame = guide.track(l, f)
        }
        ov.frame = frame
        ov.invalidate()
        updateHint()
    }

    private fun focus(x: Float, y: Float) {
        val c = cam ?: return
        try {
            val pt = pv.meteringPointFactory.createPoint(x * pv.width, y * pv.height)
            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
        } catch (e: Exception) { }
    }

    private fun startCam() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val prov = f.get()
            val pbd = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            Camera2Interop.Extender<Preview>(pbd).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { lastIso = it }
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { lastEt = it }
                }
            })
            val prev = pbd.build()
            prev.setSurfaceProvider(pv.surfaceProvider)
            capture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            prov.unbindAll()
            cam = prov.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, capture)
            setupControls()
            h.post(tick)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupControls() {
        val c = cam ?: return
        pro.removeAllViews()
        zoomRow.removeAllViews()
        val zs = c.cameraInfo.zoomState.value
        if (zs != null) {
            for (q in listOf(.5f, 1f, 2f, 3f, 5f)) {
                if (q >= zs.minZoomRatio - .01f && q <= zs.maxZoomRatio) {
                    val lab = if (q == q.toInt().toFloat()) q.toInt().toString() else q.toString()
                    val lp = LinearLayout.LayoutParams(-2, dp(34))
                    lp.marginEnd = dp(8)
                    zoomRow.addView(btn("$lab×") { c.cameraControl.setZoomRatio(q); zoomNow = q }, lp)
                }
            }
        }
        val es = c.cameraInfo.exposureCompensationState
        if (es.isExposureCompensationSupported) {
            val r = es.exposureCompensationRange
            val step = es.exposureCompensationStep.toFloat()
            pro.addView(slider("Exposição", r.upper - r.lower, -r.lower, { String.format("%.1f", (it + r.lower) * step) }) {
                c.cameraControl.setExposureCompensationIndex(it + r.lower)
            })
        }
        try {
            val ci = Camera2CameraInfo.from(c.cameraInfo)
            val ir = ci.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val er = ci.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (ir != null && er != null) {
                val isoMin = max(ir.lower, 50)
                val isoMax = min(ir.upper, 3200)
                val etMin = max(er.lower, 125000L)
                val etMax = min(er.upper, 500000000L)
                var curIso = if (lastIso > 0) lastIso.coerceIn(isoMin, isoMax) else 100.coerceIn(isoMin, isoMax)
                var curEt = if (lastEt > 0) lastEt.coerceIn(etMin, etMax) else 10000000L.coerceIn(etMin, etMax)
                fun manual() {
                    Camera2CameraControl.from(c.cameraControl).setCaptureRequestOptions(
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, curIso)
                            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, curEt)
                            .build()
                    )
                }
                val li = ln(isoMin.toDouble())
                val ri = ln(isoMax.toDouble())
                val le = ln(etMin.toDouble())
                val re = ln(etMax.toDouble())
                val p0 = (100 * (ln(curIso.toDouble()) - li) / (ri - li)).roundToInt()
                val p1 = (100 * (ln(curEt.toDouble()) - le) / (re - le)).roundToInt()
                pro.addView(slider("ISO", 100, p0, { exp(li + it / 100.0 * (ri - li)).roundToInt().toString() }) {
                    curIso = exp(li + it / 100.0 * (ri - li)).roundToInt().coerceIn(isoMin, isoMax); manual()
                })
                pro.addView(slider("Obturador", 100, p1, { "1/" + (1e9 / exp(le + it / 100.0 * (re - le))).roundToInt() }) {
                    curEt = exp(le + it / 100.0 * (re - le)).toLong().coerceIn(etMin, etMax); manual()
                })
                pro.addView(btn("Automático") {
                    Camera2CameraControl.from(c.cameraControl).clearCaptureRequestOptions()
                    setupControls()
                })
            }
        } catch (e: Exception) { }
    }

    private fun cropRatio(b: Bitmap, r: Float): Bitmap {
        val w = b.width
        val hh = b.height
        return if (w / hh.toFloat() > r) {
            val nw = (hh * r).toInt()
            Bitmap.createBitmap(b, (w - nw) / 2, 0, nw, hh)
        } else {
            val nh = (w / r).toInt()
            Bitmap.createBitmap(b, 0, (hh - nh) / 2, w, nh)
        }
    }

    private fun shoot() {
        shBtn.isEnabled = false
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rot = image.imageInfo.rotationDegrees
                    var bmp = image.toBitmap()
                    image.close()
                    val m = Matrix()
                    m.postRotate(rot.toFloat())
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    bmp = Film.scaled(cropRatio(bmp, ratio), 3200)
                    cap = Cap(zoomNow, lastIso, lastEt, Date())
                    openEditor(bmp)
                } catch (e: Exception) {
                    toast("Erro ao processar a fotografia")
                }
                shBtn.isEnabled = true
            }
            override fun onError(exception: ImageCaptureException) {
                toast("Não foi possível fotografar")
                shBtn.isEnabled = true
            }
        })
    }

    private fun importUri(uri: Uri) {
        try {
            val s = ImageDecoder.createSource(contentResolver, uri)
            val bm = ImageDecoder.decodeBitmap(s) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val mx = max(info.size.width, info.size.height)
                if (mx > 3200) dec.setTargetSampleSize(mx / 3200 + 1)
            }
            cap = Cap(date = Date())
            openEditor(bm)
        } catch (e: Exception) {
            toast("Não foi possível abrir a imagem")
        }
    }

    // ---------- editor ----------
    private fun buildEd() {
        edScr = LinearLayout(this)
        edScr.orientation = LinearLayout.VERTICAL
        edScr.setBackgroundColor(Color.parseColor("#141311"))
        iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(12), dp(12), dp(12), dp(12))
        edScr.addView(iv, LinearLayout.LayoutParams(-1, 0, 1f))
        tabsRow = LinearLayout(this)
        tabsRow.gravity = Gravity.CENTER
        listOf("Looks", "Ajustes", "Efeitos", "Molduras").forEachIndexed { i, n ->
            val lp = LinearLayout.LayoutParams(-2, dp(36))
            lp.marginEnd = dp(6)
            tabsRow.addView(btn(n) { showTab(i) }, lp)
        }
        edScr.addView(tabsRow, LinearLayout.LayoutParams(-1, -2))
        ctl = ScrollView(this)
        edScr.addView(ctl, LinearLayout.LayoutParams(-1, dp(170)))
        val bar = LinearLayout(this)
        bar.setPadding(dp(16), dp(8), dp(16), dp(14))
        bar.addView(btn("Voltar") { show(0) })
        bar.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val sv = btn("Guardar") { save() }
        sv.background = pill(Color.WHITE, Color.WHITE)
        sv.setTextColor(Color.BLACK)
        bar.addView(sv)
        edScr.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(edScr, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openEditor(b: Bitmap) {
        srcBmp = b
        pb = Film.scaled(b, 900)
        tb = Film.scaled(b, 110)
        seed = Random.nextInt(1, Int.MAX_VALUE)
        lk = 1
        cur = LOOKS[1].toP(null)
        show(1)
        showTab(0)
        redraw()
    }

    private fun showTab(n: Int) {
        for (i in 0 until tabsRow.childCount) tabsRow.getChildAt(i).alpha = if (i == n) 1f else .5f
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(6), dp(14), dp(6))
        when (n) {
            0 -> {
                val hs = HorizontalScrollView(this)
                val row = LinearLayout(this)
                val thumb = tb
                LOOKS.forEachIndexed { i, lo ->
                    val col = LinearLayout(this)
                    col.orientation = LinearLayout.VERTICAL
                    col.gravity = Gravity.CENTER
                    col.setPadding(dp(5), dp(5), dp(5), dp(5))
                    if (i == lk) col.background = pill(Color.TRANSPARENT, Color.WHITE)
                    val im = ImageView(this)
                    im.scaleType = ImageView.ScaleType.CENTER_CROP
                    if (thumb != null) im.setImageBitmap(Film.render(thumb, lo.toP(null), seed))
                    col.addView(im, LinearLayout.LayoutParams(dp(66), dp(88)))
                    col.addView(tv(lo.name, 12f))
                    col.setOnClickListener { lk = i; cur = lo.toP(cur); showTab(0); redraw() }
                    row.addView(col)
                }
                hs.addView(row)
                box.addView(hs)
            }
            1, 2 -> {
                for ((k, lab) in if (n == 1) ADJ else FX) {
                    val off = if (n == 1 && k != "fade") 100 else 0
                    box.addView(slider(lab, if (off > 0) 200 else 100, cur.g(k) + off, { (it - off).toString() }) {
                        cur.a[k] = it - off
                        redraw()
                    })
                }
            }
            else -> {
                val hs = HorizontalScrollView(this)
                val row = LinearLayout(this)
                for ((k, lab) in FR) {
                    val lp = LinearLayout.LayoutParams(-2, dp(36))
                    lp.marginEnd = dp(8)
                    val b = btn(lab) { cur.frame = k; showTab(3); redraw() }
                    if (cur.frame == k) { b.background = pill(Color.WHITE, Color.WHITE); b.setTextColor(Color.BLACK) }
                    row.addView(b, lp)
                }
                hs.addView(row)
                box.addView(hs)
                val cb = CheckBox(this)
                cb.text = "Carimbo de data"
                cb.setTextColor(Color.WHITE)
                cb.isChecked = cur.date
                cb.setOnCheckedChangeListener { _, c -> cur.date = c; redraw() }
                box.addView(cb)
            }
        }
        ctl.removeAllViews()
        ctl.addView(box)
    }

    private fun redraw() {
        val base = pb ?: return
        val v = ++ver
        val pc = cur.copy()
        val cp = cap
        val sd = seed
        worker.execute {
            if (v != ver) return@execute
            val o = Film.compose(Film.render(base, pc, sd), pc, cp)
            runOnUiThread { if (v == ver) iv.setImageBitmap(o) }
        }
    }

    private fun save() {
        val s = srcBmp ?: return
        val pc = cur.copy()
        val cp = cap
        val sd = seed
        toast("A guardar…")
        worker.execute {
            try {
                val o = Film.compose(Film.render(Film.scaled(s, 2600), pc, sd), pc, cp)
                val cv = ContentValues()
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, "Enquadra_" + System.currentTimeMillis() + ".jpg")
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Enquadra")
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(uri)!!.use { o.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                runOnUiThread { toast("Guardada na galeria (pasta Enquadra)") }
            } catch (e: Exception) {
                runOnUiThread { toast("Não foi possível guardar") }
            }
        }
    }
}
