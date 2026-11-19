package br.anotaquestoes

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var draw: DrawingView
    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences

    private var bar: LinearLayout? = null
    private var toggleBtn: TextView? = null
    private var preview: WidthPreview? = null
    private val toolButtons = LinkedHashMap<DrawingView.Tool, TextView>()
    private val colorDots = ArrayList<Pair<View, Int>>()

    private var lastUrl: String? = null
    private var autoClear = false
    private var vertical = false
    private var collapsed = false
    private var barX = -1f
    private var barY = -1f

    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val density by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        root = FrameLayout(this)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    if (!isReload && url != lastUrl) {
                        if (autoClear && lastUrl != null) draw.clearAll()
                        lastUrl = url
                    }
                }
            }
        }
        draw = DrawingView(this).apply {
            scrollSource = web
            onTextRequest = { x, y, existing -> askText(x, y, existing) }
        }
        web.setOnScrollChangeListener { _, _, _, _, _ -> draw.invalidate() }
        loadPrefs()

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(web, FrameLayout.LayoutParams(match, match))
        root.addView(draw, FrameLayout.LayoutParams(match, match))
        setContentView(root)
        rebuildBar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        web.loadUrl("https://www.tecconcursos.com.br")
    }

    override fun onPause() {
        savePrefs()
        super.onPause()
    }

    // ------------------------------------------------------------ preferências
    private fun loadPrefs() {
        vertical = prefs.getBoolean("vertical", false)
        collapsed = prefs.getBoolean("collapsed", false)
        barX = prefs.getFloat("barX", -1f)
        barY = prefs.getFloat("barY", -1f)
        autoClear = prefs.getBoolean("autoClear", false)
        draw.penColor = prefs.getInt("penColor", draw.penColor)
        draw.hlColor = prefs.getInt("hlColor", draw.hlColor)
        draw.penW = prefs.getFloat("penW", draw.penW)
        draw.hlW = prefs.getFloat("hlW", draw.hlW)
        draw.eraserW = prefs.getFloat("eraserW", draw.eraserW)
        draw.laserW = prefs.getFloat("laserW", draw.laserW)
        draw.textW = prefs.getFloat("textW", draw.textW)
        draw.pressureOn = prefs.getBoolean("pressure", true)
        draw.palmRejection = prefs.getBoolean("palm", true)
        draw.ruler = prefs.getBoolean("ruler", false)
        draw.fingerDraws = prefs.getBoolean("finger", false)
        val t = DrawingView.Tool.values().getOrNull(prefs.getInt("tool", 0)) ?: DrawingView.Tool.PEN
        draw.selectTool(t)
    }

    private fun savePrefs() {
        bar?.let { barX = it.x; barY = it.y }
        prefs.edit()
            .putBoolean("vertical", vertical).putBoolean("collapsed", collapsed)
            .putFloat("barX", barX).putFloat("barY", barY)
            .putBoolean("autoClear", autoClear)
            .putInt("penColor", draw.penColor).putInt("hlColor", draw.hlColor)
            .putFloat("penW", draw.penW).putFloat("hlW", draw.hlW)
            .putFloat("eraserW", draw.eraserW).putFloat("laserW", draw.laserW).putFloat("textW", draw.textW)
            .putBoolean("pressure", draw.pressureOn).putBoolean("palm", draw.palmRejection)
            .putBoolean("ruler", draw.ruler).putBoolean("finger", draw.fingerDraws)
            .putInt("tool", draw.tool.ordinal)
            .apply()
    }

    // ------------------------------------------------------------ helpers de UI
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = px(r).toFloat()
    }

    private fun label(t: String, size: Float = 20f, minW: Int = 46) = TextView(this).apply {
        text = t; textSize = size; gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        minWidth = px(minW)
        setPadding(px(4), 0, px(4), 0)
        layoutParams = LinearLayout.LayoutParams(WRAP, px(46))
    }

    private fun mark(v: View, on: Boolean, color: String = "#3D5AFE") {
        v.background = if (on) rounded(Color.parseColor(color), 12) else null
    }

    private fun sep(v: Boolean) = View(this).apply {
        setBackgroundColor(Color.parseColor("#55FFFFFF"))
        layoutParams = if (v) LinearLayout.LayoutParams(px(60), px(1)).apply { setMargins(0, px(6), 0, px(6)) }
        else LinearLayout.LayoutParams(px(1), px(28)).apply { setMargins(px(6), 0, px(6), 0) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    /** Botão que repete a ação enquanto está pressionado (para + e −). */
    @SuppressLint("ClickableViewAccessibility")
    private fun repeatable(v: View, action: () -> Unit) {
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() { action(); h.postDelayed(this, 70) }
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { action(); h.postDelayed(r, 380); view.isPressed = true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); view.isPressed = false }
            }
            true
        }
    }

    private fun bump(dir: Int) {
        val v = draw.widthDp + dir * draw.widthStep
        draw.widthDp = v.coerceIn(draw.widthMin, draw.widthMax)
        preview?.invalidate()
    }

    // ------------------------------------------------------------ barra
    private fun rebuildBar() {
        bar?.let { barX = it.x; barY = it.y; root.removeView(it) }
        toolButtons.clear(); colorDots.clear()
        val b = buildBar()
        bar = b
        root.addView(b, FrameLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.TOP or Gravity.START })
        b.post {
            val mx = max(0f, (root.width - b.width).toFloat())
            val my = max(0f, (root.height - b.height).toFloat())
            b.x = (if (barX >= 0f) barX else px(12).toFloat()).coerceIn(0f, mx)
            b.y = (if (barY >= 0f) barY else px(12).toFloat()).coerceIn(0f, my)
        }
        refreshAll()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBar(): LinearLayout {
        val v = vertical
        val bar = LinearLayout(this).apply {
            orientation = if (v) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (v) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            background = rounded(Color.parseColor("#EB212121"), 24)
            elevation = px(8).toFloat()
            setPadding(px(6), px(6), px(6), px(6))
        }

        // alça para arrastar
        val handle = label("⠿", 22f, 30)
        var offX = 0f; var offY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { offX = bar.x - e.rawX; offY = bar.y - e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    bar.x = (e.rawX + offX).coerceIn(0f, max(0f, (root.width - bar.width).toFloat()))
                    bar.y = (e.rawY + offY).coerceIn(0f, max(0f, (root.height - bar.height).toFloat()))
                }
            }
            true
        }

        // gira a barra (vertical <-> horizontal)
        val orient = label(if (v) "↔" else "↕", 18f, 30)
        orient.tooltipText = if (v) "Barra na horizontal" else "Barra na vertical"
        orient.setOnClickListener { vertical = !vertical; rebuildBar() }

        val collapse = label(collapseIcon(v), 14f, 30)

        // liga/desliga escrita
        val toggle = label("", 15f, if (v) 80 else 100)
        if (v) toggle.layoutParams = LinearLayout.LayoutParams(WRAP, px(54))
        toggleBtn = toggle
        toggle.setOnClickListener { draw.drawingEnabled = !draw.drawingEnabled; refreshToggle() }

        val scroll: ViewGroup = if (v) ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        else HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val content = LinearLayout(this).apply {
            orientation = if (v) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (v) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        scroll.addView(content)
        scroll.visibility = if (collapsed) View.GONE else View.VISIBLE

        val sections = ArrayList<List<View>>()

        // 1) ferramentas
        val tools = listOf(
            Triple("✏️", DrawingView.Tool.PEN, "Lápis"),
            Triple("🖍️", DrawingView.Tool.HIGHLIGHTER, "Marcador"),
            Triple("╱", DrawingView.Tool.LINE, "Linha"),
            Triple("↗", DrawingView.Tool.ARROW, "Seta"),
            Triple("▢", DrawingView.Tool.RECT, "Retângulo"),
            Triple("◯", DrawingView.Tool.OVAL, "Círculo"),
            Triple("🗒️", DrawingView.Tool.TEXT, "Post-it (toque num post-it para editar)"),
            Triple("🔴", DrawingView.Tool.LASER, "Laser"),
            Triple("🧽", DrawingView.Tool.ERASER, "Borracha: traço inteiro"),
            Triple("⌫", DrawingView.Tool.ERASER_PART, "Borracha: só onde passar"),
            Triple("✋", DrawingView.Tool.MOVE, "Mover anotações (toque num post-it para editar)")
        )
        sections.add(tools.map { (icon, tool, tip) ->
            label(icon).also { b ->
                b.tooltipText = tip
                b.setOnClickListener { selectTool(tool) }
                toolButtons[tool] = b
            }
        })

        // 2) desfazer / refazer / limpar
        sections.add(listOf(
            label("↩️").apply { tooltipText = "Desfazer"; setOnClickListener { draw.undo() } },
            label("↪️").apply { tooltipText = "Refazer"; setOnClickListener { draw.redo() } },
            label("🗑️").apply { tooltipText = "Limpar tudo (dá para desfazer)"; setOnClickListener { draw.clearAll() } }
        ))

        // 3) espessura: − [amostra] +
        val minus = label("−", 26f, 42).apply { tooltipText = "Mais fino (segure para repetir)" }
        val plus = label("+", 26f, 42).apply { tooltipText = "Mais grosso (segure para repetir)" }
        repeatable(minus) { bump(-1) }
        repeatable(plus) { bump(1) }
        val pv = WidthPreview(this, draw).apply {
            layoutParams = LinearLayout.LayoutParams(px(if (v) 92 else 64), px(46))
        }
        preview = pv
        sections.add(if (v) listOf(minus, plus, pv) else listOf(minus, pv, plus))

        // 4) cores
        val colors = listOf("#111111", "#E53935", "#1E88E5", "#43A047", "#FFEB3B", "#FF9800", "#8E24AA", "#00ACC1")
        sections.add(colors.map { hex ->
            val color = Color.parseColor(hex)
            val dot = View(this)
            colorDots.add(dot to color)
            FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(px(40), px(46))
                addView(dot, FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
                setOnClickListener {
                    if (draw.tool == DrawingView.Tool.HIGHLIGHTER) draw.hlColor = color else draw.penColor = color
                    refreshColors(); pv.invalidate()
                }
            }
        })

        // 5) opções
        sections.add(listOf(
            optionToggle("📏", "Marcador em linha reta (régua)", draw.ruler) { draw.ruler = it },
            optionToggle("🖋️", "Pressão: traço mais fino/grosso conforme aperta a caneta", draw.pressureOn) { draw.pressureOn = it },
            optionToggle("🤚", "Rejeição de palma (ignora a mão apoiada)", draw.palmRejection) { draw.palmRejection = it },
            optionToggle("☝️", "Dedo também desenha", draw.fingerDraws) { draw.fingerDraws = it },
            optionToggle("🔄", "Limpar anotações ao trocar de página", autoClear) { autoClear = it }
        ))

        // 6) ações
        sections.add(listOf(
            label("📷").apply { tooltipText = "Salvar captura da tela na galeria"; setOnClickListener { capture() } },
            label("⟳", 22f).apply { tooltipText = "Recarregar página"; setOnClickListener { web.reload() } },
            label("◁", 22f).apply { tooltipText = "Voltar"; setOnClickListener { if (web.canGoBack()) web.goBack() } }
        ))

        sections.forEachIndexed { idx, views ->
            if (idx > 0) content.addView(sep(v))
            if (!v) views.forEach { content.addView(it) }
            else views.chunked(2).forEach { pair ->
                content.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    pair.forEach { addView(it) }
                })
            }
        }

        collapse.setOnClickListener {
            collapsed = !collapsed
            scroll.visibility = if (collapsed) View.GONE else View.VISIBLE
            collapse.text = collapseIcon(v)
        }

        if (v) {
            bar.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(handle); addView(orient); addView(collapse)
            })
            bar.addView(toggle)
        } else {
            bar.addView(handle); bar.addView(toggle); bar.addView(orient); bar.addView(collapse)
        }
        bar.addView(scroll)
        return bar
    }

    private fun collapseIcon(v: Boolean) =
        if (v) (if (collapsed) "▼" else "▲") else (if (collapsed) "▶" else "◀")

    private fun optionToggle(icon: String, tip: String, initial: Boolean, onChange: (Boolean) -> Unit): TextView {
        val b = label(icon)
        var on = initial
        b.tooltipText = tip
        mark(b, on, "#00897B")
        b.setOnClickListener { on = !on; mark(b, on, "#00897B"); onChange(on) }
        return b
    }

    private fun selectTool(t: DrawingView.Tool) {
        draw.selectTool(t)
        draw.drawingEnabled = true
        refreshAll()
    }

    private fun refreshAll() {
        refreshToggle()
        toolButtons.forEach { (k, b) -> mark(b, k == draw.tool) }
        refreshColors()
        preview?.invalidate()
    }

    private fun refreshToggle() {
        val t = toggleBtn ?: return
        val on = draw.drawingEnabled
        val ico = if (on) "✍️" else "👆"
        val name = if (on) "Escrever" else "Navegar"
        t.text = if (vertical) "$ico\n$name" else "$ico $name"
        t.textSize = if (vertical) 12f else 15f
        t.background = rounded(Color.parseColor(if (on) "#2E7D32" else "#616161"), 14)
    }

    private fun refreshColors() {
        val cur = if (draw.tool == DrawingView.Tool.HIGHLIGHTER) draw.hlColor else draw.penColor
        for ((view, c) in colorDots) {
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                if (c == cur) setStroke(px(3), Color.WHITE) else setStroke(px(1), Color.parseColor("#88FFFFFF"))
            }
        }
    }

    // ------------------------------------------------------------ post-it
    private fun askText(x: Float, y: Float, existing: String?) {
        val et = EditText(this).apply {
            hint = "Anotação"; minLines = 2
            setText(existing ?: "")
            setSelection(text.length)
            setPadding(px(20), px(12), px(20), px(12))
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Novo post-it" else "Editar post-it")
            .setView(et)
            .setPositiveButton("OK") { _, _ -> draw.applyText(x, y, et.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        et.requestFocus()
    }

    // ------------------------------------------------------------ captura
    private fun capture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { toast("Captura precisa do Android 10 ou mais novo"); return }
        val b = bar
        b?.visibility = View.INVISIBLE
        root.postDelayed({
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val loc = IntArray(2)
            root.getLocationInWindow(loc)
            val rect = Rect(loc[0], loc[1], loc[0] + root.width, loc[1] + root.height)
            try {
                PixelCopy.request(window, rect, bmp, { result ->
                    b?.visibility = View.VISIBLE
                    if (result == PixelCopy.SUCCESS) saveBitmap(bmp) else toast("Não foi possível capturar")
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                b?.visibility = View.VISIBLE
                toast("Não foi possível capturar")
            }
        }, 150)
    }

    private fun saveBitmap(bmp: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "questao_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AnotaQuestoes")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) { toast("Falha ao salvar"); return }
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            toast("Salvo na Galeria (pasta AnotaQuestoes)")
        } catch (e: Exception) {
            toast("Falha ao salvar")
        }
    }
}
