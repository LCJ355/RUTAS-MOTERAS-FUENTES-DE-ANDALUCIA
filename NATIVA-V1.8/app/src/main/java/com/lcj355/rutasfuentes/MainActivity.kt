package com.lcj355.rutasfuentes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object { private var splashDone = false }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var map: MapView
    private lateinit var myLoc: MyLocationNewOverlay
    private lateinit var root: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var searchOverlay: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var results: ListView
    private lateinit var panelFuentes: LinearLayout
    private lateinit var panelRecord: LinearLayout
    private lateinit var panelMore: LinearLayout
    private lateinit var panelRuta: LinearLayout
    private lateinit var fuentesCount: TextView
    private lateinit var centrarBtn: android.widget.ImageView
    private lateinit var perfilBtn: android.widget.ImageView
    private var gpsStartOn = false
    private var gpsStartBtn: TextView? = null
    private lateinit var seguirBtn: TextView
    private lateinit var gpsCenterBtn: TextView
    private lateinit var nubesBtn: android.widget.ImageView
    private var infoIcon: android.widget.ImageView? = null
    private lateinit var radiusRow: LinearLayout
    private var fuenteRadius = 25.0
    private lateinit var routeInfo: LinearLayout
    private lateinit var routeInfoHeader: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var routeStats: TextView
    private var routeStatsText = ""
    private lateinit var routeStatsRow: LinearLayout
    private var routeTotalKm = 0.0
    private var routeTotalMin = 0.0
    private lateinit var navBanner: TextView
    private var navFreePan = false
    private lateinit var navBtn: android.widget.FrameLayout
    private lateinit var locMarker: Marker
    private lateinit var arrowDrawable: android.graphics.drawable.RotateDrawable
    private var compassHeading = -1f
    private var roseCard: LinearLayout? = null
    private var roseRetryPending = false
    private var roseView: CompassRoseView? = null
    private var roseText: TextView? = null
    private var lastRoseDeg = -1
    private var sensorMgr: android.hardware.SensorManager? = null
    private var compassRegistered = false
    private var lastAzimuth = -1f
    private val rotMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val compassListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            if (e.sensor.type == android.hardware.Sensor.TYPE_ROTATION_VECTOR) {
                if (map.visibility != View.VISIBLE) return
                android.hardware.SensorManager.getRotationMatrixFromVector(rotMatrix, e.values)
                android.hardware.SensorManager.getOrientation(rotMatrix, orientation)
                var az = ((Math.toDegrees(orientation[0].toDouble()).toFloat()) + 360f) % 360f
                // Suavizado circular para que el triangulo no tiemble
                if (lastAzimuth >= 0f) {
                    var d = ((az - lastAzimuth + 540f) % 360f) - 180f
                    if (Math.abs(d) < 0.8f) d = 0f
                    az = (lastAzimuth + d * 0.45f + 360f) % 360f
                }
                lastAzimuth = az
                compassHeading = az
                // Aplica el rumbo segun el modo (flecha y rosa; el mapa no gira solo)
                applyHeading(az)
            }
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
    }
    private var navAfterCalc = false
    private var searchTarget = "dest"
    private lateinit var recordText: TextView
    private lateinit var recordBtn: TextView
    private lateinit var fuenteList: ListView

    private val ORANGE = Color.parseColor("#FF6A00")
    private val GREEN_FUENTE = Color.parseColor("#2ECC71")
    private val GREEN_NUM = Color.parseColor("#00A000")
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private val stopMarkers = ArrayList<Marker>()
    private val BAR = Color.parseColor("#0E0E0E")
    private val DARK = Color.parseColor("#111111")
    private val ROUTE_BLUE = Color.parseColor("#1A73E8")
    private val TEXT2 = Color.parseColor("#5F6B7A")
    private val ORANGE_TEXT = Color.parseColor("#C2410C")
    private val ORANGE_SOFT = Color.parseColor("#FFF1E6")
    private val GREEN_TEXT = Color.parseColor("#1E9E57")
    private val GREEN_SOFT = Color.parseColor("#E7F7EE")
    private val BLUE_SOFT = Color.parseColor("#E8F0FE")
    private val DIVIDER = Color.parseColor("#ECEFF1")
    private val SURFACE2 = Color.parseColor("#F7F8FA")

    data class Fountain(val id: Int, val name: String, val town: String, val lat: Double, val lon: Double, val acc: String)

    private val fountains = ArrayList<Fountain>()
    private var fountainsByLat: List<Fountain> = emptyList()
    private val fSearch = ArrayList<String>()
    private val townNames = ArrayList<String>()
    private val towns = LinkedHashMap<String, DoubleArray>()
    private val townSearch = ArrayList<String>()
    private val postal = HashMap<String, List<String>>()

    private val markers = ArrayList<Marker>()
    private val poiMarkers = ArrayList<Marker>()
    private var routeLine: Polyline? = null
    private var routeCasing: Polyline? = null
    private var followArrow: Marker? = null
    private val refreshRunnable = Runnable { refreshMarkers() }

    private var startPt: GeoPoint? = null; private var startName = ""
    private var endPt: GeoPoint? = null; private var endName = ""
    private val paradas = ArrayList<Pair<GeoPoint, String>>()

    private val routePts = ArrayList<GeoPoint>()
    private var routeBounds: BoundingBox? = null
    private var routeCenterActive: Boolean? = null
    private var centerBtnOk: Boolean? = null
    private var lastArrowRot = -999f
    private val steps = ArrayList<String>()
    private val stepPts = ArrayList<GeoPoint>()
    private var navMode = false
    private var lastNavStep = -1

    private var recording = false
    private var lastLoc: GeoPoint? = null
    private var lastProcessedFixTime = 0L
    private var trackDist = 0.0
    private var currentTab = 0
    private var routePanelHidden = true
    private var routePanelEmpty = true
    private var manualPick: String? = null
    private var routeActionsHidden = true
    private var actionsBox: LinearLayout? = null
    private var actionsHeader: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val cfg = Configuration.getInstance()
        cfg.load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        cfg.userAgentValue = packageName
        cfg.setTileDownloadThreads(4.toShort())
        cfg.setTileFileSystemThreads(4.toShort())
        val base = File(filesDir, "osmdroid"); base.mkdirs()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")

        buildUi()
        loadData()
        askLocation()
    }

    // ---------------- UI ----------------
    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun round(color: Int, radiusDp: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable {
        val g = GradientDrawable(); g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radiusDp).toFloat(); g.setColor(color)
        if (stroke > 0) g.setStroke(dp(stroke), strokeColor)
        return g
    }

    private fun ring(strokeColor: Int, sizeDp: Int = 38, strokeDp: Int = 4): GradientDrawable {
        val g = GradientDrawable(); g.shape = GradientDrawable.OVAL
        g.setColor(Color.TRANSPARENT); g.setStroke(dp(strokeDp), strokeColor)
        g.setSize(dp(sizeDp), dp(sizeDp))
        return g
    }
    private fun dot(color: Int = ORANGE, sizeDp: Int = 16): GradientDrawable {
        val g = GradientDrawable(); g.shape = GradientDrawable.OVAL
        g.setColor(color); g.setStroke(dp(if (sizeDp >= 14) 2 else 1), Color.WHITE)
        g.setSize(dp(sizeDp), dp(sizeDp))
        return g
    }

    // Estrella: marcador de grupo (con número dentro) o suelto
    private fun star(color: Int = ORANGE, sizeDp: Int = 32, count: Int = 0): android.graphics.drawable.BitmapDrawable {
        val sz = dp(sizeDp) + dp(2)
        val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Path()
        val cx = sz / 2f; val cy = sz / 2f
        val outer = sz / 2f - dp(1)
        val inner = outer * (if (count > 0) 0.50f else 0.44f)
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * Math.cos(a)).toFloat(); val y = cy + (r * Math.sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        fill.color = color; fill.style = android.graphics.Paint.Style.FILL
        c.drawPath(p, fill)
        val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        line.color = Color.WHITE; line.style = android.graphics.Paint.Style.STROKE
        line.strokeWidth = dp(2).toFloat(); line.strokeJoin = android.graphics.Paint.Join.ROUND
        c.drawPath(p, line)
        if (count > 0) {
            val txt = if (count > 999) "999+" else count.toString()
            val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            tp.textAlign = android.graphics.Paint.Align.CENTER
            tp.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tp.textSize = sz * when { txt.length >= 4 -> 0.24f; txt.length == 3 -> 0.30f; txt.length == 2 -> 0.38f; else -> 0.46f }
            val fm = tp.fontMetrics
            val ty = cy - (fm.ascent + fm.descent) / 2f + sz * 0.03f
            val halo = android.graphics.Paint(tp)
            halo.style = android.graphics.Paint.Style.STROKE
            halo.strokeWidth = sz * 0.10f
            halo.strokeJoin = android.graphics.Paint.Join.ROUND
            halo.color = Color.WHITE
            c.drawText(txt, sz / 2f, ty, halo)
            tp.color = GREEN_NUM
            c.drawText(txt, sz / 2f, ty, tp)
        }
        return android.graphics.drawable.BitmapDrawable(resources, b)
    }

    private fun tv(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this); t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD)
        return t
    }

    private fun ic(res: Int, sizeDp: Int, tint: Int = DARK): ImageView = ImageView(this).apply {
        setImageResource(res); setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun iconCircle(res: Int, bg: Int, tint: Int, sizeDp: Int = 34): FrameLayout {
        val box = FrameLayout(this)
        box.background = round(bg, sizeDp / 2)
        val inner = (sizeDp * 55) / 100
        box.addView(ImageView(this).apply { setImageResource(res); setColorFilter(tint) },
            FrameLayout.LayoutParams(dp(inner), dp(inner), Gravity.CENTER))
        box.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(11) }
        return box
    }

    private fun markMatch(text: String, q: String): CharSequence {
        if (q.isBlank()) return text
        val i = text.indexOf(q, 0, true)
        if (i < 0) return text
        val sp = android.text.SpannableString(text)
        sp.setSpan(android.text.style.ForegroundColorSpan(ORANGE_TEXT), i, i + q.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(android.text.style.StyleSpan(Typeface.BOLD), i, i + q.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    private class FIcon(val res: Int, val bg: Int, val fg: Int)

    private fun fountainStyle(name: String): FIcon {
        val n = norm(name)
        return when {
            n.contains("pozo") || n.contains("pocito") || n.contains("aljibe") || n.contains("cisterna") ->
                FIcon(R.drawable.ic_well, BLUE_SOFT, ROUTE_BLUE)
            n.contains("manantial") || n.contains("nacimiento") || n.contains("surgencia") || n.contains("afloramiento") ->
                FIcon(R.drawable.ic_spring, GREEN_SOFT, GREEN_TEXT)
            else -> FIcon(R.drawable.ic_fountain, ORANGE_SOFT, ORANGE_TEXT)
        }
    }

    private fun fountainDotColor(name: String): Int {
        val n = norm(name)
        return when {
            n.contains("pozo") || n.contains("pocito") || n.contains("aljibe") || n.contains("cisterna") -> ROUTE_BLUE
            n.contains("manantial") || n.contains("nacimiento") || n.contains("surgencia") || n.contains("afloramiento") -> GREEN_TEXT
            else -> GREEN_FUENTE
        }
    }

    private inner class MsgAdapter(private val text: String) : BaseAdapter() {
        override fun getCount() = 1
        override fun getItem(p: Int): Any? = null
        override fun getItemId(p: Int) = 0L
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(18), dp(30), dp(18), dp(30))
                addView(iconCircle(R.drawable.ic_fountain, SURFACE2, TEXT2, 42).apply {
                    val lp = layoutParams as LinearLayout.LayoutParams; lp.marginEnd = 0; lp.gravity = Gravity.CENTER_HORIZONTAL
                })
                addView(tv(text, 13.5f, TEXT2).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
            }
        }
    }

    private fun greenArrow(sz: Int): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        // Flecha de navegacion (punta arriba, muesca en la cola)
        val pts = arrayOf(
            sz * 0.50f to sz * 0.04f,   // punta
            sz * 0.93f to sz * 0.94f,   // esquina derecha
            sz * 0.50f to sz * 0.74f,   // muesca
            sz * 0.07f to sz * 0.94f    // esquina izquierda
        )
        val p = android.graphics.Path()
        p.moveTo(pts[0].first, pts[0].second)
        p.lineTo(pts[1].first, pts[1].second)
        p.lineTo(pts[2].first, pts[2].second)
        p.lineTo(pts[3].first, pts[3].second)
        p.close()
        // Flecha interior (verde) reducida hacia el centro
        val cx = pts.sumOf { it.first.toDouble() }.toFloat() / pts.size
        val cy = pts.sumOf { it.second.toDouble() }.toFloat() / pts.size
        val t = 0.26f
        val pin = android.graphics.Path()
        pts.forEachIndexed { i, (x, y) ->
            val ix = x + (cx - x) * t
            val iy = y + (cy - y) * t
            if (i == 0) pin.moveTo(ix, iy) else pin.lineTo(ix, iy)
        }
        pin.close()
        // Borde blanco exterior para que se vea sobre el mapa
        c.drawPath(p, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = sz * 0.09f; strokeJoin = android.graphics.Paint.Join.ROUND })
        // Triangulo naranja (marco)
        c.drawPath(p, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE; style = android.graphics.Paint.Style.FILL })
        // Triangulo verde dentro del naranja
        c.drawPath(pin, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ECC71"); style = android.graphics.Paint.Style.FILL })
        return b
    }
    private fun compassIcon(sz: Int, withRing: Boolean = true, ringColor: Int = ORANGE): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val cx = sz / 2f; val cy = sz / 2f
        // sin relleno: la brujula deja ver el mapa
        if (withRing) c.drawCircle(cx, cy, sz * 0.47f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = sz * 0.035f; color = ringColor })
        val tick = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = sz * 0.014f; color = Color.parseColor("#B3FF6A00") }
        for (a in intArrayOf(0, 45, 90, 135, 180, 225, 270, 315)) {
            val rad = Math.toRadians(a.toDouble())
            val r1 = if (a % 90 == 0) sz * 0.37f else sz * 0.41f
            val r2 = sz * 0.45f
            c.drawLine(cx + (Math.sin(rad) * r1).toFloat(), cy - (Math.cos(rad) * r1).toFloat(),
                       cx + (Math.sin(rad) * r2).toFloat(), cy - (Math.cos(rad) * r2).toFloat(), tick)
        }
        val north = android.graphics.Path().apply { moveTo(cx, cy - sz * 0.33f); lineTo(cx - sz * 0.11f, cy); lineTo(cx + sz * 0.11f, cy); close() }
        c.drawPath(north, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = sz * 0.022f; color = Color.parseColor("#99000000") })
        c.drawPath(north, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = ORANGE })
        val south = android.graphics.Path().apply { moveTo(cx, cy + sz * 0.33f); lineTo(cx - sz * 0.11f, cy); lineTo(cx + sz * 0.11f, cy); close() }
        c.drawPath(south, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = sz * 0.022f; color = Color.parseColor("#99000000") })
        c.drawPath(south, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = Color.parseColor("#E6E6E6") })
        c.drawCircle(cx, cy, sz * 0.035f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return b
    }


    private fun buildUi() {
        root = FrameLayout(this)
        map = MapView(this)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        // Girar el mapa con dos dedos (pausa la brujula hasta tocar el boton)
        val rotOverlay = object : org.osmdroid.views.overlay.gestures.RotationGestureOverlay(map) {
            override fun onRotate(angle: Float) {
                super.onRotate(angle)
                lastRoseDeg = -1
                applyHeading()
                map.post { placeRoseOnMap() }
                map.post { updateCenterButton() }
            }
        }
        rotOverlay.isEnabled = true
        map.overlays.add(rotOverlay)
        map.setBuiltInZoomControls(false)
        map.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN && navMode) navFreePan = true
            false
        }
        map.controller.setZoom(8.0)
        map.controller.setCenter(GeoPoint(37.3, -4.5))
        root.addView(map, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val mlp = GpsMyLocationProvider(this)
        mlp.setLocationUpdateMinTime(2000L)   // no pedir posición más de 1 vez cada 2 s
        mlp.setLocationUpdateMinDistance(5f)  // ni si no te has movido 5 m
        myLoc = MyLocationNewOverlay(mlp, map)
        myLoc.setDrawAccuracyEnabled(false)
        val trans = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        myLoc.setPersonIcon(trans); myLoc.setDirectionArrow(trans, trans)
        map.overlays.add(myLoc)
        arrowDrawable = android.graphics.drawable.RotateDrawable().apply { drawable = android.graphics.drawable.BitmapDrawable(resources, greenArrow(dp(44))); setFromDegrees(0f); setToDegrees(360f) }
        locMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = arrowDrawable; isEnabled = !compassRoseOn
            title = null; snippet = null
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
            map.overlays.add(this)
        }

        val events = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null && manualPick != null) { applyPickPoint(p); return true }
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    if (paradas.size >= 10) { toast("Máximo 10 paradas") } else {
                        paradas.add(p to "Punto manual"); updRouteInfo()
                        toast("Punto manual ${paradas.size}/10. Pulsa Calcular ruta")
                        if (routePts.isNotEmpty() && startPt != null) calcRoute()
                    }
                }
                return true
            }
        })
        map.overlays.add(0, events)

        // Barra superior
        topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(dp(12), dp(12), dp(12), dp(10)) }
        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.hd_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(23).toFloat()) } }
            clipToOutline = true
            setOnClickListener { v ->
                val pois = listOf(
                    "\uD83C\uDFCD  Concesionarios H-D" to "dealer",
                    "\u26FD  Gasolina" to "fuel",
                    "\uD83C\uDF74  Restaurantes" to "restaurant",
                    "\uD83D\uDECF\uFE0F  Alojamiento" to "hotel",
                    "\uD83C\uDD7F\uFE0F  Aparcamiento" to "parking",
                    "\uD83D\uDCA7  Fuentes de agua" to "drinking_water",
                    "\uD83C\uDFE5  Hospitales" to "hospital",
                    "\uD83D\uDD27  Talleres" to "car_repair",
                    "\uD83D\uDED2  Supermercados" to "supermarket"
                )
                val pop = android.widget.PopupMenu(this@MainActivity, v)
                pois.forEachIndexed { i, p ->
                    val mi = pop.menu.add(0, i, i, p.first)
                    mi.isCheckable = true; mi.isChecked = p.second in poiKinds
                }
                pop.menu.setGroupCheckable(0, true, false)
                pop.setOnMenuItemClickListener { mi ->
                    val kind = pois[mi.itemId].second
                    if (kind in poiKinds) hidePoisKind(kind) else loadPois(kind)
                    true
                }
                pop.show()
            }
        }
        val pill = tv("🔍  ¿A dónde?", 16f, DARK).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), 0, dp(18), 0); background = round(Color.WHITE, 26, 3, ORANGE)
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(10); marginEnd = dp(10) }
            setOnClickListener { searchTarget = "dest"; openSearch() }
        }
        // Tipos de mapa al estilo Google Maps (indice real del TileSource)
        val layers = listOf(0 to "Mapa", 3 to "Satélite", 1 to "Relieve", 2 to "Bici")
        val more = tv("🗺️", 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER; background = round(Color.TRANSPARENT, 25, 3, ORANGE); layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
            contentDescription = "Tipo de mapa"
            setOnClickListener { v ->
                val pop = android.widget.PopupMenu(this@MainActivity, v)
                layers.forEachIndexed { i, (idx, l) -> pop.menu.add(0, i, i, if (idx == layerIdx) "✓ $l" else l) }
                pop.menu.add(1, 100, 100, "🚦 Tráfico DGT")
                pop.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        100 -> startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://infocar.dgt.es/etraffic/")))
                        else -> { layerIdx = layers[mi.itemId].first; applyLayer() }
                    }
                    true
                }
                pop.show()
            }
        }
        // Menú meteorológico: nubes NASA, radar de lluvia y clima (justo debajo de las capas)
        nubesBtn = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_weather)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = ring(ORANGE, 38, 3); contentDescription = "Nubes, radar y clima"
            setOnClickListener { v ->
                val pop = android.widget.PopupMenu(this@MainActivity, v)
                pop.menu.add(1, 200, 200, (if (layerIdx == 4) "✓ " else "") + "☁️ NASA (nubes)")
                pop.menu.add(1, 201, 201, (if (radarEnabled) "✓ " else "") + "🌧️ Radar de lluvia")
                pop.menu.add(1, 202, 202, "🌤️ Clima aquí")
                pop.menu.add(1, 203, 203, "\uD83C\uDF26\uFE0F Clima en ruta")
                pop.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        200 -> { layerIdx = if (layerIdx == 4) 0 else 4; applyLayer() }
                        201 -> toggleRadar()
                        202 -> showWeather()
                        203 -> weatherDialog()
                    }
                    true
                }
                pop.show()
            }
        }
        seguirBtn = tv("\uD83E\uDDED", 17f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = ring(if (compassRoseOn) GREEN_FUENTE else ORANGE, 19, 3)
            contentDescription = "Modo Br\u00FAjula / Modo GPS"
            setOnClickListener {
                compassRoseOn = !compassRoseOn
                lastArrowRot = -999f
                cfg().edit().putString("roseOn", if (compassRoseOn) "1" else "0").apply()
                seguirBtn.text = if (compassRoseOn) "\uD83E\uDDED" else "\uD83C\uDFAF"
                seguirBtn.background = ring(if (compassRoseOn) GREEN_FUENTE else ORANGE, 19, 3)
                seguirBtn.contentDescription = if (compassRoseOn) "Modo Br\u00FAjula" else "Modo GPS"
                lastRoseDeg = -1
                applyHeading()
                if (compassRoseOn) {
                    map.post { placeRoseOnMap() }
                    toast("Modo Br\u00FAjula")
                } else {
                    roseCard?.visibility = View.INVISIBLE
                    toast("Modo GPS")
                }
                myLoc.enableMyLocation()
                val p = myLoc.myLocation
                if (p != null) {
                    myLoc.disableFollowLocation()
                    navFreePan = false
                    map.controller.animateTo(p)
                    if (map.zoomLevelDouble < 15.0) map.controller.setZoom(16.0)
                }
            }
            setOnLongClickListener {
                myLoc.enableMyLocation()
                val p = myLoc.myLocation
                if (p == null) { toast("Esperando GPS\u2026"); true } else {
                    myLoc.disableFollowLocation()
                    navFreePan = false
                    map.controller.animateTo(p)
                    if (map.zoomLevelDouble < 15.0) map.controller.setZoom(16.0)
                    toast("Centrado en tu ubicaci\u00F3n")
                    true
                }
            }
        }
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val avatarWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
            background = ring(ORANGE, 50, 3)
        }
        avatar.layoutParams = FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)
        avatarWrap.addView(avatar)
        leftCol.addView(avatarWrap)
        leftCol.addView(seguirBtn, LinearLayout.LayoutParams(dp(38), dp(38)).apply { topMargin = dp(6) })
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        rightCol.addView(more)
        rightCol.addView(nubesBtn, LinearLayout.LayoutParams(dp(38), dp(38)).apply { topMargin = dp(5) })
        topBar.addView(leftCol); topBar.addView(pill); topBar.addView(rightCol, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP })
        centrarBtn = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_center_route)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = round(Color.parseColor("#CC000000"), 19); contentDescription = "Centrar ruta"; visibility = View.GONE
            setOnClickListener { centrarRuta() }
        }
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.addView(centrarBtn, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(17); bottomMargin = dp(134) })
        perfilBtn = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_chart)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = ring(ORANGE, 50, 3); contentDescription = "Perfil de altura"; visibility = View.VISIBLE
            setOnClickListener { elevationDialog() }
        }
        if (!compassRoseOn) {
            seguirBtn.text = "\uD83C\uDFAF"; seguirBtn.contentDescription = "Modo GPS"
            seguirBtn.background = ring(ORANGE, 19, 3)
        }
        gpsCenterBtn = tv("\uD83C\uDFAF", 17f, Color.WHITE).apply {
            gravity = Gravity.CENTER; background = round(Color.parseColor("#CC000000"), 19)
            contentDescription = "Centrar en tu ubicaci\u00F3n"
            setOnClickListener { centerOnGps() }
        }
        root.addView(gpsCenterBtn, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(17); bottomMargin = dp(134) })
        statusText = tv("", 12.5f, Color.WHITE).apply { setPadding(dp(14), dp(6), dp(14), dp(6)); background = round(Color.parseColor("#CC000000"), 8); visibility = View.GONE }
        root.addView(statusText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(148) })
        // Los avisos se borran solos a los 5 s (los de "⏳" se quedan mientras trabajan)
        statusText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val t = s?.toString() ?: ""
                main.removeCallbacks(statusHide)
                if (t.isEmpty() || t.contains("⏳")) return
                main.postDelayed(statusHide, 5000)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Franja inferior: circulo GPS/Navegar + km y tiempo restantes de la ruta
        navBtn = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(10) }
            background = ring(ORANGE, 50, 3)
            setOnClickListener { toggleNav() }
            addView(android.widget.ImageView(this@MainActivity).apply {
                setImageBitmap(greenArrow(dp(34)))
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        }
        routeStats = tv("", 13.5f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        routeStatsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BAR); setPadding(dp(8), dp(3), dp(12), dp(3)); visibility = View.GONE
            addView(navBtn); addView(routeStats); addView(perfilBtn, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginStart = dp(10) })
        }
        root.addView(routeStatsRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(72) })

        navBanner = tv("", 14.5f, Color.WHITE, true).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12)); background = round(BAR, 12, 2, ORANGE)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; visibility = View.GONE
        }
        root.addView(navBanner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply { topMargin = dp(146); leftMargin = dp(12); rightMargin = dp(12) })

        routeInfoHeader = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        routeInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = round(Color.WHITE, 14, 1, Color.parseColor("#E0E0E0")); visibility = View.GONE
            addView(routeInfoHeader)
            val undoRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) } }
            undoRow.addView(tv("↩️  Atrás", 13.5f, DARK, true).apply {
                gravity = Gravity.CENTER; background = round(Color.parseColor("#EFEFEF"), 20); setPadding(0, dp(9), 0, dp(9))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { undoRouteStep() }
            })
            undoRow.addView(tv("Calcular", 13.5f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = round(ORANGE, 20); setPadding(0, dp(9), 0, dp(9))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { cancelManualPick(); calcRoute() }
            })
            undoRow.addView(tv("🗑️  Reset", 13.5f, DARK, true).apply {
                gravity = Gravity.CENTER; background = round(Color.parseColor("#FFE0E0"), 20); setPadding(0, dp(9), 0, dp(9))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { resetRoute() }
            })
            addView(undoRow)
        }
        root.addView(routeInfo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { leftMargin = dp(10); rightMargin = dp(10); bottomMargin = dp(88) })


        // Overlay buscador
        searchOverlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); visibility = View.GONE }
        val srow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(10), dp(12), dp(6)) }
        srow.addView(tv("←", 24f, DARK).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)); setOnClickListener { closeSearch() } })
        searchInput = EditText(this).apply {
            hint = "Fuente, pueblo o CP…"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = round(Color.parseColor("#F0F0F0"), 24); setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false }
            setOnClickListener { post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) } }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { doSearch(s?.toString() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        srow.addView(searchInput)
        results = ListView(this).apply { setPadding(0, 0, 0, dp(8)); clipToPadding = false }
        searchOverlay.addView(srow)
        searchOverlay.addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(searchOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Paneles
        panelFuentes = makePanel("⛲ Fuentes cerca de ti")
        radiusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), 0, 0, dp(6)) }
        for (r in listOf(10, 25, 50, 100, 150, 200)) {
            radiusRow.addView(tv("$r", 12.5f, DARK, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(9), dp(8), dp(9), dp(8)); tag = r
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) }
                setOnClickListener { fuenteRadius = r.toDouble(); styleRadiusRow(); showFuentesNear() }
            })
        }
        radiusRow.addView(tv("\uD83D\uDD0D", 12f, DARK, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(6), dp(9), dp(6), dp(9)); tag = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { searchTarget = "dest"; openSearch() }
        })
        fuentesCount = tv("", 13f, GREEN_TEXT, true).apply { setPadding(0, dp(2), 0, dp(10)) }
        panelFuentes.addView(fuentesCount)
        panelFuentes.addView(android.widget.HorizontalScrollView(this@MainActivity).apply {
            isHorizontalScrollBarEnabled = false; addView(radiusRow)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fuenteList = ListView(this).apply { setPadding(0, 0, 0, dp(4)); clipToPadding = false; divider = null; dividerHeight = 0 }
        panelFuentes.addView(fuenteList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        panelRecord = makePanel("⏺️ Grabar ruta")
        recordText = tv("Pulsa INICIAR para grabar tu recorrido.", 14f, Color.parseColor("#333333"))
        recordBtn = tv("INICIAR GRABACIÓN", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = round(ORANGE, 10); setPadding(0, dp(16), 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
            setOnClickListener { toggleRecord() }
        }
        panelRecord.addView(recordText); panelRecord.addView(recordBtn)

        panelMore = makePanel("\u2630 Datos")
        fun dato(key: String, icon: Int, title: String, sub: String) {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(12), dp(2), dp(12))
            }
            row.addView(iconCircle(icon, SURFACE2, ORANGE_TEXT, 36))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(tv(title, 16f, DARK, true))
            col.addView(tv(sub, 12.5f, TEXT2).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(col)
            row.setOnClickListener { onMore(key) }
            panelMore.addView(row)
            panelMore.addView(View(this@MainActivity).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(49) }
            })
        }
        dato("CP de la ruta", R.drawable.ic_postal, "CP de la ruta", "Códigos postales de salida, paradas y destino")
        dato("GPX", R.drawable.ic_gpx, "GPX", "Exportar o importar el recorrido")
        dato("Orientacion", R.drawable.ic_target, "Orientaci\u00F3n del mapa", "Norte arriba al centrar; gira con dos dedos")
        dato("COPIA de datos", R.drawable.ic_backup, "COPIA de datos", "Copia ZIP con fotos y notas")
        dato("Fotos: carpeta y copias", R.drawable.ic_photo, "Fotos: carpeta y copias", "Carpeta externa y copia automática")
        panelRuta = makePanel("\u2699\uFE0F Ruta")
        val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val act = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) } }
            act.addView(tv("🔗  Compartir", 14f, DARK, true).apply {
                gravity = Gravity.CENTER; background = round(Color.parseColor("#EFEFEF"), 20); setPadding(0, dp(10), 0, dp(10))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { shareRouteMenu() }
            })
            act.addView(tv("💾  Guardar", 14f, DARK, true).apply {
                gravity = Gravity.CENTER; background = round(Color.parseColor("#EFEFEF"), 20); setPadding(0, dp(10), 0, dp(10))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { saveRouteDialog() }
            })
            box.addView(act)
        panelRuta.addView(box)

        panelRuta.addView(tv("\u23FA  Grabar recorrido", 14f, DARK, true).apply {
            gravity = Gravity.CENTER; background = round(Color.parseColor("#EFEFEF"), 20); setPadding(0, dp(12), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            setOnClickListener { selectTab(2) }
        })
        panelRuta.addView(tv("\uD83D\uDCC1  Mis rutas guardadas", 14f, DARK, true).apply {
            gravity = Gravity.CENTER; background = round(Color.parseColor("#EFEFEF"), 20); setPadding(0, dp(12), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
            setOnClickListener { showSavedRoutes() }
        })

        // Brujula transparente anclada al Norte del mapa
        compassRoseOn = (cfg().getString("roseOn", "1") ?: "1") == "1"
        val roseBox = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
        }
        val rt = tv("0\u00B0N", 22f, ORANGE, true).apply {
            gravity = Gravity.CENTER
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xAA000000.toInt())
            setPadding(0, 0, 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(dp(253), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        roseText = rt
        val rv = CompassRoseView(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(253), dp(253))
        }
        roseView = rv
        roseBox.addView(rt)
        roseBox.addView(rv)
        roseCard = roseBox
        root.addView(roseBox, FrameLayout.LayoutParams(dp(253), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8); topMargin = dp(150)
        })

        panelFuentes.visibility = View.GONE; panelRecord.visibility = View.GONE; panelMore.visibility = View.GONE; panelRuta.visibility = View.GONE
        styleRadiusRow()
        val plp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { bottomMargin = dp(72) }
        root.addView(panelFuentes, plp); root.addView(panelRecord, plp); root.addView(panelMore, plp); root.addView(panelRuta, plp)

        // Barra inferior
        bottomBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.TRANSPARENT) }
        listOf("\uD83C\uDFCD\uFE0F" to "Ruta", "\u2699\uFE0F" to "Ruta", "\u26F2" to "Fuentes", "\u2630" to "Datos").forEachIndexed { i, (ic, lb) ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(2)) }
            item.addView(tv(ic, 24f, if (i == 3) ROUTE_BLUE else Color.WHITE).apply { gravity = Gravity.CENTER; contentDescription = lb; layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)); background = ring(GREEN_FUENTE, 50); setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), Color.BLACK); if (i == 0) setPadding(dp(2), 0, dp(2), dp(16)) })

            item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val tab = intArrayOf(0, 4, 1, 3)[i]; item.tag = tab
            item.setOnClickListener {
                if (tab == 0) {
                    if (currentTab == 0) routePanelHidden = !routePanelHidden else routePanelHidden = false
                    selectTab(0)
                } else if (currentTab == tab) {
                    // Segunda pulsación: cierra y vuelve al mapa
                    selectTab(0)
                } else selectTab(tab)
            }
            bottomBar.addView(item)
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(2)) }
        val ii = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_info_round)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(2), dp(2), dp(2), dp(2))
            contentDescription = "Acerca de"
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
            background = ring(ORANGE, 50)
        }
        infoIcon = ii
        info.addView(ii)
        info.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        info.setOnClickListener { infoIcon?.background = ring(GREEN_FUENTE, 50); onMore("Acerca de") }
        info.tag = -1
        bottomBar.addView(info)
        root.addView(bottomBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        setContentView(root)

        updRouteInfo()

        loadPhotoTree()
        showSplashAndSound()

        bottomBar.post { layoutFab() }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(dp(12), sys.top + dp(10), dp(12), dp(10))
            bottomBar.setPadding(0, dp(2), 0, sys.bottom + dp(2))
            val pTop = sys.top + dp(10)
            panelFuentes.setPadding(dp(14), pTop, dp(14), dp(10))
            panelRecord.setPadding(dp(14), pTop, dp(14), dp(10))
            panelMore.setPadding(dp(14), pTop, dp(14), dp(10))
            panelRuta.setPadding(dp(14), pTop, dp(14), dp(10))
            root.post { layoutFab() }
            (searchOverlay.getChildAt(0) as? View)?.setPadding(dp(10), sys.top + dp(16), dp(12), dp(8))
            results.setPadding(0, 0, 0, sys.bottom + dp(8))
            insets
        }

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { scheduleRefresh(); placeRoseOnMap(); updateCenterButton(); updateRouteCenterButton(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { scheduleRefresh(); placeRoseOnMap(); updateCenterButton(); updateRouteCenterButton(); return false }
        })
        selectTab(0)
        updateCenterButton()
        map.post {
            // Vista inicial: toda Andalucia
            val andalucia = BoundingBox.fromGeoPoints(listOf(GeoPoint(38.78, -7.70), GeoPoint(35.95, -1.55))).increaseByScale(1.04f)
            map.zoomToBoundingBox(andalucia, false)
        }
        map.post { refreshMarkers() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    searchOverlay.visibility == View.VISIBLE -> closeSearch()
                    navMode -> stopNav()
                    currentTab != 0 -> selectTab(0)
                    poiMarkers.isNotEmpty() -> hidePois()
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    private fun makePanel(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(14), dp(96), dp(14), dp(10))
        val head = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ic(R.drawable.ic_back, 18, ORANGE_TEXT).apply {
            background = ring(ORANGE, 32, 2); setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
            contentDescription = "Volver al mapa"; setOnClickListener { selectTab(0) }
        })
        val tt = tv(title, 21f, DARK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        if (title.startsWith("\u2630")) {
            val sp = android.text.SpannableString(title)
            sp.setSpan(android.text.style.ForegroundColorSpan(ROUTE_BLUE), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tt.text = sp
        }
        head.addView(tt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(head)
        addView(View(this@MainActivity).apply { setBackgroundColor(DIVIDER); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(10) } })
    }

    private fun selectTab(i: Int) {
        currentTab = i
        if (i == 0) registerCompass() else unregisterCompass()
        syncBarRings()

        map.visibility = if (i == 0) View.VISIBLE else View.GONE
        topBar.visibility = if (i == 0) View.VISIBLE else View.GONE
        panelFuentes.visibility = if (i == 1) View.VISIBLE else View.GONE
        panelRecord.visibility = if (i == 2) View.VISIBLE else View.GONE
        panelMore.visibility = if (i == 3) View.VISIBLE else View.GONE
        panelRuta.visibility = if (i == 4) View.VISIBLE else View.GONE
        routeInfo.visibility = if (!routePanelHidden && i == 0) View.VISIBLE else View.GONE
        if (routeInfo.visibility == View.VISIBLE) routeInfo.bringToFront()
        routeStatsRow.visibility = if (i == 0 && routeStatsText.isNotEmpty()) View.VISIBLE else View.GONE
        val showMap = i == 0
        seguirBtn.visibility = if (showMap) View.VISIBLE else View.GONE
        gpsCenterBtn.visibility = if (showMap) View.VISIBLE else View.GONE
        if (!showMap) roseCard?.visibility = View.INVISIBLE
        if (i == 0) { routeInfo.post { layoutFab() }; routeStats.post { layoutFab() }; map.post { placeRoseOnMap() } }
        if (i == 1) showFuentesNear()
        syncRouteButtons()
    }

    // Aros de la barra: verde = pestana activa (Ruta solo cuenta con el panel de ruta abierto)
    private fun syncBarRings() {
        if (!::bottomBar.isInitialized) return
        for (j in 0 until bottomBar.childCount) {
            val item = bottomBar.getChildAt(j) as? LinearLayout ?: continue
            val tab = item.tag as? Int ?: continue
            val on = tab == currentTab && (tab != 0 || !routePanelHidden)
            (item.getChildAt(0) as? TextView)?.let { tvw ->
                if (tab != 3) tvw.setTextColor(Color.WHITE)
                tvw.background = ring(if (on) GREEN_FUENTE else ORANGE, 50)
            }
        }
    }

    // ---------------- DATOS ----------------
    private fun loadData() {
        scope.launch {
            val js = withContext(Dispatchers.IO) { assets.open("data.json").bufferedReader().readText() }
            withContext(Dispatchers.Default) {
                val o = JSONObject(js)
                val arr = o.getJSONArray("fd")
                val fTemp = ArrayList<Fountain>(arr.length())
                val fSTemp = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONArray(i)
                    fTemp.add(Fountain(a.getInt(0), a.getString(1), a.getString(2), a.getDouble(3), a.getDouble(4), a.optString(5)))
                    fSTemp.add(norm(a.getString(1) + " " + a.getString(2)))
                }
                val fsTemp = fTemp.sortedBy { it.lat }
                val tTemp = LinkedHashMap<String, DoubleArray>()
                val tnTemp = ArrayList<String>(); val tsTemp = ArrayList<String>()
                val tj = o.getJSONObject("towns")
                for (k in tj.keys()) { val a = tj.getJSONArray(k); tTemp[k] = doubleArrayOf(a.getDouble(0), a.getDouble(1), a.optDouble(2, 0.0)); tnTemp.add(k); tsTemp.add(norm(k)) }
                val pTemp = HashMap<String, List<String>>()
                val pj = o.getJSONObject("postal")
                for (k in pj.keys()) { val a = pj.getJSONArray(k); val l = ArrayList<String>(); for (i in 0 until a.length()) l.add(a.getString(i)); pTemp[k] = l }
                // Publicar todo de golpe en el hilo principal: evita ConcurrentModificationException
                withContext(Dispatchers.Main) {
                    fountains.clear(); fountains.addAll(fTemp)
                    fountainsByLat = fsTemp
                    fSearch.clear(); fSearch.addAll(fSTemp)
                    towns.clear(); towns.putAll(tTemp)
                    townNames.clear(); townNames.addAll(tnTemp)
                    townSearch.clear(); townSearch.addAll(tsTemp)
                    postal.clear(); postal.putAll(pTemp)
                    refreshMarkers(); showFuentesNear()
                }
            }
        }
    }

    private fun norm(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    // ---------------- MARCADORES ----------------
    private fun scheduleRefresh() {
        if (navMode) return
        main.removeCallbacks(refreshRunnable); main.postDelayed(refreshRunnable, 350)
    }
    private fun radarToFront() {
        val ro = radarOverlay ?: return
        if (!radarEnabled) return
        if (map.overlays.isNotEmpty() && map.overlays[map.overlays.size - 1] === ro) return
        map.overlays.remove(ro); map.overlays.add(ro)
    }


    private fun refreshMarkers() {
        if (navMode) return
        radarToFront()
        val poiWant = map.zoomLevelDouble >= POI_MIN_ZOOM
        if (poiMarkers.isNotEmpty() && poiWant != poiShown) { poiShown = poiWant; poiMarkers.forEach { it.isEnabled = poiWant }; map.invalidate() }
        if (fountains.isEmpty()) return
        val bb: BoundingBox = map.boundingBox ?: return
        markers.forEach { map.overlays.remove(it) }; markers.clear()
        val z = map.zoomLevelDouble
        val byLat = fountainsByLat
        if (byLat.isEmpty()) return
        var leftIdx = 0; var rightIdx = byLat.size
        while (leftIdx < rightIdx) {
            val mid = (leftIdx + rightIdx) ushr 1
            if (byLat[mid].lat < bb.latSouth) leftIdx = mid + 1 else rightIdx = mid
        }
        val inView = ArrayList<Fountain>()
        var i = leftIdx
        while (i < byLat.size) {
            val f = byLat[i]
            if (f.lat > bb.latNorth) break
            if (f.lon in bb.lonWest..bb.lonEast) inView.add(f)
            i++
        }
        if (inView.isEmpty()) { map.invalidate(); return }

        // Agrupado real: buscamos la celda mas fina que no pase del objetivo de grupos (busqueda binaria)
        // Con una ruta calculada se dejan solo 10 grupos para que la ruta se vea bien
        val tileDeg = 360.0 / Math.pow(2.0, z)
        val target = if (routePts.size >= 2) 10 else 20
        fun gridKey(f: Fountain, cell: Double): Long {
            val gy = Math.floor(f.lat / cell).toLong()
            val gx = Math.floor(f.lon / cell).toLong()
            return (gy shl 32) or (gx and 0xffffffffL)
        }
        fun exceeds(cellPx: Double): Boolean {
            val cell = tileDeg * (cellPx / 256.0)
            val seen = HashSet<Long>()
            for (f in inView) if (seen.add(gridKey(f, cell)) && seen.size > target) return true
            return false
        }
        var lo = 44.0; var hi = 1200.0
        repeat(7) {
            val mid = (lo + hi) / 2.0
            if (exceeds(mid)) lo = mid else hi = mid
        }
        val cell = tileDeg * (hi / 256.0)
        val groups = HashMap<Long, MutableList<Fountain>>()
        for (f in inView) {
            val key = gridKey(f, cell)
            var lst = groups[key]
            if (lst == null) { lst = ArrayList(); groups[key] = lst }
            lst.add(f)
        }

        for (lst in groups.values) {
            if (lst.size == 1) {
                val f = lst[0]
                val m = Marker(map)
                m.position = GeoPoint(f.lat, f.lon)
                m.title = null; m.snippet = null
                m.icon = dot(fountainDotColor(f.name), 16)
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                m.setOnMarkerClickListener { _, _ -> map.post { showFuenteDialog(f) }; true }
                map.overlays.add(m); markers.add(m)
            } else {
                var la = 0.0; var lo = 0.0
                for (f in lst) { la += f.lat; lo += f.lon }
                la /= lst.size; lo /= lst.size
                val target = GeoPoint(la, lo)
                val m = Marker(map)
                m.position = target
                m.title = null
                m.snippet = null
                m.icon = star(ORANGE, 34, lst.size)
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                m.setOnMarkerClickListener { _, _ ->
                    map.controller.animateTo(target)
                    map.controller.setZoom(Math.min(map.zoomLevelDouble + 2.0, 19.0))
                    scheduleRefresh(); true
                }
                map.overlays.add(m); markers.add(m)
            }
        }
        radarToFront()
        map.invalidate()
    }

    private fun showFuenteDialog(f: Fountain) {
        val nFotos = photoFiles(f.id).size
        val acc = getAccess(f.id)
        val note = getNote(f.id)
        val gps = myLoc.myLocation
        val dist = if (gps != null) distKm(gps.latitude, gps.longitude, f) else null

        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(Color.WHITE, 18)
            setPadding(dp(18), dp(16), dp(18), dp(8))
        }

        val st = fountainStyle(f.name)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val iconCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(11) }
        }
        iconCol.addView(ic(R.drawable.ic_back, 18, ORANGE_TEXT).apply {
            background = ring(ORANGE, 32, 2); setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(5) }
            contentDescription = "Volver a fuentes"
            setOnClickListener { dlg.dismiss(); selectTab(1) }
        })
        iconCol.addView(iconCircle(st.res, st.bg, st.fg, 44).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        })
        iconCol.addView(ic(R.drawable.ic_close, 12, ORANGE_TEXT).apply {
            background = ring(ORANGE, 28, 2); setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { topMargin = dp(5) }
            contentDescription = "Cerrar"; setOnClickListener { dlg.dismiss() }
        })
        head.addView(iconCol)
        val hcol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        hcol.addView(tv(f.name, 18.5f, DARK, true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
        val cp = cpDe(f.town)
        val townTxt = cp.ifEmpty { f.town } + (dist?.let { String.format(Locale.US, "  ·  %.1f km", it) } ?: "")
        hcol.addView(tv(townTxt, 12.5f, TEXT2).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        })
        head.addView(hcol)
        box.addView(head)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        fun chip(text: String, bg: Int, fg: Int) {
            chips.addView(tv(text, 12f, fg, true).apply {
                background = round(bg, 11); setPadding(dp(9), dp(4), dp(9), dp(4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
            })
        }
        when (acc) {
            "r" -> chip("Asfaltada", GREEN_SOFT, GREEN_TEXT)
            "o" -> chip("Sin asfaltar", ORANGE_SOFT, ORANGE_TEXT)
            else -> chip("Acceso sin datos", SURFACE2, TEXT2)
        }
        chip("Fotos $nFotos/3", if (nFotos > 0) ORANGE_SOFT else SURFACE2, if (nFotos > 0) ORANGE_TEXT else TEXT2)
        box.addView(chips)

        if (note.isNotEmpty()) box.addView(tv(note, 12.5f, TEXT2).apply {
            maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(2), 0, dp(2), dp(6))
        })

        fun action(iconRes: Int, bg: Int, tint: Int, title: String, sub: String, run: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(9), dp(2), dp(9))
            }
            row.addView(iconCircle(iconRes, bg, tint, 34))
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(tv(title, 15f, DARK, true))
            if (sub.isNotEmpty()) col.addView(tv(sub, 12f, TEXT2).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0)
            })
            row.addView(col)
            row.setOnClickListener { dlg.dismiss(); run() }
            box.addView(row)
            box.addView(View(this).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(45) }
            })
        }

        action(R.drawable.ic_flag, ORANGE_SOFT, ORANGE_TEXT, "Poner como salida", "Iniciar la ruta desde esta fuente") {
            startPt = GeoPoint(f.lat, f.lon); startName = f.name; gpsStartOn = false; updRouteInfo(); toast("Salida: " + f.name)
        }
        action(R.drawable.ic_target, ORANGE_SOFT, ORANGE_TEXT, "Poner como destino", "Terminar la ruta en esta fuente") {
            endPt = GeoPoint(f.lat, f.lon); endName = f.name; updRouteInfo(); toast("Destino: " + f.name)
        }
        action(R.drawable.ic_plus, ORANGE_SOFT, ORANGE_TEXT, "Añadir parada", "Máximo 10 paradas en la ruta") {
            if (paradas.size >= 10) toast("Máximo 10 paradas")
            else { paradas.add(GeoPoint(f.lat, f.lon) to f.name); updRouteInfo(); toast("Parada ${paradas.size}/10: " + f.name) }
        }
        action(R.drawable.ic_access, SURFACE2, TEXT2, "Acceso", accLabel(acc)) { cycleAccess(f); showFuenteDialog(f) }
        action(R.drawable.ic_note, SURFACE2, TEXT2, if (note.isEmpty()) "Añadir nota" else "Ver o editar nota", if (note.isEmpty()) "Sin nota guardada" else note) { editNote(f) }
        action(R.drawable.ic_photo, SURFACE2, ORANGE_TEXT, "Fotos", if (nFotos == 0) "Añadir fotos" else "$nFotos de 3 fotos") { openPhoto(f) }

        dlg.setContentView(box)
        dlg.setCanceledOnTouchOutside(false)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dlg.show()
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---------------- FOTOS: camara + galeria, optimizadas, rejilla y visor ----------------
    private var _photoTargetId = -1
    private var _photoSlot = 0
    private var _camFile: File? = null
    private var _camId = -1
    private var _camSlot = 0
    private var photoTreeUri: android.net.Uri? = null
    private val PHOTO_MAX = 3

    private fun cfg() = getSharedPreferences("cfg", MODE_PRIVATE)

    private fun photoDir() = File(filesDir, "photos").apply { mkdirs() }
    private fun thumbDir() = File(photoDir(), "thumb").apply { mkdirs() }
    private fun photoFile(id: Int, slot: Int) = File(photoDir(), "${id}_$slot.jpg")
    private fun thumbFile(id: Int, slot: Int) = File(thumbDir(), "${id}_$slot.jpg")
    private fun photoFiles(id: Int): List<File> = (0 until PHOTO_MAX).map { photoFile(id, it) }.filter { it.exists() }
    private fun nextPhotoSlot(id: Int): Int = (0 until PHOTO_MAX).firstOrNull { !photoFile(id, it).exists() } ?: -1
    private fun slotOf(file: File): Int = file.name.substringAfterLast('_').substringBefore('.').toIntOrNull() ?: 0
    private fun idOf(file: File): Int = file.name.substringBefore('_').toIntOrNull() ?: -1
    private fun getCaption(id: Int, slot: Int): String = getSharedPreferences("notes", MODE_PRIVATE).getString("c_${id}_$slot", "") ?: ""
    private fun photoDate(id: Int, slot: Int): Long = getSharedPreferences("notes", MODE_PRIVATE).getLong("d_${id}_$slot", 0L)
    private fun copyEnabled(): Boolean = photoTreeUri != null && cfg().getBoolean("photoCopyOn", true)

    private fun loadPhotoTree() {
        photoTreeUri = cfg().getString("photoTreeUri", null)?.let { u -> runCatching { android.net.Uri.parse(u) }.getOrNull() }
    }

    private fun treeFolderName(uri: android.net.Uri): String {
        val id = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return "carpeta"
        val p = id.substringAfter(':', id)
        if (p.isBlank()) return "Almacenamiento"
        return p.substringAfterLast('/').ifBlank { p }
    }

    private fun sanitizeName(s: String): String =
        s.replace(Regex("[^A-Za-z0-9\u00E1\u00E9\u00ED\u00F3\u00FA\u00C1\u00C9\u00CD\u00D3\u00DA\u00F1\u00D1_-]+"), "_").trim('_').ifBlank { "fuente" }.take(40)

    private fun fountainName(id: Int): String = fountains.firstOrNull { it.id == id }?.name ?: "fuente"

    private val photoTreePicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {}
        photoTreeUri = uri
        cfg().edit().putString("photoTreeUri", uri.toString()).putBoolean("photoCopyOn", true).putBoolean("photoAsked", true).apply()
        toast("Carpeta de fotos: " + treeFolderName(uri))
        copyAllPhotosToTree()
    }

    private fun createInTree(name: String, mime: String = "image/jpeg"): android.net.Uri? {
        val tree = photoTreeUri ?: return null
        return try {
            val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
            android.provider.DocumentsContract.createDocument(contentResolver, parent, mime, name)
        } catch (_: Exception) { null }
    }

    private fun copyFileToTree(file: File, name: String, mime: String = "image/jpeg"): Boolean {
        val doc = createInTree(name, mime) ?: return false
        return try {
            contentResolver.openOutputStream(doc)?.use { o -> file.inputStream().use { it.copyTo(o) } != null } ?: false
        } catch (e: Exception) {
            try { android.provider.DocumentsContract.deleteDocument(contentResolver, doc) } catch (_: Exception) {}
            false
        }
    }

    private fun copyPhotoToTree(id: Int, slot: Int): Boolean {
        val f = photoFile(id, slot); if (!f.exists()) return false
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        return copyFileToTree(f, "fuente_${id}_${sanitizeName(fountainName(id))}_$stamp.jpg")
    }

    private fun copyAllPhotosToTree() {
        if (photoTreeUri == null) { toast("Elige primero una carpeta"); photoTreePicker.launch(null); return }
        scope.launch {
            var n = 0; var e = 0
            withContext(Dispatchers.IO) {
                photoDir().listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }?.forEach { file ->
                    if (copyPhotoToTree(idOf(file), slotOf(file))) n++ else e++
                }
            }
            toast("Copiadas $n fotos" + if (e > 0) " ($e fallos)" else "")
        }
    }

    private fun photoSettings() {
        val cur = photoTreeUri?.let { treeFolderName(it) } ?: "Solo en la app (sin copia)"
        val copiando = copyEnabled()
        val items = arrayListOf(
            "Carpeta: $cur",
            "\uD83D\uDCC2 Elegir / cambiar carpeta",
            if (copiando) "\u274C Dejar de copiar (solo en la app)" else "\u2705 Activar copia a la carpeta",
            "\uD83D\uDCE4 Copiar fotos actuales a la carpeta"
        )
        android.app.AlertDialog.Builder(this).setTitle("\uD83D\uDCF7 Fotos")
            .setItems(items.toTypedArray()) { _, w ->
                when (w) {
                    1 -> photoTreePicker.launch(null)
                    2 -> { cfg().edit().putBoolean("photoCopyOn", !copiando).apply(); toast(if (!copiando) "Copia a la carpeta activada" else "Solo en la app") }
                    3 -> copyAllPhotosToTree()
                }
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun ensureThumb(id: Int, slot: Int) {
        val th = thumbFile(id, slot); if (th.exists()) return
        val f = photoFile(id, slot); if (!f.exists()) return
        try {
            val b = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return
            val s = 300f / maxOf(b.width, b.height)
            val tb = if (s < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else b
            th.outputStream().use { tb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }
            if (tb != b) tb.recycle()
            b.recycle()
        } catch (_: Exception) {}
    }

    private fun importPhoto(id: Int, slot: Int, open: () -> java.io.InputStream?): Boolean {
        return try {
            val probe = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, probe) }
            if (probe.outWidth <= 0 || probe.outHeight <= 0) return false
            var sample = 1
            while (probe.outWidth / (sample * 2) >= 1600 && probe.outHeight / (sample * 2) >= 1600) sample *= 2
            val dec = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, dec) } ?: return false
            val orientation = open()?.use {
                try { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
            } ?: 1
            val m = android.graphics.Matrix()
            when (orientation) {
                6 -> m.postRotate(90f)
                3 -> m.postRotate(180f)
                8 -> m.postRotate(270f)
                2 -> m.postScale(-1f, 1f)
                5 -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                7 -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (!m.isIdentity) {
                val r = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r != bmp) { bmp.recycle(); bmp = r }
            }
            val big = maxOf(bmp.width, bmp.height)
            if (big > 1600) {
                val s = 1600f / big
                val r = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
                if (r != bmp) { bmp.recycle(); bmp = r }
            }
            val out = photoFile(id, slot)
            val tmp = File(out.parentFile, out.name + ".tmp")
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
            val ts = 300f / maxOf(bmp.width, bmp.height)
            val tb = if (ts < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * ts).toInt().coerceAtLeast(1), (bmp.height * ts).toInt().coerceAtLeast(1), true) else bmp
            val th = thumbFile(id, slot)
            val ttmp = File(th.parentFile, th.name + ".tmp")
            ttmp.outputStream().use { tb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }
            if (th.exists()) th.delete()
            if (!ttmp.renameTo(th)) { ttmp.copyTo(th, overwrite = true); ttmp.delete() }
            if (tb != bmp) tb.recycle()
            bmp.recycle()
            getSharedPreferences("notes", MODE_PRIVATE).edit().putLong("d_${id}_$slot", System.currentTimeMillis()).apply()
            true
        } catch (_: Exception) { false }
    }

    private fun savePhoto(id: Int, slot: Int, open: () -> java.io.InputStream?, cleanup: () -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { importPhoto(id, slot, open) } finally { try { cleanup() } catch (_: Exception) {} }
            }
            if (!ok) { toast("No se pudo guardar la foto"); return@launch }
            toast("Foto guardada (${photoFiles(id).size}/3)")
            if (copyEnabled() && !withContext(Dispatchers.IO) { copyPhotoToTree(id, slot) }) toast("Aviso: no se pudo copiar a la carpeta")
            if (!cfg().getBoolean("photoAsked", false)) maybeAskPhotoFolder()
        }
    }

    private fun maybeAskPhotoFolder() {
        if (cfg().getBoolean("photoAsked", false)) return
        cfg().edit().putBoolean("photoAsked", true).apply()
        android.app.AlertDialog.Builder(this).setTitle("\u00BFDonde guardar las fotos?")
            .setMessage("Puedes guardar una copia de las fotos en una carpeta del m\u00F3vil (memoria interna o tarjeta SD). La copia de la app se guarda siempre.")
            .setPositiveButton("Elegir carpeta") { _, _ -> photoTreePicker.launch(null) }
            .setNegativeButton("Solo en la app", null).show()
    }

    private val photoPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && _photoTargetId >= 0) {
            val id = _photoTargetId; val slot = _photoSlot
            savePhoto(id, slot, { contentResolver.openInputStream(uri) })
        }
    }

    private val photoCamera = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        val f = _camFile; _camFile = null
        if (ok && f != null && f.exists() && _camId >= 0) {
            val id = _camId; val slot = _camSlot
            savePhoto(id, slot, { java.io.FileInputStream(f) }, { f.delete() })
        } else { f?.delete(); toast("Foto cancelada") }
    }

    private fun askAddPhoto(f: Fountain) {
        if (photoFiles(f.id).size >= PHOTO_MAX) { toast("M\u00E1ximo 3 fotos"); return }
        val slot = nextPhotoSlot(f.id)
        if (slot < 0) { toast("M\u00E1ximo 3 fotos"); return }

        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(Color.WHITE, 18)
            setPadding(dp(18), dp(16), dp(18), dp(10))
        }

        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val iconCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(11) }
        }
        iconCol.addView(ic(R.drawable.ic_back, 18, ORANGE_TEXT).apply {
            background = ring(ORANGE, 32, 2); setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(5) }
            contentDescription = "Volver a la fuente"
            setOnClickListener { dlg.dismiss(); showFuenteDialog(f) }
        })
        iconCol.addView(ic(R.drawable.ic_close, 12, ORANGE_TEXT).apply {
            background = ring(ORANGE, 28, 2); setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            contentDescription = "Cerrar"; setOnClickListener { dlg.dismiss() }
        })
        head.addView(iconCol)
        val hcol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        hcol.addView(tv("A\u00F1adir foto", 18f, DARK, true))
        hcol.addView(tv(f.name, 12.5f, TEXT2).apply {
            maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        })
        head.addView(hcol)
        box.addView(head)
        box.addView(View(this).apply {
            setBackgroundColor(DIVIDER)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(2) }
        })

        fun row(iconRes: Int, bg: Int, tint: Int, title: String, sub: String, run: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(10), dp(2), dp(10))
            }
            r.addView(iconCircle(iconRes, bg, tint, 34))
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            c.addView(tv(title, 15f, DARK, true))
            c.addView(tv(sub, 12f, TEXT2).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0)
            })
            r.addView(c)
            r.setOnClickListener { dlg.dismiss(); run() }
            box.addView(r)
            box.addView(View(this).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(45) }
            })
        }

        fun takePhoto() {
            _photoTargetId = f.id; _photoSlot = slot
            val dir = File(cacheDir, "cam").apply { mkdirs() }
            val cf = File(dir, "cam_${f.id}_${slot}_${System.currentTimeMillis()}.jpg")
            _camFile = cf; _camId = f.id; _camSlot = slot
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", cf)
                photoCamera.launch(uri)
            } catch (e: Exception) { toast("No se pudo abrir la c\u00E1mara: ${e.message}") }
        }

        row(R.drawable.ic_camera, ORANGE_SOFT, ORANGE_TEXT, "Hacer foto", "Abrir la c\u00E1mara") { takePhoto() }
        row(R.drawable.ic_gallery, SURFACE2, TEXT2, "Elegir de galer\u00EDa", "Desde las fotos del m\u00F3vil") {
            _photoTargetId = f.id; _photoSlot = slot
            photoPicker.launch("image/*")
        }

        dlg.setContentView(box)
        dlg.setCanceledOnTouchOutside(false)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dlg.show()
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun openPhoto(f: Fountain) {
        if (photoFiles(f.id).isEmpty()) { askAddPhoto(f); return }
        val files = photoFiles(f.id)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(4)) }
        val grid = android.widget.GridLayout(this).apply { columnCount = 3 }
        val cellPx = (resources.displayMetrics.widthPixels - dp(24)) / 3
        files.forEachIndexed { idx, file ->
            val slot = slotOf(file)
            ensureThumb(f.id, slot)
            val cell = FrameLayout(this)
            val src = if (thumbFile(f.id, slot).exists()) thumbFile(f.id, slot) else file
            val iv = ImageView(this).apply {
                setImageBitmap(android.graphics.BitmapFactory.decodeFile(src.absolutePath))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cell.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val badge = tv("${slot + 1}", 11f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = dot(ORANGE, 20)
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END).apply { topMargin = dp(4); rightMargin = dp(4) }
            }
            cell.addView(badge)
            cell.layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = cellPx - dp(6); height = cellPx - dp(6)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            cell.setOnClickListener { showPhotoViewer(f, idx) }
            grid.addView(cell)
        }
        box.addView(grid)
        val dlg = android.app.AlertDialog.Builder(this).setTitle("\uD83D\uDCF7 ${f.name}")
            .setView(box)
            .setPositiveButton("A\u00F1adir foto", null)
            .setNegativeButton("Cerrar", null)
        val a = dlg.create()
        a.setOnShowListener {
            a.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { a.dismiss(); askAddPhoto(f) }
        }
        a.show()
    }

    private fun textPrompt(title: String, sub: String, previa: String, onSaved: (String) -> Unit, onDelete: (() -> Unit)? = null) {
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(Color.WHITE, 18)
            setPadding(dp(18), dp(16), dp(18), dp(14))
        }
        val ed = EditText(this).apply {
            setText(previa); setSelection(text.length)
            minHeight = dp(52); isSingleLine = true
            textSize = 15f
            setTextColor(DARK); setHintTextColor(TEXT2)
            hint = "Escribe aqu\u00ED"
            background = round(SURFACE2, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        fun save() { onSaved(ed.text.toString()); dlg.dismiss() }

        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ic(R.drawable.ic_back, 18, ORANGE_TEXT).apply {
            background = ring(ORANGE, 32, 2); setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(11) }
            contentDescription = "Cancelar"
            setOnClickListener { dlg.dismiss() }
        })
        val hcol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        hcol.addView(tv(title, 18f, DARK, true))
        if (sub.isNotEmpty()) hcol.addView(tv(sub, 12.5f, TEXT2).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        })
        head.addView(hcol)
        head.addView(ic(R.drawable.ic_check, 16, ORANGE_TEXT).apply {
            background = ring(ORANGE, 36, 2); setPadding(dp(9), dp(9), dp(9), dp(9))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(8) }
            contentDescription = "Guardar"
            setOnClickListener { save() }
        })
        box.addView(head)

        box.addView(ed, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        if (onDelete != null) {
            box.addView(tv("Borrar", 13.5f, ORANGE_TEXT, true).apply {
                setPadding(dp(2), dp(12), dp(2), dp(2))
                setOnClickListener { dlg.dismiss(); onDelete() }
            })
        }

        dlg.setContentView(box)
        dlg.setCanceledOnTouchOutside(false)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dlg.show()
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        ed.requestFocus()
    }

    private fun editCaption(f: Fountain, slot: Int, onDone: () -> Unit = {}) {
        val previa = getCaption(f.id, slot)
        val prefs = getSharedPreferences("notes", MODE_PRIVATE)
        textPrompt("Pie de foto", f.name, previa, onSaved = { txt ->
            prefs.edit().putString("c_${f.id}_$slot", txt).apply()
            toast(if (txt.isBlank()) "Pie borrado" else "Pie guardado")
            onDone()
        }, onDelete = if (previa.isNotEmpty()) ({ prefs.edit().remove("c_${f.id}_$slot").apply(); toast("Pie borrado"); onDone() }) else null)
    }

    private fun rotatePhoto(id: Int, slot: Int) {
        try {
            val f = photoFile(id, slot)
            val b = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return
            val m = android.graphics.Matrix().apply { postRotate(90f) }
            val r = android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            f.outputStream().use { r.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
            if (r != b) r.recycle()
            b.recycle()
            thumbFile(id, slot).delete()
            ensureThumb(id, slot)
        } catch (_: Exception) {}
    }

    private fun saveToGallery(file: File): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "fuente_" + file.name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RutasFuentes")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } } ?: return false
            values.clear(); values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            true
        } catch (_: Exception) { false }
    }

    private fun confirmDeletePhotoAt(f: Fountain, slot: Int, onGone: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle("\uD83D\uDDD1 Borrar foto")
            .setMessage("Se borrar\u00E1 esta foto de \"${f.name}\". \u00BFSeguro?")
            .setPositiveButton("Borrar") { _, _ ->
                val prefs = getSharedPreferences("notes", MODE_PRIVATE)
                photoFile(f.id, slot).delete(); thumbFile(f.id, slot).delete()
                prefs.edit().remove("c_${f.id}_$slot").remove("d_${f.id}_$slot").apply()
                for (s in slot + 1 until PHOTO_MAX) {
                    val fromF = photoFile(f.id, s); val toF = photoFile(f.id, s - 1)
                    if (fromF.exists()) { toF.delete(); fromF.renameTo(toF) }
                    val fromT = thumbFile(f.id, s); val toT = thumbFile(f.id, s - 1)
                    if (fromT.exists()) { toT.delete(); fromT.renameTo(toT) }
                    val e2 = prefs.edit()
                    val cap = prefs.getString("c_${f.id}_$s", null)
                    val dt = prefs.getLong("d_${f.id}_$s", 0L)
                    if (cap != null) e2.putString("c_${f.id}_${s - 1}", cap) else e2.remove("c_${f.id}_${s - 1}")
                    if (dt > 0) e2.putLong("d_${f.id}_${s - 1}", dt) else e2.remove("d_${f.id}_${s - 1}")
                    e2.remove("c_${f.id}_$s").remove("d_${f.id}_$s").apply()
                }
                toast("Foto borrada (quedan ${photoFiles(f.id).size}/3)")
                onGone()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showPhotoViewer(f: Fountain, startIndex: Int) {
        val files = photoFiles(f.id)
        if (files.isEmpty()) return
        var idx = startIndex.coerceIn(0, files.size - 1)
        val dlg = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val rootView = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val zoom = ZoomImageView(this)
        rootView.addView(zoom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val top = tv("", 13f, Color.WHITE, true).apply { setPadding(dp(12), dp(6), dp(12), dp(4)); gravity = Gravity.CENTER }
        rootView.addView(top, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply { topMargin = dp(26) })
        val close = tv("\u2715", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = dot(Color.parseColor("#66000000"), 38)
            setOnClickListener { dlg.dismiss() }
        }
        rootView.addView(close, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP or Gravity.END).apply { topMargin = dp(24); rightMargin = dp(12) })
        val navId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navH = if (navId > 0) resources.getDimensionPixelSize(navId) else 0
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(8), dp(6), navH + dp(10)) }
        fun b(label: String, onClick: () -> Unit) = tv(label, 11.5f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = round(Color.parseColor("#33FFFFFF"), 16); setPadding(0, dp(9), 0, dp(9))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
            setOnClickListener { onClick() }
        }
        rootView.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        fun show(n: Int) {
            if (files.isEmpty()) { dlg.dismiss(); return }
            idx = ((n % files.size) + files.size) % files.size
            val file = files[idx]
            val slot = slotOf(file)
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) zoom.setPhoto(bmp)
            val d0 = photoDate(f.id, slot)
            val cap = getCaption(f.id, slot)
            val fecha = if (d0 > 0) " \u00B7 " + java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date(d0)) else ""
            top.text = "${idx + 1}/${files.size}" + fecha + if (cap.isEmpty()) "" else "\n$cap"
        }
        zoom.onSwipe = { dx -> show(if (dx < 0) idx + 1 else idx - 1) }
        bar.addView(b("\uD83D\uDCAC Pie") { val i = idx; val slot = slotOf(files[i]); dlg.dismiss(); editCaption(f, slot) { showPhotoViewer(f, i) } })
        bar.addView(b("\u21BB Rotar") {
            val slot = slotOf(files[idx])
            scope.launch { withContext(Dispatchers.IO) { rotatePhoto(f.id, slot) }; show(idx) }
        })
        bar.addView(b("\uD83D\uDCE4 Enviar") {
            val file = files[idx]
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/jpeg"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(i, "Compartir foto"))
            } catch (e: Exception) { toast("Error: ${e.message}") }
        })
        bar.addView(b("\uD83D\uDDBC Galer\u00EDa") {
            val file = files[idx]
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveToGallery(file) }
                if (ok) toast("Foto guardada en Pictures/RutasFuentes") else toast("Usa Enviar para guardarla o compartirla")
            }
        })
        bar.addView(b("\uD83D\uDDD1 Borrar") { val i = idx; val slot = slotOf(files[i]); dlg.dismiss(); confirmDeletePhotoAt(f, slot) { if (photoFiles(f.id).isNotEmpty()) openPhoto(f) } })
        dlg.setContentView(rootView)
        dlg.show()
        show(idx)
    }

    // Visor con zoom (pinch + doble toque) y arrastre cuando esta ampliado
    private class ZoomImageView(ctx: android.content.Context) : androidx.appcompat.widget.AppCompatImageView(ctx) {
        private val m = android.graphics.Matrix()
        private var lastX = 0f
        private var lastY = 0f
        var onSwipe: ((Float) -> Unit)? = null
        private val scaleDet = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                var f = d.scaleFactor
                val cur = currentScale()
                if (cur * f < 1f) f = 1f / cur
                if (cur * f > 6f) f = 6f / cur
                m.postScale(f, f, d.focusX, d.focusY)
                imageMatrix = m
                return true
            }
        })
        private val tapDet = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                if (currentScale() > 1.05f) reset() else { m.postScale(2.2f, 2.2f, e.x, e.y); imageMatrix = m }
                return true
            }
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                if (currentScale() > 1.05f) return false
                val x1 = e1?.x ?: return false
                val dx = e2.x - x1; val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > dp(60f) && kotlin.math.abs(dx) > kotlin.math.abs(dy)) { onSwipe?.invoke(dx); return true }
                return false
            }
        })
        init { scaleType = ImageView.ScaleType.MATRIX }
        private fun dp(v: Float) = v * resources.displayMetrics.density
        private fun currentScale(): Float { val a = FloatArray(9); m.getValues(a); return a[android.graphics.Matrix.MSCALE_X] }
        fun setPhoto(b: android.graphics.Bitmap) { setImageBitmap(b); post { reset() } }
        fun reset() {
            val d = drawable ?: return
            val vw = width.toFloat(); val vh = height.toFloat()
            val bw = d.intrinsicWidth.toFloat(); val bh = d.intrinsicHeight.toFloat()
            if (bw <= 0f || bh <= 0f || vw <= 0f) return
            val s = minOf(vw / bw, vh / bh)
            m.reset(); m.postScale(s, s); m.postTranslate((vw - bw * s) / 2f, (vh - bh * s) / 2f)
            imageMatrix = m
        }
        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            scaleDet.onTouchEvent(ev)
            tapDet.onTouchEvent(ev)
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { lastX = ev.x; lastY = ev.y }
                android.view.MotionEvent.ACTION_MOVE -> if (currentScale() > 1.05f && ev.pointerCount == 1) {
                    m.postTranslate(ev.x - lastX, ev.y - lastY); imageMatrix = m; lastX = ev.x; lastY = ev.y
                }
            }
            return true
        }
    }

    // ---------------- NOTAS ----------------
    private fun getNote(id: Int): String = getSharedPreferences("notes", MODE_PRIVATE).getString("n_$id", "") ?: ""
    private fun editNote(f: Fountain) {
        val previa = getNote(f.id)
        val prefs = getSharedPreferences("notes", MODE_PRIVATE)
        textPrompt("Nota", f.name, previa, onSaved = { txt ->
            prefs.edit().putString("n_${f.id}", txt).apply()
            toast(if (txt.isBlank()) "Nota vac\u00EDa (se ha guardado vac\u00EDa)" else "Nota guardada")
        }, onDelete = if (previa.isNotEmpty()) ({ confirmDeleteNote(f) }) else null)
    }

    private fun confirmDeleteNote(f: Fountain) {
        android.app.AlertDialog.Builder(this)
            .setTitle("🗑️ Borrar nota")
            .setMessage("Se borrará la nota de \"${f.name}\". ¿Seguro?")
            .setPositiveButton("Borrar") { _, _ ->
                getSharedPreferences("notes", MODE_PRIVATE).edit().remove("n_${f.id}").apply()
                toast("Nota borrada")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun updRouteInfo() {
        // Al definir la salida, una parada o el destino se abre solo el menu Ruta (si estaba cerrado por estar vacio)
        val has = startPt != null || endPt != null || paradas.isNotEmpty()
        if (has && routePanelEmpty) routePanelHidden = false
        routePanelEmpty = !has
        routeInfoHeader.removeAllViews()
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r1.addView(tv("🏁 " + (startName.ifEmpty { "Salida" }) + "  🔍", 13.5f, DARK, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { searchTarget = "start"; openSearch() }
        })
        if (startPt == null) gpsStartOn = false
        gpsStartBtn = smallBtn("\uD83D\uDCCD GPS") { useGpsAsStart() }
        if (gpsStartOn) gpsStartBtn?.background = round(GREEN_FUENTE, 16)
        r1.addView(gpsStartBtn)
        manualStartBtn = smallBtn("\u270B Manual") { startManualPick("start") }
        r1.addView(manualStartBtn)
        routeInfoHeader.addView(r1)
        routeInfoHeader.addView(thinBlueLine())
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, 0) }
        r2.addView(tv("\u2795 Paradas: " + paradas.size + "/10  \uD83D\uDD0D", 13.5f, DARK, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { if (paradas.isEmpty()) selectTab(1) else paradasDialog() }
        })
        manualParadaBtn = smallBtn("\u270B Manual") {
            if (paradas.size >= 10 && manualPick != "parada") toast("M\u00E1ximo 10 paradas")
            else startManualPick("parada")
        }
        r2.addView(manualParadaBtn)
        routeInfoHeader.addView(r2)
        routeInfoHeader.addView(thinBlueLine())
        val r3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, 0) }
        r3.addView(tv("🎯 " + (endName.ifEmpty { "Destino" }) + "  🔍", 13.5f, DARK, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { searchTarget = "dest"; openSearch() }
        })
        manualDestBtn = smallBtn("\u270B Manual") { startManualPick("dest") }
        r3.addView(manualDestBtn)
        routeInfoHeader.addView(r3)
        routeInfoHeader.addView(thinBlueLine())
        routeInfo.visibility = if (!routePanelHidden && map.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (routeInfo.visibility == View.VISIBLE) routeInfo.bringToFront()
        routeInfo.post { layoutFab() }
        syncBarRings()
        updateRoutePointMarkers()
        for (i in 0 until routeInfoHeader.childCount) {
            val row = routeInfoHeader.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val v = row.getChildAt(j)
                val lp = v.layoutParams as? LinearLayout.LayoutParams
                if (v is TextView && lp != null && lp.width == 0) {
                    v.maxLines = 1; v.ellipsize = android.text.TextUtils.TruncateAt.END
                }
            }
        }
    }

    private fun smallBtn(text: String, onClick: () -> Unit): TextView = tv(text, 12f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; background = round(ORANGE, 16); setPadding(dp(10), dp(6), dp(10), dp(6))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) }
        setOnClickListener { onClick() }
    }

    // Separador fino azul entre Salida / Paradas / Destino
    private fun thinBlueLine(): View = View(this).apply {
        setBackgroundColor(ROUTE_BLUE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(2) }
    }

    // ---------------- SELECCION MANUAL (diana central + boton +) ----------------
    private var crossView: View? = null
    private var crossPlus: View? = null
    private var manualStartBtn: TextView? = null
    private var manualParadaBtn: TextView? = null
    private var manualDestBtn: TextView? = null

    private fun ensureCrossUi() {
        if (crossView != null) return
        val cv = object : View(this) {
            override fun onDraw(c: android.graphics.Canvas) {
                val r = width / 2f
                val cx = width / 2f; val cy = height / 2f
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                p.style = android.graphics.Paint.Style.STROKE
                val inner = r * 0.56f
                p.color = 0xFF9E9E9E.toInt(); p.alpha = 158; p.strokeWidth = r - inner
                c.drawCircle(cx, cy, (r + inner) / 2f, p)
                p.alpha = 255; p.color = Color.BLACK; p.strokeWidth = dp(2).toFloat()
                c.drawCircle(cx, cy, r * 0.58f, p)
                p.strokeCap = android.graphics.Paint.Cap.ROUND
                c.drawLine(cx - r * 0.94f, cy, cx + r * 0.94f, cy, p)
                c.drawLine(cx, cy - r * 0.94f, cx, cy + r * 0.94f, p)
            }
        }
        cv.isClickable = false
        crossView = cv
        root.addView(cv, FrameLayout.LayoutParams(dp(62), dp(62)))
        val plus = TextView(this).apply {
            text = "+"; setTextSize(24f); setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; background = dot(0xFF2ECC71.toInt(), 46)
            setOnClickListener { pickFromCrosshair() }
        }
        crossPlus = plus
        root.addView(plus, FrameLayout.LayoutParams(dp(46), dp(46)))
        showCross(false)
    }

    private fun showCross(on: Boolean) {
        ensureCrossUi()
        val v = if (on) View.VISIBLE else View.GONE
        if (on) placeCross()
        listOfNotNull(crossView, crossPlus).forEach { it.visibility = v; if (on) it.bringToFront() }
    }

    // Diana en el centro de la pantalla y "+" al filo derecho, a su misma altura
    private fun placeCross() {
        val cv = crossView ?: return
        val plus = crossPlus ?: return
        if (root.width == 0) return
        val cx = root.width / 2
        val cy = root.height / 2
        cv.layoutParams = FrameLayout.LayoutParams(dp(62), dp(62)).apply {
            leftMargin = cx - dp(31); topMargin = cy - dp(31)
        }
        plus.layoutParams = FrameLayout.LayoutParams(dp(46), dp(46)).apply {
            leftMargin = root.width - dp(54); topMargin = cy - dp(23)
        }
    }

    // Punto del mapa que hay bajo el centro de la diana
    private fun pickFromCrosshair() {
        val cv = crossView ?: return
        if (cv.visibility != View.VISIBLE) return
        val a = IntArray(2); cv.getLocationOnScreen(a)
        val b = IntArray(2); map.getLocationOnScreen(b)
        val px = a[0] - b[0] + cv.width / 2
        val py = a[1] - b[1] + cv.height / 2
        val gp = map.projection.fromPixels(px, py) ?: return
        applyPickPoint(GeoPoint(gp.latitude, gp.longitude))
    }

    // El boton Manual es un interruptor: pulsa para activar la diana (verde) y otra vez para quitarla
    private fun cancelManualPick() {
        if (manualPick == null) return
        manualPick = null; showCross(false); styleManualBtns()
    }

    private fun startManualPick(mode: String) {
        if (manualPick == mode) {
            manualPick = null
            showCross(false)
            styleManualBtns()
            return
        }
        if (mode == "parada" && paradas.size >= 10) { toast("M\u00E1ximo 10 paradas"); return }
        manualPick = mode
        showCross(true)
        styleManualBtns()
        val t = when (mode) { "start" -> "SALIDA"; "parada" -> "PARADA"; else -> "DESTINO" }
        toast("Mueve el mapa y pulsa + para fijar la " + t)
    }

    private fun styleManualBtns() {
        fun st(b: TextView?, mode: String) {
            b?.background = round(if (manualPick == mode) GREEN_FUENTE else ORANGE, 16)
            b?.alpha = if (manualPick == null || manualPick == mode) 1f else 0.55f
        }
        st(manualStartBtn, "start"); st(manualParadaBtn, "parada"); st(manualDestBtn, "dest")
    }

    // Salida y Destino: un solo punto (se sustituye). Paradas: hasta 10.
    private fun applyPickPoint(p: GeoPoint) {
        when (manualPick) {
            "start" -> { startPt = p; startName = "\uD83D\uDCCD Punto en el mapa"; gpsStartOn = false; toast("Salida fijada en el mapa") }
            "dest" -> { endPt = p; endName = "\uD83D\uDCCD Punto en el mapa"; toast("Destino fijado en el mapa") }
            "parada" -> {
                if (paradas.size >= 10) toast("M\u00E1ximo 10 paradas")
                else { paradas.add(p to "\uD83D\uDCCD Punto en el mapa"); toast("Parada ${paradas.size}/10") }
            }
        }
        updRouteInfo()
        styleManualBtns()
        if (routePts.isNotEmpty() && startPt != null && endPt != null) calcRoute()
    }

    // GPS de la Salida como interruptor: activo = verde; otra pulsacion lo quita
    private fun useGpsAsStart() {
        if (gpsStartOn) {
            gpsStartOn = false
            startPt = null; startName = ""
            if (routePts.isNotEmpty()) { routePts.clear(); stepPts.clear(); steps.clear(); hideRouteStats(); drawRoute() }
            updRouteInfo(); toast("Salida GPS desactivada")
            return
        }
        val p = myLoc.myLocation
        if (p == null) { myLoc.enableMyLocation(); toast("Esperando GPS. vuelve a pulsar"); return }
        startPt = p; startName = ""; gpsStartOn = true
        updRouteInfo(); toast("Salida: mi ubicaci\u00F3n")
    }

    // Splash de arranque: imagen a pantalla completa + sonido (res/raw/arranque.mp3 u .ogg)
    private var soundMs = 0

    private fun showSplashAndSound() {
        if (splashDone) return
        splashDone = true
        try {
            val v = ImageView(this).apply {
                setImageResource(R.drawable.splash)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.parseColor("#F7F7F7"))
            }
            val fade = Runnable {
                v.animate().alpha(0f).setDuration(600).withEndAction { try { root.removeView(v) } catch (_: Exception) { } }.start()
            }
            v.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    try { v.viewTreeObserver.removeOnPreDrawListener(this) } catch (_: Exception) { }
                    playStartSound()
                    // El splash dura lo mismo que el audio (salvo el fundido final)
                    val total = if (soundMs > 0) soundMs.toLong() else 4000L
                    main.postDelayed(fade, (total - 600).coerceAtLeast(800))
                    return true
                }
            })
            root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            main.postDelayed(fade, 15000) // seguridad
        } catch (e: Exception) { playStartSound() }
    }

    // Sonido: app/src/main/res/raw/arranque.mp3 (o .ogg)
    private fun playStartSound() {
        try {
            val sid = resources.getIdentifier("arranque", "raw", packageName)
            if (sid != 0) {
                val mp = android.media.MediaPlayer.create(this, sid)
                if (mp != null) {
                    soundMs = mp.duration
                    mp.setOnCompletionListener { it.release() }
                    mp.start()
                }
            }
        } catch (_: Exception) { }
    }

    private fun layoutFab() {
        val barH = if (bottomBar.height > 0) bottomBar.height else dp(72)
        val m = barH + dp(8)
        (routeStatsRow.layoutParams as FrameLayout.LayoutParams).bottomMargin = barH
        routeStatsRow.requestLayout()
        val st = if (routeStatsRow.visibility == View.VISIBLE) routeStatsRow.height else 0
        val fabBase = barH + st + dp(8)
        // El panel de Ruta queda pegado al filo superior de diana/modo
        val clear = dp(46)
        (routeInfo.layoutParams as FrameLayout.LayoutParams).bottomMargin = m + st + clear; routeInfo.requestLayout()
        (gpsCenterBtn.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = fabBase; gpsCenterBtn.layoutParams = it }
        (centrarBtn.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = fabBase; centrarBtn.layoutParams = it }
        for (p in listOf(panelFuentes, panelRecord, panelMore, panelRuta)) {
            (p.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = barH; p.layoutParams = it }
        }
        val topY = if (topBar.height > 0) topBar.height else dp(110)
        (navBanner.layoutParams as? FrameLayout.LayoutParams)?.let { it.topMargin = topY + dp(8); navBanner.layoutParams = it }
        val sy = if (navBanner.visibility == View.VISIBLE && navBanner.height > 0) topY + dp(8) + navBanner.height + dp(8) else topY + dp(8)
        (statusText.layoutParams as? FrameLayout.LayoutParams)?.let { it.topMargin = sy; statusText.layoutParams = it }
    }

    private fun hideRouteStats() {
        routeStatsText = ""
        routeTotalKm = 0.0; routeTotalMin = 0.0
        routeStatsRow.visibility = View.GONE
        routeStats.post { layoutFab() }
    }

    // Distancia que queda por la ruta desde el punto más cercano a mi posición
    private fun routeRemainingMeters(): Double? {
        if (routePts.isEmpty() || endPt == null) return null
        val my = myLoc.myLocation ?: return null
        var bi = 0; var bd = Double.MAX_VALUE
        routePts.forEachIndexed { i, q -> val d = q.distanceToAsDouble(my); if (d < bd) { bd = d; bi = i } }
        var sum = 0.0
        for (i in bi until routePts.size - 1) sum += routePts[i].distanceToAsDouble(routePts[i + 1])
        sum += routePts.last().distanceToAsDouble(endPt!!)
        return sum
    }

    // Texto de la franja inferior: lo que queda desde mi posición (o el total si aún no hay GPS)
    private fun styleStats() {
        val t = routeStatsText
        if (t.isEmpty()) return
        val sp = android.text.SpannableString(t)
        val i = t.lastIndexOf("·")
        if (i > 0) {
            sp.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#FFB374")), 0, i, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#8BE6B0")), i, t.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        routeStats.text = sp
    }

    private fun fmtRouteStats(): String {
        val rem = routeRemainingMeters()
        if (rem != null) {
            val km = rem / 1000.0
            val mins = if (routeTotalKm > 0.0) routeTotalMin * (km / routeTotalKm) else 0.0
            val m = mins.toInt(); val hh = m / 60; val mm = m % 60
            val t = if (hh > 0) "$hh h $mm min" else "$mm min"
            return String.format(Locale.US, "🛣️ Faltan %.1f km · %s", km, t)
        }
        if (routeTotalKm <= 0.0) return ""
        val mm = routeTotalMin.toInt(); val hh = mm / 60
        val t = if (hh > 0) "$hh h ${mm % 60} min" else "$mm min"
        return String.format(Locale.US, "🛣️ %.0f km · %s", routeTotalKm, t)
    }

    // ---------------- CAPAS ----------------
    private var layerIdx = 0
    private val topoSource = org.osmdroid.tileprovider.tilesource.XYTileSource("Topo", 0, 17, 256, ".png", arrayOf("https://a.tile.opentopomap.org/"), "© OpenTopoMap")
    private val cycloSource = org.osmdroid.tileprovider.tilesource.XYTileSource("Cyclo", 0, 18, 256, ".png", arrayOf("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/"), "© CyclOSM")
    private val satSource = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase("Satélite", 0, 19, 256, "", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"), "© Esri") {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
            val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
            return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        }
    }
    // NASA GIBS: satélite diario true-color (nubes visibles), sin clave. Solo hasta zoom 9.
    private val gibsDate: String = run {
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val offset = if (utc.get(java.util.Calendar.HOUR_OF_DAY) >= 12) 0L else 86400000L
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(System.currentTimeMillis() - offset))
    }
    private val gibsSource = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase("NASA_$gibsDate", 0, 9, 256, "", arrayOf("https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/MODIS_Terra_CorrectedReflectance_TrueColor/default/"), "© NASA GIBS") {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
            val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
            return "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/MODIS_Terra_CorrectedReflectance_TrueColor/default/$gibsDate/GoogleMapsCompatible_Level9/$z/$y/$x.jpg"
        }
    }
    private val layerNames = listOf("Mapa", "Relieve", "Bici", "Satélite", "NASA (nubes)")
    private fun cycleLayer() { layerIdx = (layerIdx + 1) % 5; applyLayer() }
    // Comportamiento por tipo de mapa (estilo Google Maps):
    //  - Satelite: el mapa gira con la brujula
    //  - Mapa y Relieve: norte arriba, rosa visible
    //  - Bici: norte arriba y sin rosa (deja ver las rutas)
    //  - NASA: sin rosa
    private fun layerShowsRose(): Boolean = map.visibility == View.VISIBLE && layerIdx != 4 && compassRoseOn
    private fun applyLayer() {
        map.setTileSource(when (layerIdx) { 0 -> TileSourceFactory.MAPNIK; 1 -> topoSource; 2 -> cycloSource; 3 -> satSource; else -> gibsSource })
        lastRoseDeg = -1
        applyHeading()
        if (layerShowsRose()) map.post { placeRoseOnMap() } else roseCard?.visibility = View.INVISIBLE
        if (layerIdx == 4) {
            lastLoc?.let { map.controller.setCenter(it) }
            if (map.zoomLevelDouble > 7.0) map.controller.setZoom(7.0)
            toast("Capa: NASA (nubes) \u00B7 zoom 7 (tarda unos segundos)")
        } else {
            toast("Capa: " + layerNames[layerIdx] + if (compassRoseOn) " \u00B7 Norte fijo" else " \u00B7 sin rosa")
        }
    }

    // ---------------- RADAR DE LLUVIA (overlay, sin clave) ----------------
    // RainViewer solo sirve el radar hasta el zoom 7; fuera de ese rango se oculta (sin avisos).
    private var radarEnabled = false
    private var radarOverlay: org.osmdroid.views.overlay.TilesOverlay? = null
    private var radarPath: String? = null
    private var radarListenerAdded = false
    private fun refreshRadar(silent: Boolean) {
        val p = radarPath ?: return
        if (!silent) lastLoc?.let { map.controller.setCenter(it) }
        if (!radarListenerAdded) {
            radarListenerAdded = true
            map.addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean { if (radarEnabled) refreshRadar(true); return false }
            })
        }
        var rz = map.zoomLevelDouble
        if (radarEnabled && !silent && rz > 7.4) { map.controller.setZoom(7.0); rz = 7.0 }
        if (radarEnabled && rz <= 7.4) {
            if (radarOverlay == null) {
                val prov = org.osmdroid.tileprovider.MapTileProviderBasic(this)
                prov.setTileSource(org.osmdroid.tileprovider.tilesource.XYTileSource("Radar", 0, 7, 256, "/2/1_1.png", arrayOf("https://tilecache.rainviewer.com$p/256/"), "© RainViewer"))
                val ov = org.osmdroid.views.overlay.TilesOverlay(prov, this)
                ov.setLoadingBackgroundColor(Color.TRANSPARENT)
                radarOverlay = ov; map.overlays.add(ov); map.invalidate()
            }
            if (!silent) toast("🌧️ Radar activado (zoom 7: limite de RainViewer)")
        } else {
            radarOverlay?.let { map.overlays.remove(it) }; radarOverlay = null
            if (!silent && radarEnabled) toast("🌧️ Aleja el zoom (≤ 7) para ver el radar: RainViewer solo llega al 7")
        }
        map.invalidate()
    }
    private fun toggleRadar() {
        if (radarEnabled) { radarEnabled = false; radarOverlay?.let { map.overlays.remove(it) }; radarOverlay = null; map.invalidate(); toast("🌧️ Radar desactivado"); return }
        if (radarPath != null) { radarEnabled = true; refreshRadar(false); return }
        statusText.text = "🌧️ Cargando radar…"; statusText.visibility = View.VISIBLE
        scope.launch {
            try {
                val body = withContext(Dispatchers.IO) { http.newCall(Request.Builder().url("https://api.rainviewer.com/public/weather-maps.json").header("User-Agent", "rutasfuentes/1.0").build()).execute().use { it.body?.string() ?: "" } }
                val past = JSONObject(body).getJSONObject("radar").getJSONArray("past")
                radarPath = past.getJSONObject(past.length() - 1).getString("path")
                radarEnabled = true; statusText.visibility = View.GONE; refreshRadar(false)
            } catch (e: Exception) { statusText.text = "❌ Radar: ${e.message}" }
        }
    }

    // ---------------- POIs ----------------
    private class Poi(
        val id: String, val kind: String, val name: String, val lat: Double, val lon: Double,
        val address: String? = null, val phone: String? = null, val website: String? = null,
        val hours: String? = null, val openNow: Boolean? = null
    )

    private val poiCache = HashMap<String, Pair<Long, List<Poi>>>()
    private var poiErr = ""
    private var poiShown = true
    private val poiKinds = LinkedHashSet<String>()
    private val poiByKind = HashMap<String, MutableList<Marker>>()
    private val poiPts = HashMap<String, List<GeoPoint>>()
    private var poiLastKm = 100
    private val POI_MIN_ZOOM = 11.0

    private fun overpassQuery(amenity: String, lat: Double, lon: Double, radius: Int): String =
        "[out:json][timeout:20];(node[\"amenity\"=\"$amenity\"](around:$radius,$lat,$lon);way[\"amenity\"=\"$amenity\"](around:$radius,$lat,$lon););out center 60;"

    private suspend fun overpassFetch(cl: okhttp3.OkHttpClient, mirror: String, q: String): Pair<String, String>? {
        return try {
            val u = mirror + "?data=" + java.net.URLEncoder.encode(q, "UTF-8")
            cl.newCall(Request.Builder().url(u).header("User-Agent", "rutasfuentes/1.0").build()).execute().use { r ->
                val s = r.body?.string() ?: ""
                if (r.code == 200 && s.trimStart().startsWith("{")) q to s else null
            }
        } catch (e: Exception) { null }
    }

    private fun poiLabel(kind: String): String = when (kind) {
        "dealer" -> "concesionarios"
        "fuel" -> "gasolineras"
        "restaurant" -> "restaurantes"
        "hotel" -> "alojamientos"
        "parking" -> "aparcamientos"
        "drinking_water" -> "fuentes de agua"
        "hospital" -> "hospitales"
        "car_repair" -> "talleres"
        "supermarket" -> "supermercados"
        else -> "POIs"
    }

    private fun poiKindColor(kind: String): Int = when (kind) {
        "dealer" -> 0xFFFF6A00.toInt()
        "fuel" -> 0xFF2E9E5B.toInt()
        "restaurant" -> 0xFF141414.toInt()
        "hotel" -> 0xFF2F80ED.toInt()
        "parking" -> 0xFF1565C0.toInt()
        "drinking_water" -> 0xFF00ACC1.toInt()
        "hospital" -> 0xFFC62828.toInt()
        "car_repair" -> 0xFFEF6C00.toInt()
        "supermarket" -> 0xFF8E44AD.toInt()
        else -> 0xFF1976D2.toInt()
    }

    private fun poiKindGlyph(kind: String): String = when (kind) {
        "dealer" -> "\uD83C\uDFCD"
        "fuel" -> "\u26FD"
        "restaurant" -> "\uD83C\uDF74"
        "hotel" -> "\uD83D\uDECF"
        "parking" -> "P"
        "drinking_water" -> "\uD83D\uDCA7"
        "hospital" -> "\uD83C\uDFE5"
        "car_repair" -> "\uD83D\uDD27"
        "supermarket" -> "\uD83D\uDED2"
        else -> "\uD83D\uDCCD"
    }

    // Pin vectorial: cabeza + punta, ancla en la punta, glifo blanco de categoria
    private fun poiSquareIcon(color: Int, letter: String): android.graphics.drawable.BitmapDrawable {
        val s = dp(26)
        val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val rr = android.graphics.RectF(dp(1).toFloat(), dp(1).toFloat(), (s - dp(1)).toFloat(), (s - dp(1)).toFloat())
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        bg.color = color; bg.style = android.graphics.Paint.Style.FILL
        cv.drawRoundRect(rr, s * 0.22f, s * 0.22f, bg)
        val br = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        br.color = Color.WHITE; br.style = android.graphics.Paint.Style.STROKE; br.strokeWidth = dp(2).toFloat()
        cv.drawRoundRect(rr, s * 0.22f, s * 0.22f, br)
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.WHITE; tp.textSize = s * 0.74f; tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = android.graphics.Paint.Align.CENTER
        val fm = tp.fontMetrics
        cv.drawText(letter, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, tp)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun poiPin(color: Int, glyph: String): android.graphics.drawable.BitmapDrawable {
        val d = resources.displayMetrics.density
        val w = (30f * d).toInt().coerceAtLeast(48)
        val h = (38f * d).toInt().coerceAtLeast(60)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = color; p.style = android.graphics.Paint.Style.FILL
        p.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
        val cx = w / 2f; val r = w * 0.40f; val headY = h * 0.36f; val tipY = h * 0.97f
        cv.drawCircle(cx, headY, r, p)
        val tail = android.graphics.Path()
        tail.moveTo(cx - r * 0.74f, headY + r * 0.50f)
        tail.lineTo(cx, tipY)
        tail.lineTo(cx + r * 0.74f, headY + r * 0.50f)
        tail.close()
        cv.drawPath(tail, p)
        val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        ring.color = Color.WHITE; ring.style = android.graphics.Paint.Style.STROKE
        ring.strokeWidth = dp(1.5f.toInt().coerceAtLeast(1)).toFloat()
        cv.drawCircle(cx, headY, r, ring)
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        tp.textSize = r * 1.15f; tp.textAlign = android.graphics.Paint.Align.CENTER
        tp.color = Color.WHITE; tp.typeface = Typeface.DEFAULT_BOLD
        val clip = android.graphics.Path()
        clip.addCircle(cx, headY, r * 0.94f, android.graphics.Path.Direction.CW)
        cv.save(); cv.clipPath(clip)
        cv.drawText(glyph, cx, headY + tp.textSize * 0.36f, tp)
        cv.restore()
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun osmAddress(ad: org.json.JSONObject?): String {
        if (ad == null) return ""
        val road = ad.optString("road", "").ifBlank { ad.optString("pedestrian", "") }.ifBlank { ad.optString("footway", "") }
        val num = ad.optString("house_number", "")
        val city = ad.optString("city", "").ifBlank { ad.optString("town", "") }.ifBlank { ad.optString("village", "") }
            .ifBlank { ad.optString("municipality", "") }
        val cp = ad.optString("postcode", "")
        val street = (road + if (num.isNotBlank()) " $num" else "").trim()
        return listOf(street, city, cp).filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun parseMins(s: String?): Int? {
        val parts = s?.split(":") ?: return null
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    // "24/7" o "Mo-Su 06:00-22:00" -> abierto ahora?
    private fun openNowFromHours(hours: String?): Boolean? {
        if (hours.isNullOrBlank()) return null
        if (hours.contains("24/7")) return true
        val m = Regex("^\\s*([A-Z][a-z])(?:-([A-Z][a-z]))?\\s+(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})\\s*$").matchEntire(hours.trim()) ?: return null
        fun day(c: String) = when (c) { "Mo" -> 1; "Tu" -> 2; "We" -> 3; "Th" -> 4; "Fr" -> 5; "Sa" -> 6; "Su" -> 7; else -> 0 }
        val sd = day(m.groupValues[1]); if (sd == 0) return null
        val ed = m.groupValues[2].takeIf { it.isNotBlank() }?.let { day(it) } ?: sd
        val st = (m.groupValues[3].toIntOrNull() ?: return null) * 60 + (m.groupValues[4].toIntOrNull() ?: return null)
        val en = (m.groupValues[5].toIntOrNull() ?: return null) * 60 + (m.groupValues[6].toIntOrNull() ?: return null)
        val cal = java.util.Calendar.getInstance()
        val wd = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val today = if (wd == 1) 7 else wd - 1
        val mins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val inDays = if (sd <= ed) today in sd..ed else (today >= sd || today <= ed)
        return inDays && mins >= st && mins < en
    }

    private suspend fun poisFromNominatim(kind: String, lat: Double, lon: Double, radiusKm: Double): List<Poi> = withContext(Dispatchers.IO) {
        try {
            val latD = radiusKm / 111.0
            val lonD = radiusKm / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))))
            val vb = "${lon - lonD},${lat + latD},${lon + lonD},${lat - latD}"
            val u = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=40&bounded=1&viewbox=$vb" +
                "&q=" + java.net.URLEncoder.encode(kind, "UTF-8") +
                "&accept-language=es&extratags=1&addressdetails=1&namedetails=1"
            val body = http.newCall(
                Request.Builder().url(u)
                    .header("User-Agent", "RutasFuentes/1.1 (Android, uso personal)")
                    .header("Accept", "application/json").build()
            ).execute().use { r ->
                val s = if (!r.isSuccessful) "" else (r.body?.string() ?: "")
                if (s.trimStart().startsWith("{")) poiErr = s.take(120)
                s
            }
            if (!body.trimStart().startsWith("[")) return@withContext emptyList<Poi>()
            val arr = org.json.JSONArray(body)
            val out = ArrayList<Poi>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val plat = o.optString("lat", "").toDoubleOrNull() ?: continue
                val plon = o.optString("lon", "").toDoubleOrNull() ?: continue
                val ex = o.optJSONObject("extratags")
                val ad = o.optJSONObject("address")
                val nd = o.optJSONObject("namedetails")
                val display = o.optString("display_name", "")
                var nm = o.optString("name", "")
                if (nm.isBlank()) nm = nd?.optString("name", "") ?: ""
                if (nm.isBlank()) nm = display.substringBefore(',')
                val hours = ex?.optString("opening_hours", "")?.ifBlank { null }
                out.add(Poi(
                    id = "osm-" + o.optString("osm_type", "n") + "-" + o.optString("osm_id", "$plat$plon"),
                    kind = kind,
                    name = nm.ifBlank { poiLabel(kind) },
                    lat = plat, lon = plon,
                    address = osmAddress(ad).ifBlank { null },
                    phone = (ex?.optString("phone", "")?.ifBlank { null }) ?: (ex?.optString("contact:phone", "")?.ifBlank { null }),
                    website = (ex?.optString("website", "")?.ifBlank { null }) ?: (ex?.optString("contact:website", "")?.ifBlank { null }),
                    hours = hours, openNow = openNowFromHours(hours)
                ))
            }
            val ref = GeoPoint(lat, lon)
            out.distinctBy { it.id }.sortedBy { GeoPoint(it.lat, it.lon).distanceToAsDouble(ref) }
        } catch (e: Exception) { poiErr = e.message ?: e.toString(); android.util.Log.e("POI", "nominatim failed", e); emptyList<Poi>() }
    }

    private fun dealerOpen(d: org.json.JSONObject): Boolean? {
        val reg = d.optJSONObject("openingTimes")?.optJSONArray("regularHours") ?: return null
        val cal = java.util.Calendar.getInstance()
        val wd = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val day = if (wd == 1) 7 else wd - 1
        val mins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        for (i in 0 until reg.length()) {
            val r = reg.getJSONObject(i)
            if (r.optInt("weekday") != day) continue
            val b = parseMins(r.optString("periodBegin")); val e = parseMins(r.optString("periodEnd"))
            if (b != null && e != null && mins >= b && mins < e) return true
        }
        return false
    }

    private suspend fun dealersFromApi(lat: Double, lon: Double): List<Poi> = withContext(Dispatchers.IO) {
        try {
            val u = "https://maps.harley-davidson.com/api/dealers?lat=$lat&lon=$lon&radiusInMiles=63&maxResults=50"
            val body = http.newCall(
                Request.Builder().url(u)
                    .header("User-Agent", "RutasFuentes/1.1").header("Accept", "application/json")
                    .header("Accept-Language", "es-ES").build()
            ).execute().use { r ->
                val s = if (!r.isSuccessful) "" else (r.body?.string() ?: "")
                s
            }
            val arr = org.json.JSONObject(body).optJSONArray("dealers") ?: return@withContext emptyList<Poi>()
            val out = ArrayList<Poi>()
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val dLat = d.optDouble("latitude", Double.NaN); val dLon = d.optDouble("longitude", Double.NaN)
                if (dLat.isNaN() || dLon.isNaN()) continue
                val name = d.optString("dealerName", "Harley-Davidson")
                val cpCity = (d.optString("postalCode", "") + " " + d.optString("city", "")).trim()
                val addr = listOf(d.optString("address1", "").trim(), cpCity).filter { it.isNotBlank() }.joinToString(", ")
                val sb = StringBuilder()
                val days = d.optJSONArray("dealerHours")
                if (days != null) for (k in 0 until days.length()) {
                    val day = days.getJSONObject(k)
                    val label = when (day.optString("day")) {
                        "MONDAY" -> "Lun"; "TUESDAY" -> "Mar"; "WEDNESDAY" -> "Mi\u00E9"
                        "THURSDAY" -> "Jue"; "FRIDAY" -> "Vie"; "SATURDAY" -> "S\u00E1b"; "SUNDAY" -> "Dom"; else -> ""
                    }
                    val op = day.optJSONObject("open") ?: continue
                    val cl = day.optJSONObject("close") ?: continue
                    if (sb.isNotEmpty()) sb.append("  \u00B7  ")
                    sb.append(String.format("%s %02d:%02d-%02d:%02d", label, op.optInt("hour"), op.optInt("minute"), cl.optInt("hour"), cl.optInt("minute")))
                }
                out.add(Poi(
                    id = "hd-" + d.optString("dealerId", d.optString("id", name)),
                    kind = "dealer", name = name, lat = dLat, lon = dLon,
                    address = addr.ifBlank { null },
                    phone = d.optString("phone", "").ifBlank { null },
                    website = d.optString("url", "").ifBlank { null },
                    hours = sb.toString().ifBlank { null },
                    openNow = dealerOpen(d)
                ))
            }
            val ref = GeoPoint(lat, lon)
            out.distinctBy { it.id }.sortedBy { GeoPoint(it.lat, it.lon).distanceToAsDouble(ref) }
        } catch (e: Exception) { emptyList<Poi>() }
    }

    private fun parseOverpass(body: String): List<Poi> {
        val out = ArrayList<Poi>()
        try {
            val els = org.json.JSONObject(body).getJSONArray("elements")
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                var lat = e.optDouble("lat", Double.NaN); var lon = e.optDouble("lon", Double.NaN)
                if (lat.isNaN()) { val ce = e.optJSONObject("center") ?: continue; lat = ce.getDouble("lat"); lon = ce.getDouble("lon") }
                val tags = e.optJSONObject("tags")
                out.add(Poi(
                    id = "ov-" + e.optString("type", "n") + "-" + e.optString("id", "$lat$lon"),
                    kind = "overpass", name = tags?.optString("name", "POI") ?: "POI",
                    lat = lat, lon = lon,
                    address = tags?.optString("addr:street", "")?.ifBlank { null },
                    phone = tags?.optString("phone", "")?.ifBlank { null },
                    website = tags?.optString("website", "")?.ifBlank { null },
                    hours = tags?.optString("opening_hours", "")?.ifBlank { null },
                    openNow = openNowFromHours(tags?.optString("opening_hours", ""))
                ))
            }
        } catch (_: Exception) { }
        return out
    }

    private fun loadPois(kind: String) {
        val c = lastLoc ?: map.mapCenter ?: return
        poiCache[kind]?.let { (t, list) ->
            if (System.currentTimeMillis() - t < 600000L) { renderPois(list, kind, "guardado"); return }
        }
        showStatus("\uD83D\uDD0D Buscando " + poiLabel(kind) + "\u2026", 0)
        scope.launch {
            val list = if (kind == "dealer") dealersFromApi(c.latitude, c.longitude)
                       else poisFromNominatimLadder(kind, c.latitude, c.longitude)
            if (list.isNotEmpty()) {
                poiCache[kind] = System.currentTimeMillis() to list
                renderPois(list, kind, if (kind == "dealer") "en 100 km" else "en $poiLastKm km")
                return@launch
            }
            // Respaldo: Overpass (mas lento; puede estar saturado)
            showStatus("\u26A0 Nominatim: " + poiErr.take(40) + " \u00B7 Overpass\u2026", 0)
            val amenity = if (kind == "dealer") "motorcycle" else kind
            val q = overpassQuery(amenity, c.latitude, c.longitude, 4000)
            val cl = http.newBuilder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
            val mirrors = listOf(
                "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
                "https://overpass-api.de/api/interpreter",
                "https://overpass.private.coffee/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter"
            )
            var found: List<Poi> = emptyList()
            for (m in mirrors) {
                val r = withContext(Dispatchers.IO) { overpassFetch(cl, m, q) } ?: continue
                found = parseOverpass(r.second)
                if (found.isNotEmpty()) break
            }
            if (found.isEmpty()) { showStatus("\u26A0\uFE0F POIs: sin datos (reintenta)", 6000); return@launch }
            poiCache[kind] = System.currentTimeMillis() to found
            renderPois(found, kind, "en el mapa")
        }
    }

    private suspend fun poisFromNominatimLadder(kind: String, lat: Double, lon: Double): List<Poi> {
        var best: List<Poi> = emptyList()
        for (km in intArrayOf(25, 50, 100)) {
            val res = poisFromNominatim(kind, lat, lon, km.toDouble())
            poiLastKm = km
            if (res.size > best.size) best = res
            if (best.size >= 8) break
            if (km < 100) kotlinx.coroutines.delay(1100)
        }
        return best
    }

    private fun poiSnippet(p: Poi): String {

        val parts = ArrayList<String>()
        if (p.openNow == true) parts.add("Abierto ahora")
        if (p.openNow == false) parts.add("Cerrado ahora")
        if (!p.address.isNullOrBlank()) parts.add(p.address!!)
        return parts.joinToString(" \u00B7 ")
    }

    private fun renderPois(list: List<Poi>, kind: String, label: String) {
        poiByKind[kind]?.forEach { map.overlays.remove(it) }
        val icon = when (kind) {
            "parking" -> poiSquareIcon(0xFF1565C0.toInt(), "P")
            "hospital" -> poiSquareIcon(0xFFC62828.toInt(), "H")
            else -> poiPin(poiKindColor(kind), poiKindGlyph(kind))
        }
        val lst = ArrayList<Marker>()
        val pts = ArrayList<GeoPoint>()
        for (p in list) {
            val m = Marker(map)
            m.position = GeoPoint(p.lat, p.lon)
            m.title = p.name
            m.snippet = poiSnippet(p)
            m.icon = icon
            m.setAnchor(0.5f, if (kind == "parking" || kind == "hospital") 0.5f else 1.0f)
            m.setOnMarkerClickListener { _, _ -> showPoiCard(p); true }
            m.isEnabled = map.zoomLevelDouble >= POI_MIN_ZOOM
            map.overlays.add(m); lst.add(m); pts.add(m.position)
        }
        poiByKind[kind] = lst
        poiPts[kind] = pts
        poiKinds.add(kind)
        poiMarkers.clear()
        poiByKind.values.forEach { poiMarkers.addAll(it) }
        val allPts = poiPts.values.flatten()
        val ctr = lastLoc ?: map.mapCenter
        if (ctr != null && allPts.isNotEmpty()) {
            if (kind == poiKinds.first()) map.controller.setCenter(ctr)
            val maxKm = allPts.maxOf { it.distanceToAsDouble(ctr) / 1000.0 }
            val z = when { maxKm <= 3.0 -> 15.0; maxKm <= 8.0 -> 14.0; maxKm <= 20.0 -> 13.0; maxKm <= 45.0 -> 12.0; else -> 11.0 }
            map.controller.setZoom(z)
        }
        poiShown = true
        poiMarkers.forEach { it.isEnabled = true }
        val nombres = poiKinds.joinToString(", ") { poiLabel(it) }
        showStatus("\uD83D\uDCCD ${poiMarkers.size} pines (" + nombres + ") $label", 5000)
        map.invalidate()
    }

    private fun hidePoisKind(kind: String) {
        poiByKind[kind]?.forEach { map.overlays.remove(it) }
        poiByKind.remove(kind); poiPts.remove(kind); poiKinds.remove(kind)
        poiMarkers.clear()
        poiByKind.values.forEach { poiMarkers.addAll(it) }
        poiShown = poiMarkers.isNotEmpty()
        if (poiKinds.isEmpty()) showStatus("\uD83D\uDCCD POIs ocultos", 3000)
        else showStatus("\uD83D\uDCCD ${poiMarkers.size} pines (" + poiKinds.joinToString(", ") { poiLabel(it) } + ")", 3000)
        map.invalidate()
    }

    private fun hidePois() {
        poiMarkers.forEach { map.overlays.remove(it) }
        poiMarkers.clear()
        poiByKind.clear(); poiPts.clear(); poiKinds.clear()
        poiShown = false
        showStatus("\uD83D\uDCCD POIs ocultos", 3000)
        map.invalidate()
    }
    private fun tradHorario(h: String): String {
        var s = h
        val dias = listOf("Mo" to "Lun", "Tu" to "Mar", "We" to "Mi\u00E9", "Th" to "Jue", "Fr" to "Vie", "Sa" to "S\u00E1b", "Su" to "Dom", "PH" to "Fest", "off" to "cerrado")
        for ((a, b) in dias) s = Regex("\\b" + a + "\\b").replace(s, b)
        return s
    }
    private fun poiDistKm(p: Poi): Double {
        val ref = lastLoc ?: map.mapCenter ?: return 0.0
        return GeoPoint(p.lat, p.lon).distanceToAsDouble(ref) / 1000.0
    }

    private fun showPoiCard(p: Poi) {
        val accent = poiKindColor(p.kind)
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2)) }
        fun txt(t: String, size: Float, color: Int, bold: Boolean = false, click: (() -> Unit)? = null) {
            val v = TextView(this)
            v.text = t; v.setTextSize(size); v.setTextColor(color)
            if (bold) v.setTypeface(null, Typeface.BOLD)
            v.setPadding(0, dp(3), 0, dp(3))
            if (click != null) { v.setOnClickListener { click() }; v.setTextColor(if (color == Color.BLACK) ROUTE_BLUE else color) }
            ll.addView(v)
        }
        txt(poiLabel(p.kind).uppercase(), 10.5f, accent, true)
        txt(p.name, 18f, Color.BLACK, true)
        if (!p.address.isNullOrBlank()) txt(p.address!!, 13.5f, 0xFF444444.toInt())
        if (p.openNow != null) txt(if (p.openNow) "\u25CF Abierto ahora" else "\u25CF Cerrado ahora", 13f, if (p.openNow) GREEN_FUENTE else 0xFFC62828.toInt(), true)
        if (!p.hours.isNullOrBlank()) txt("Horario: " + tradHorario(p.hours!!), 12.5f, 0xFF666666.toInt())
        val dk = poiDistKm(p)
        if (dk > 0.0) txt(String.format("A %.1f km en linea recta", dk), 12.5f, 0xFF666666.toInt())
        if (!p.phone.isNullOrBlank()) txt("\u260E " + p.phone!! + "  (llamar)", 13.5f, accent, true) {
            try { startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + p.phone!!.replace(" ", "")))) }
            catch (e: Exception) { toast("No se pudo abrir el telefono") }
        }
        if (!p.website.isNullOrBlank()) {
            val web = p.website!!
            txt("\uD83C\uDF10 " + web, 12.5f, accent) {
                try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(if (web.startsWith("http")) web else "https://$web"))) }
                catch (e: Exception) { toast("No se pudo abrir el navegador") }
            }
        }
        txt("\uD83E\uDDED Abrir en la app de mapas", 12.5f, 0xFF666666.toInt()) {
            try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(" + android.net.Uri.encode(p.name) + ")"))) }
            catch (e: Exception) { toast("No hay app de mapas") }
        }
        val sv = android.widget.ScrollView(this).apply { addView(ll) }
        android.app.AlertDialog.Builder(this)
            .setView(sv)
            .setPositiveButton("\uD83C\uDFAF  Destino") { _, _ ->
                endPt = GeoPoint(p.lat, p.lon); endName = p.name; updRouteInfo(); selectTab(0); toast("Destino: " + p.name)
            }
            .setNeutralButton("+ Parada") { _, _ ->
                if (paradas.size >= 10) toast("Maximo 10 paradas")
                else { paradas.add(GeoPoint(p.lat, p.lon) to p.name); updRouteInfo(); selectTab(0); toast("Parada ${paradas.size}/10: " + p.name) }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // ---------------- BUSCADOR ----------------
    private fun openSearch() {
        searchOverlay.bringToFront()
        searchOverlay.visibility = View.VISIBLE
        searchInput.setText(""); searchInput.requestFocus(); doSearch("")
        searchInput.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }
    private fun closeSearch() { hideKeyboard(); searchOverlay.visibility = View.GONE }
    private fun hideKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0) }

    private class SRow(val icon: Int, val bg: Int, val tint: Int, val title: String, val sub: String, val kind: Int, val index: Int)
    private inner class SearchAdapter(private val items: List<SRow>, private val q: String) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val ctx = this@MainActivity
            val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            row.addView(iconCircle(items[p].icon, items[p].bg, items[p].tint, 34))
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(tv("", 15f, DARK, true).apply {
                text = markMatch(items[p].title, q); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            col.addView(tv(items[p].sub, 12.5f, TEXT2).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0)
            })
            row.addView(col)
            wrap.addView(row)
            wrap.addView(View(ctx).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(52) }
            })
            return wrap
        }
    }

    private fun doSearch(q: String) {
        val qRaw = q.trim()
        val query = norm(qRaw)
        val rows = ArrayList<SRow>()
        if (query.isEmpty()) {
            results.adapter = MsgAdapter("Busca por fuente, pueblo o código postal")
            results.setOnItemClickListener(null)
            return
        }
        if (query.length <= 5 && query.all { it.isDigit() }) for ((t, cps) in postal) if (cps.any { it.startsWith(query) }) {
            val ti = townNames.indexOf(t); if (ti >= 0) rows.add(SRow(R.drawable.ic_postal, GREEN_SOFT, GREEN_TEXT, t, "CP " + cps.joinToString(", "), 1, ti))
        }
        var tn = 0
        for (i in townSearch.indices) if (townSearch[i].contains(query)) { rows.add(SRow(R.drawable.ic_pin, BLUE_SOFT, ROUTE_BLUE, townNames[i], "Pueblo", 1, i)); if (++tn >= 10) break }
        var fn = 0
        for (i in fSearch.indices) if (fSearch[i].contains(query)) {
            val f = fountains[i]; val st = fountainStyle(f.name)
            rows.add(SRow(st.res, st.bg, st.fg, f.name, f.town, 0, i)); if (++fn >= 40) break
        }
        if (rows.isEmpty()) { results.adapter = MsgAdapter("Sin resultados para \"$qRaw\""); return }
        results.adapter = SearchAdapter(rows, qRaw)
        results.setOnItemClickListener { _, _, pos, _ ->
            val r = rows.getOrNull(pos) ?: return@setOnItemClickListener
            hideKeyboard()
            selectTab(0)
            if (r.kind == 1) {
                val t = townNames[r.index]; val c = towns[t]
                if (c != null) {
                    if (searchTarget == "start") { startPt = GeoPoint(c[0], c[1]); startName = t; gpsStartOn = false } else { endPt = GeoPoint(c[0], c[1]); endName = t }
                    updRouteInfo(); closeSearch(); centerOn(GeoPoint(c[0], c[1]), 14.0)
                }
            } else {
                val f = fountains[r.index]
                if (searchTarget == "start") { startPt = GeoPoint(f.lat, f.lon); startName = f.name; gpsStartOn = false; updRouteInfo(); closeSearch(); centerOn(GeoPoint(f.lat, f.lon), 16.0) }
                else { closeSearch(); centerOn(GeoPoint(f.lat, f.lon), 16.0); showFuenteDialog(f) }
            }
        }
    }

    private fun centerOn(p: GeoPoint, z: Double) { map.controller.animateTo(p); map.controller.setZoom(z) }

    private fun distKm(lat: Double, lon: Double, f: Fountain): Double {
        val r = FloatArray(1); android.location.Location.distanceBetween(lat, lon, f.lat, f.lon, r); return r[0] / 1000.0
    }
    private fun styleRadiusRow() {
        for (i in 0 until radiusRow.childCount) {
            val b = radiusRow.getChildAt(i) as TextView
            val on = (b.tag as Int).toDouble() == fuenteRadius
            b.background = round(if (on) GREEN_SOFT else Color.WHITE, 16, 2, if (on) GREEN_FUENTE else ORANGE)
            b.setTextColor(if (on) GREEN_TEXT else ORANGE_TEXT)
            b.alpha = 1f
        }
    }
    // ---------------- LISTA FUENTES (formato) ----------------
    private class FRow(val name: String, val town: String, val km: String)
    private inner class FuentesAdapter(private val items: List<FRow>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val ctx = this@MainActivity
            val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(10), dp(2), dp(10))
            }
            val st = fountainStyle(items[p].name)
            row.addView(iconCircle(st.res, st.bg, st.fg, 34))
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(tv(items[p].name, 14.5f, DARK, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val sub = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, 0)
            }
            sub.addView(ic(R.drawable.ic_pin, 12, TEXT2).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(4) }
            })
            sub.addView(tv(items[p].town, 12.5f, TEXT2).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            col.addView(sub)
            row.addView(col)
            row.addView(tv(items[p].km, 12f, ORANGE_TEXT, true).apply {
                background = round(ORANGE_SOFT, 10); setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
            })
            wrap.addView(row)
            wrap.addView(View(ctx).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(47) }
            })
            return wrap
        }
    }

    private fun showFuentesNear() {
        styleRadiusRow()
        val gps = myLoc.myLocation
        if (gps == null) {
            myLoc.enableMyLocation()
            nearList = ArrayList()
            fuentesCount.text = ""
            fuenteList.adapter = MsgAdapter("Esperando GPS. Activa la ubicación y vuelve a tocar Fuentes.")
            return
        }
        val lat = gps.latitude; val lon = gps.longitude
        val R = fuenteRadius
        val rTxt = if (R >= 1) R.toInt().toString() else R.toString()
        scope.launch {
            val near = withContext(Dispatchers.Default) {
                fountains.map { it to distKm(lat, lon, it) }.filter { it.second <= R }.sortedBy { it.second }.take(400)
            }
            nearList = ArrayList(near.map { it.first })
            if (near.isEmpty()) {
                fuentesCount.text = ""
                fuenteList.adapter = MsgAdapter("Sin fuentes a menos de $rTxt km de tu ubicación")
            } else {
                val rows = near.map { FRow(it.first.name, it.first.town, String.format(Locale.US, "%.1f km", it.second)) }
                fuenteList.adapter = FuentesAdapter(rows)
                fuentesCount.text = if (near.size >= 400) "400+ fuentes · $rTxt km" else "${near.size} fuentes · $rTxt km"
                fuenteList.setOnItemClickListener { _, _, pos, _ -> val f = nearList.getOrNull(pos); if (f != null) { selectTab(0); centerOn(GeoPoint(f.lat, f.lon), 16.0); showFuenteDialog(f) } }
            }
        }
    }

    private var nearList = ArrayList<Fountain>()

    // ---------------- COMPARTIR / GUARDAR ----------------
    private fun shareRoute() {
        val pts = ArrayList<GeoPoint>(); startPt?.let { pts.add(it) }; paradas.forEach { pts.add(it.first) }; endPt?.let { pts.add(it) }
        if (pts.size < 2) { toast("Define salida y destino"); return }
        val url = "https://www.google.com/maps/dir/" + pts.joinToString("/") { String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude) }
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Mi ruta por las fuentes 🏍\n$url") }
        startActivity(android.content.Intent.createChooser(i, "Compartir ruta"))
    }

    private fun shareRouteMenu() {
        val pts = ArrayList<GeoPoint>(); startPt?.let { pts.add(it) }; paradas.forEach { pts.add(it.first) }; endPt?.let { pts.add(it) }
        if (pts.size < 2) { toast("Define salida y destino"); return }
        val url = "https://www.google.com/maps/dir/" + pts.joinToString("/") { String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude) }
        val texto = "Mi ruta por las fuentes 🏍\n$url"
        android.app.AlertDialog.Builder(this).setTitle("Compartir ruta")
            .setItems(arrayOf("📤  Compartir (elegir app)", "🟢  WhatsApp", "🗺️  Abrir en Google Maps")) { _, w ->
                when (w) {
                    0 -> shareRoute()
                    1 -> {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, texto); setPackage("com.whatsapp") }
                        try { startActivity(i) } catch (e: Exception) {
                            try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/?text=" + android.net.Uri.encode(texto)))) }
                            catch (e2: Exception) { toast("No hay WhatsApp instalado") }
                        }
                    }
                    else -> startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }.setNegativeButton("Cerrar", null).show()
    }

    // ---------------- PARADAS: ORDENAR / BORRAR ----------------
    private fun paradasDialog() {
        if (paradas.isEmpty()) { toast("No hay paradas"); return }
        val labels = paradas.mapIndexed { i, p -> "${i + 1}.  " + p.second }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("➕ Paradas (${paradas.size}/10)")
            .setItems(labels) { _, w -> paradaAcciones(w) }
            .setNeutralButton("A\u00F1adir") { _, _ -> selectTab(1) }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun paradaAcciones(i: Int) {
        val p = paradas.getOrNull(i) ?: return
        android.app.AlertDialog.Builder(this).setTitle("${i + 1}. " + p.second)
            .setItems(arrayOf("▲  Subir", "▼  Bajar", "🗑️  Borrar esta parada")) { _, w ->
                when (w) {
                    0 -> if (i > 0) { val t = paradas.removeAt(i); paradas.add(i - 1, t) } else toast("Ya es la primera")
                    1 -> if (i < paradas.size - 1) { val t = paradas.removeAt(i); paradas.add(i + 1, t) } else toast("Ya es la última")
                    else -> { paradas.removeAt(i); toast("Parada borrada") }
                }
                updRouteInfo()
                if (routePts.size >= 2) { toast("Recalculando ruta…"); calcRoute() } else map.invalidate()
            }.setNegativeButton("Cerrar", null).show()
    }

    // ---------------- CENTRAR RUTA / CODIGOS POSTALES ----------------
    private fun centrarRuta() {
        if (routePts.size < 2) { toast("Calcula primero la ruta"); return }
        try { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(routePts).increaseByScale(1.15f), true); map.invalidate() } catch (_: Exception) {}
        updateRouteCenterButton()
        map.postDelayed({ updateRouteCenterButton() }, 450L)
    }

    private fun updateRouteCenterButton() {
        if (!::centrarBtn.isInitialized) return
        if (centrarBtn.visibility != View.VISIBLE) { routeCenterActive = null; return }
        var on = false
        val bb = routeBounds
        if (bb != null && map.visibility == View.VISIBLE) {
            val v = map.boundingBox
            if (v != null) on = v.latNorth >= bb.latNorth && v.latSouth <= bb.latSouth &&
                v.lonEast >= bb.lonEast && v.lonWest <= bb.lonWest
        }
        if (on == routeCenterActive) return
        routeCenterActive = on
        centrarBtn.background = round(Color.parseColor("#CC000000"), 19, 3, if (on) GREEN_FUENTE else ORANGE)
        centrarBtn.imageTintList = android.content.res.ColorStateList.valueOf(if (on) GREEN_FUENTE else ORANGE)
    }

    private fun cpDe(nombre: String): String {
        if (nombre.isEmpty()) return ""
        val t = if (towns.containsKey(nombre)) nombre else (fountains.firstOrNull { it.name == nombre }?.town ?: "")
        val cps = postal[t] ?: return ""
        return "$t → " + cps.joinToString(", ")
    }

    private fun mostrarCPs() {
        val rows = ArrayList<Pair<String, String>>()
        fun add(nm: String) { if (nm.isNotEmpty() && nm != "Punto en el mapa") rows.add(nm to cpDe(nm).ifEmpty { "Sin datos" }) }
        add(startName)
        paradas.forEach { add(it.second) }
        add(endName)
        if (rows.isEmpty()) { toast("Define salida, paradas o destino"); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), dp(6)) }
        rows.forEachIndexed { i, r ->
            if (i > 0) box.addView(View(this@MainActivity).apply { setBackgroundColor(DIVIDER); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(42) } })
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
            row.addView(iconCircle(R.drawable.ic_postal, GREEN_SOFT, GREEN_TEXT, 32))
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            col.addView(tv(r.first, 14.5f, DARK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            col.addView(tv(r.second, 12.5f, if (r.second == "Sin datos") TEXT2 else GREEN_TEXT).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(col)
            box.addView(row)
        }
        val sc = android.widget.ScrollView(this@MainActivity).apply { addView(box) }
        android.app.AlertDialog.Builder(this).setTitle("\uD83D\uDCEE Códigos postales").setView(sc).setPositiveButton("Cerrar", null).show()
    }

    // ---------------- ACCESO DE LA FUENTE (Asfaltada / Sin asfaltar) ----------------
    private fun accLabel(t: String): String = when (t) { "r" -> "🛣️ Asfaltada"; "o" -> "🚧 Sin asfaltar"; else -> "❔ Sin información" }
    private fun getAccess(id: Int): String = getSharedPreferences("notes", MODE_PRIVATE).getString("a_$id", "") ?: ""
    private fun cycleAccess(f: Fountain) {
        val order = listOf("", "r", "o")
        val cur = getAccess(f.id)
        val next = order[(order.indexOf(cur).coerceAtLeast(0) + 1) % order.size]
        getSharedPreferences("notes", MODE_PRIVATE).edit().putString("a_${f.id}", next).apply()
        toast("Acceso: " + accLabel(next))
    }


    // ---------------- COPIA DE SEGURIDAD (ZIP con index.json + fotos, y JSON legado) ----------------
    private data class RestoreResult(val fotos: Int, val notas: Int, val accesos: Int, val pies: Int, val error: String?)

    private fun shareFile(f: File, mime: String) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(i, "Compartir copia"))
        } catch (e: Exception) { toast("Error: ${e.message}") }
    }

    private fun exportarDatos() {
        scope.launch {
            statusText.text = "\u23F3 Creando copia."; statusText.visibility = View.VISIBLE
            val zip = withContext(Dispatchers.IO) { buildBackupZip() }
            statusText.visibility = View.GONE
            if (zip == null) { toast("No se pudo crear la copia"); return@launch }
            val opts = arrayOf("\uD83D\uDCC2 Guardar en la carpeta de fotos", "\uD83D\uDCE4 Compartir")
            android.app.AlertDialog.Builder(this@MainActivity).setTitle("\uD83D\uDCBE Copia de datos").setItems(opts) { _, w ->
                if (w == 0) {
                    if (photoTreeUri == null) { toast("Elige una carpeta en Datos > Fotos"); shareFile(zip, "application/zip") }
                    else {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(java.util.Date())
                        val ok = copyFileToTree(zip, "copia_rutas_fuentes_$stamp.zip", "application/zip")
                        if (ok) toast("Copia guardada en la carpeta") else { toast("No se pudo guardar; se comparte"); shareFile(zip, "application/zip") }
                    }
                } else shareFile(zip, "application/zip")
            }.setNegativeButton("Cancelar", null).show()
        }
    }

    private fun buildBackupZip(): File? {
        return try {
            val prefs = getSharedPreferences("notes", MODE_PRIVATE).all
            val o = JSONObject()
            o.put("app", "RutasFuentes"); o.put("tipo", "backup"); o.put("version", 2)
            o.put("fecha", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date()))
            val notas = JSONObject(); val accesos = JSONObject(); val pies = JSONObject(); val fechas = JSONObject()
            for ((k, v) in prefs) {
                when {
                    k.startsWith("n_") -> (v as? String)?.let { notas.put(k, it) }
                    k.startsWith("a_") -> (v as? String)?.let { if (it.isNotEmpty()) accesos.put(k, it) }
                    k.startsWith("c_") -> (v as? String)?.let { if (it.isNotEmpty()) pies.put(k, it) }
                    k.startsWith("d_") -> (v as? Long)?.let { fechas.put(k, it) }
                }
            }
            o.put("notas", notas); o.put("accesos", accesos); o.put("pies", pies); o.put("fechas", fechas)
            val arr = org.json.JSONArray()
            photoDir().listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }?.sortedBy { it.name }?.forEach { file ->
                val id = idOf(file); val slot = slotOf(file)
                if (id >= 0) {
                    val j = JSONObject(); j.put("id", id); j.put("slot", slot)
                    j.put("fecha", photoDate(id, slot))
                    arr.put(j)
                }
            }
            o.put("fotos", arr)
            val zip = File(cacheDir, "copia_rutas_fuentes.zip")
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(zip.outputStream())).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("index.json"))
                z.write(o.toString().toByteArray(Charsets.UTF_8)); z.closeEntry()
                photoDir().listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }?.sortedBy { it.name }?.forEach { f ->
                    z.putNextEntry(java.util.zip.ZipEntry("fotos/" + f.name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            zip
        } catch (e: Exception) { toast("Error al exportar: ${e.message}"); null }
    }

    private fun importarDatosPick() {
        try { backupPicker.launch("*/*") } catch (e: Exception) { toast("Error: ${e.message}") }
    }

    private val backupPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        scope.launch {
            statusText.text = "\u23F3 Restaurando copia."; statusText.visibility = View.VISIBLE
            val r = withContext(Dispatchers.IO) { restoreBackup(uri) }
            statusText.visibility = View.GONE
            if (r.error != null) { toast(r.error); return@launch }
            refreshMarkers(); map.invalidate()
            android.app.AlertDialog.Builder(this@MainActivity).setTitle("\u2705 Copia importada")
                .setMessage("Restaurados:\n ${r.notas} notas\n ${r.accesos} accesos\n ${r.pies} pies de foto\n ${r.fotos} fotos")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun restoreBackup(uri: android.net.Uri): RestoreResult {
        return try {
            val isZip = contentResolver.openInputStream(uri)?.use { i ->
                val b = ByteArray(2); val n = i.read(b)
                n == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
            } ?: false
            if (isZip) {
                var index: JSONObject? = null
                var fotos = 0
                val prefs = getSharedPreferences("notes", MODE_PRIVATE)
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(contentResolver.openInputStream(uri))).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name
                        if (name == "index.json") {
                            val txt = String(z.readBytes(), Charsets.UTF_8)
                            index = runCatching { JSONObject(txt) }.getOrNull()
                        } else if (name.startsWith("fotos/") && name.endsWith(".jpg")) {
                            val fn = name.substringAfter("fotos/")
                            val id = fn.substringBefore('_').toIntOrNull()
                            val slot = fn.substringAfterLast('_').substringBefore('.').toIntOrNull()
                            if (id != null && slot != null && slot in 0 until PHOTO_MAX) {
                                val f = photoFile(id, slot); f.parentFile?.mkdirs()
                                f.outputStream().use { out -> z.copyTo(out) }
                                thumbFile(id, slot).delete()
                                fotos++
                            }
                        }
                        z.closeEntry()
                        e = z.nextEntry
                    }
                }
                val o = index ?: return RestoreResult(0, 0, 0, 0, "Copia ZIP sin index.json")
                if (o.optString("app") != "RutasFuentes") return RestoreResult(0, 0, 0, 0, "Ese archivo no es una copia de esta app")
                val ed = prefs.edit()
                for (campo in listOf("notas", "accesos", "pies")) {
                    val jo = o.optJSONObject(campo) ?: continue
                    for (k in jo.keys()) ed.putString(k, jo.optString(k, ""))
                }
                o.optJSONObject("fechas")?.let { jo -> for (k in jo.keys()) ed.putLong(k, jo.optLong(k)) }
                ed.apply()
                return RestoreResult(fotos, o.optJSONObject("notas")?.length() ?: 0, o.optJSONObject("accesos")?.length() ?: 0, o.optJSONObject("pies")?.length() ?: 0, null)
            } else {
                val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val o = JSONObject(txt)
                if (o.optString("app") != "RutasFuentes") return RestoreResult(0, 0, 0, 0, "Ese archivo no es una copia de esta app")
                val prefs = getSharedPreferences("notes", MODE_PRIVATE)
                val ed = prefs.edit()
                for (campo in listOf("notas", "accesos", "pies")) {
                    val jo = o.optJSONObject(campo) ?: continue
                    for (k in jo.keys()) ed.putString(k, jo.optString(k, ""))
                }
                var nF = 0
                val fa = o.optJSONArray("fotos")
                if (fa != null) for (i in 0 until fa.length()) {
                    try {
                        val j = fa.getJSONObject(i)
                        val id = j.getInt("id"); val slot = j.getInt("slot")
                        if (slot !in 0 until PHOTO_MAX) continue
                        val bytes = android.util.Base64.decode(j.getString("b64"), android.util.Base64.NO_WRAP)
                        val f = photoFile(id, slot); f.parentFile?.mkdirs()
                        f.writeBytes(bytes)
                        thumbFile(id, slot).delete()
                        nF++
                    } catch (_: Exception) {}
                }
                ed.apply()
                return RestoreResult(nF, o.optJSONObject("notas")?.length() ?: 0, o.optJSONObject("accesos")?.length() ?: 0, o.optJSONObject("pies")?.length() ?: 0, null)
            }
        } catch (e: Exception) { RestoreResult(0, 0, 0, 0, "Error al importar: ${e.message}") }
    }

    private fun routeToJson(name: String): JSONObject {
        val o = JSONObject(); o.put("name", name)
        o.put("start", ptArray(startPt, startName)); o.put("end", ptArray(endPt, endName))
        val pa = org.json.JSONArray(); paradas.forEach { pa.put(ptArray(it.first, it.second)) }; o.put("paradas", pa)
        return o
    }
    private fun ptArray(p: GeoPoint?, n: String): org.json.JSONArray {
        val a = org.json.JSONArray(); if (p != null) { a.put(p.latitude); a.put(p.longitude); a.put(n) } ; return a
    }
    private fun savedRoutes(): org.json.JSONArray = try { org.json.JSONArray(getSharedPreferences("rf", MODE_PRIVATE).getString("routes", "[]") ?: "[]") } catch (e: Exception) { org.json.JSONArray() }
    private fun putSavedRoutes(a: org.json.JSONArray) = getSharedPreferences("rf", MODE_PRIVATE).edit().putString("routes", a.toString()).apply()

    private fun saveRouteDialog() {
        if (startPt == null && endPt == null && paradas.isEmpty()) { toast("No hay ruta que guardar"); return }
        textPrompt("Guardar ruta", "", (startName.ifEmpty { "Ruta" }) + " - " + (endName.ifEmpty { ".." }), onSaved = { raw ->
            val name = raw.ifBlank { "Ruta" }
            val a = savedRoutes(); a.put(routeToJson(name)); putSavedRoutes(a); toast("Ruta guardada: $name")
        })
    }

    private fun showSavedRoutes() {
        val a = savedRoutes()
        if (a.length() == 0) { toast("No hay rutas guardadas"); return }
        val names = ArrayList<String>(); for (i in 0 until a.length()) names.add(a.getJSONObject(i).optString("name", "Ruta ${i + 1}"))
        android.app.AlertDialog.Builder(this).setTitle("Mis rutas").setItems(names.toTypedArray()) { _, w ->
            android.app.AlertDialog.Builder(this).setTitle(names[w]).setItems(arrayOf("📂 Cargar", "🗑 Eliminar")) { _, w2 ->
                if (w2 == 0) { loadRoute(a.getJSONObject(w)); } else { val na = org.json.JSONArray(); for (i in 0 until a.length()) if (i != w) na.put(a.getJSONObject(i)); putSavedRoutes(na); toast("Ruta eliminada") }
            }.show()
        }.setNegativeButton("Cerrar", null).show()
    }

    private fun loadRoute(o: JSONObject) {
        val s = o.optJSONArray("start"); if (s != null && s.length() >= 2) { startPt = GeoPoint(s.getDouble(0), s.getDouble(1)); startName = s.optString(2, "Salida") } else { startPt = null; startName = "" }
        val e = o.optJSONArray("end"); if (e != null && e.length() >= 2) { endPt = GeoPoint(e.getDouble(0), e.getDouble(1)); endName = e.optString(2, "Destino") } else { endPt = null; endName = "" }
        paradas.clear(); val pa = o.optJSONArray("paradas"); if (pa != null) for (i in 0 until pa.length()) { val p = pa.getJSONArray(i); paradas.add(GeoPoint(p.getDouble(0), p.getDouble(1)) to p.optString(2, "Parada")) }
        selectTab(0); updRouteInfo(); calcRoute(); toast("Ruta cargada")
    }

    // ---------------- RUTA ----------------
    private fun calcRoute() {
        if (startPt == null) { navAfterCalc = false; toast("Elige SALIDA (toca una fuente → Poner como SALIDA)"); return }
        if (endPt == null && paradas.isEmpty()) { navAfterCalc = false; toast("Elige DESTINO o alguna parada"); return }
        val MAX_WP = 498
        if (paradas.size > MAX_WP) toast("Máximo ~500 puntos por ruta: se usarán los primeros $MAX_WP")
        val useP = if (paradas.size > MAX_WP) paradas.take(MAX_WP) else paradas
        val pts = ArrayList<GeoPoint>(); pts.add(startPt!!); useP.forEach { pts.add(it.first) }; endPt?.let { pts.add(it) }
        if (pts.size < 2) { toast("Faltan puntos"); return }
        val coords = pts.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.longitude, it.latitude) }
        val url = "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson&steps=true"
        statusText.text = "⏳ Calculando…"; statusText.visibility = View.VISIBLE
        scope.launch {
            try {
                val body = withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(url).header("User-Agent", "rutasfuentes/1.0").build()).execute().use { it.body?.string() ?: "" } }
                val r0 = JSONObject(body).getJSONArray("routes").getJSONObject(0)
                val geo = r0.getJSONObject("geometry").getJSONArray("coordinates")
                routePts.clear()
                for (i in 0 until geo.length()) { val c = geo.getJSONArray(i); routePts.add(GeoPoint(c.getDouble(1), c.getDouble(0))) }
                steps.clear(); stepPts.clear()
                val legs = r0.getJSONArray("legs")
                for (li in 0 until legs.length()) { val st = legs.getJSONObject(li).getJSONArray("steps")
                    for (si in 0 until st.length()) { val s = st.getJSONObject(si); val man = s.getJSONObject("maneuver"); val nm = s.optString("name")
                        val loc = man.getJSONArray("location"); stepPts.add(GeoPoint(loc.getDouble(1), loc.getDouble(0)))
                        steps.add(describe(man.optString("type"), man.optString("modifier")) + (if (nm.isNotEmpty()) " · $nm" else "")) } }
                drawRoute()
                val mins = (r0.getDouble("duration") / 60).toInt(); val hh = mins / 60; val mm = mins % 60
                val timeStr = if (hh > 0) "$hh h $mm min" else "$mm min"
                statusText.visibility = View.GONE
                routeTotalKm = r0.getDouble("distance") / 1000.0
                routeTotalMin = r0.getDouble("duration") / 60.0
                routeStatsText = fmtRouteStats()
                styleStats()
                routeStatsRow.visibility = View.VISIBLE
                routeStats.post { layoutFab() }
                if (navAfterCalc) { navAfterCalc = false; startNav() }
            } catch (e: Exception) { statusText.text = "❌ Error de ruta"; toast("Error: ${e.message}") }
        }
    }

    // ---------------- CLIMA POR DÍA ----------------
    private fun wmoIcon(c: Int): String = when (c) {
        0 -> "☀️"; in 1..3 -> "⛅"; in 45..48 -> "🌫️"; in 51..57 -> "🌦️"; in 61..67 -> "🌧️"; in 71..77 -> "❄️"; in 80..82 -> "🌦️"; in 95..99 -> "⛈️"; else -> "🌡️"
    }

    private fun routePointNames(): List<Pair<GeoPoint, String>> {
        val out = ArrayList<Pair<GeoPoint, String>>()
        startPt?.let { out.add(it to startName.ifEmpty { "Salida" }) }
        paradas.forEach { out.add(it.first to it.second) }
        endPt?.let { out.add(it to endName.ifEmpty { "Destino" }) }
        return out
    }

    private fun weatherDialog() {
        val pts = routePointNames()
        if (pts.isEmpty()) { toast("Añade salida, paradas o destino"); return }
        val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val dlg = android.app.DatePickerDialog(this, { _, y, m, d ->
            loadWeather(pts, String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
        dlg.datePicker.minDate = System.currentTimeMillis() - 3600000L
        dlg.datePicker.maxDate = System.currentTimeMillis() + 16L * 86400000L
        dlg.setTitle("🌤️ Clima · elige el día")
        dlg.show()
    }

    private fun loadWeather(pts: List<Pair<GeoPoint, String>>, date: String) {
        statusText.text = "\u23F3 Clima $date."; statusText.visibility = View.VISIBLE
        scope.launch {
            try {
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), dp(6)) }
                var n = 0
                pts.forEach { pr ->
                    val p = pr.first
                    val u = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code&timezone=auto&start_date=%s&end_date=%s", p.latitude, p.longitude, date, date)
                    val body = withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(u).header("User-Agent", "rutasfuentes/1.0").build()).execute().use { it.body?.string() ?: "" } }
                    val d = JSONObject(body).getJSONObject("daily")
                    val tmin = d.getJSONArray("temperature_2m_min").getDouble(0)
                    val tmax = d.getJSONArray("temperature_2m_max").getDouble(0)
                    val prc = d.getJSONArray("precipitation_sum").optDouble(0, 0.0)
                    val cod = d.getJSONArray("weather_code").getInt(0)
                    if (n > 0) box.addView(View(this@MainActivity).apply { setBackgroundColor(DIVIDER); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)) })
                    n++
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
                    row.addView(tv(wmoIcon(cod), 22f, DARK).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) } })
                    val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    col.addView(tv(pr.second, 14.5f, DARK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    col.addView(tv(String.format(Locale.US, "%s · %.1f mm", wmo(cod), prc), 12.5f, TEXT2).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    row.addView(col)
                    val tTxt = String.format(Locale.US, "%.0f° / %.0f°", tmax, tmin)
                    val tt = android.text.SpannableString(tTxt)
                    val k = tTxt.indexOf("/").coerceAtLeast(0)
                    tt.setSpan(android.text.style.ForegroundColorSpan(ORANGE_TEXT), 0, k, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    row.addView(tv("", 14.5f, DARK, true).apply { text = tt; gravity = Gravity.END })
                    box.addView(row)
                }
                statusText.visibility = View.GONE
                val sc = android.widget.ScrollView(this@MainActivity).apply { addView(box) }
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("\uD83C\uDF26\uFE0F Clima · $date")
                    .setView(sc)
                    .setPositiveButton("Cerrar", null)
                    .show()
            } catch (e: Exception) {
                statusText.text = "\u26A0 Clima: ${e.message}"
                toast("Error clima: ${e.message}")
            }
        }
    }

    private fun elevationDialog() {
        if (routePts.size < 2) { toast("Calcula la ruta primero"); return }
        statusText.text = "⏳ Perfil de altura…"; statusText.visibility = View.VISIBLE
        scope.launch {
            try {
                val step = Math.max(1, (routePts.size + 99) / 100)
                val sample = ArrayList<GeoPoint>()
                var i = 0
                while (i < routePts.size && sample.size < 100) { sample.add(routePts[i]); i += step }
                if (sample.size < 100 && sample.last() != routePts.last()) sample.add(routePts.last())
                val lat = sample.joinToString(",") { String.format(Locale.US, "%.5f", it.latitude) }
                val lon = sample.joinToString(",") { String.format(Locale.US, "%.5f", it.longitude) }
                val u = "https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lon"
                val body = withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(u).header("User-Agent", "rutasfuentes/1.0").build()).execute().use { it.body?.string() ?: "" } }
                val arr = JSONObject(body).getJSONArray("elevation")
                val data = ArrayList<Pair<Double, Double>>()
                var dist = 0.0; var up = 0.0; var dn = 0.0; var prevE = 0.0
                for (k in 0 until arr.length()) {
                    if (k > 0) dist += sample[k - 1].distanceToAsDouble(sample[k]) / 1000.0
                    val e = arr.getDouble(k)
                    if (k > 0) { if (e > prevE) up += e - prevE else dn += prevE - e }
                    prevE = e
                    data.add(dist to e)
                }
                statusText.visibility = View.GONE
                showElevDialog(data, up, dn)
            } catch (e: Exception) {
                statusText.text = "❌ Perfil: ${e.message}"
                toast("Error perfil: ${e.message}")
            }
        }
    }

    private fun showElevDialog(data: List<Pair<Double, Double>>, up: Double, down: Double) {
        if (data.size < 2) { toast("Sin datos de altura"); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#111111")); setPadding(dp(8), dp(8), dp(8), dp(12)) }
        box.addView(ElevProfileView(this, data), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)))
        val eMin = data.minOf { it.second }; val eMax = data.maxOf { it.second }
        val stats = String.format(Locale.US, "⬆ %.0f m    ⬇ %.0f m\nmáx %.0f m · mín %.0f m · %.1f km", up, down, eMax, eMin, data.last().first)
        box.addView(tv(stats, 13f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        android.app.AlertDialog.Builder(this).setTitle("📈 Perfil de altura").setView(box).setPositiveButton("Cerrar", null).show()
    }

    private inner class ElevProfileView(context: android.content.Context, private val pts: List<Pair<Double, Double>>) : View(context) {
        private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE; style = android.graphics.Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); strokeJoin = android.graphics.Paint.Join.ROUND }
        private val fillP = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }
        private val grid = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFFFFF"); strokeWidth = 1f }
        private val txt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B3FFFFFF"); textSize = dp(11).toFloat() }

        override fun onDraw(c: android.graphics.Canvas) {
            super.onDraw(c)
            if (pts.size < 2) return
            val padL = dp(46).toFloat(); val padR = dp(6).toFloat(); val padT = dp(12).toFloat(); val padB = dp(18).toFloat()
            val w = width - padL - padR; val h = height - padT - padB
            if (w <= 0 || h <= 0) return
            val dMax = pts.last().first.coerceAtLeast(0.001)
            val eMin = pts.minOf { it.second }; val eMax = pts.maxOf { it.second }
            val span = (eMax - eMin).coerceAtLeast(1.0)
            for (k in 0..2) {
                val e = eMin + span * k / 2.0
                val y = padT + (1.0 - (e - eMin) / span).toFloat() * h
                c.drawLine(padL, y, padL + w, y, grid)
                c.drawText(String.format(Locale.US, "%.0f m", e), dp(6).toFloat(), y - dp(3).toFloat(), txt)
            }
            val path = android.graphics.Path(); val area = android.graphics.Path()
            pts.forEachIndexed { i, p ->
                val x = padL + (p.first / dMax).toFloat() * w
                val y = padT + (1.0 - (p.second - eMin) / span).toFloat() * h
                if (i == 0) { path.moveTo(x, y); area.moveTo(x, padT + h); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
            }
            area.lineTo(padL + w, padT + h); area.close()
            fillP.shader = android.graphics.LinearGradient(0f, padT, 0f, padT + h, Color.parseColor("#8CFF6A00"), Color.parseColor("#1AFF6A00"), android.graphics.Shader.TileMode.CLAMP)
            c.drawPath(area, fillP)
            c.drawPath(path, line)
            c.drawText("0", padL, height - dp(3).toFloat(), txt)
            val dl = String.format(Locale.US, "%.0f km", dMax)
            c.drawText(dl, padL + w - txt.measureText(dl), height - dp(3).toFloat(), txt)
        }
    }

    private fun describe(type: String, mod: String): String = when (type) {
        "depart" -> "▶️ Sal"; "arrive" -> "🏁 Llega a destino"
        "turn" -> when (mod) { "left" -> "↰ Izquierda"; "right" -> "↱ Derecha"; "slight left" -> "↖ Leve izq."; "slight right" -> "↗ Leve der."; "sharp left" -> "⤴ Fuerte izq."; "sharp right" -> "⤵ Fuerte der."; "uturn" -> "↺ Cambio sentido"; else -> "↱ Gira" }
        "merge" -> "Incorpórate"; "fork" -> "Bifurcación"; "roundabout", "rotary" -> "🔄 Rotonda"; "end of road" -> "Fin de vía"; else -> "Continúa"
    }

    // Alfiler de mapa (punta abajo): salida y destino
    private fun pin(color: Int, sizeDp: Int = 34): android.graphics.drawable.BitmapDrawable {
        val w = dp(sizeDp); val h = dp((sizeDp * 1.36f).toInt())
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val r = w / 2f - dp(1)
        val cx = w / 2f; val cy = r + dp(1)
        val path = android.graphics.Path()
        path.addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
        val tail = android.graphics.Path()
        tail.moveTo(cx - r * 0.60f, cy + r * 0.58f)
        tail.lineTo(cx + r * 0.60f, cy + r * 0.58f)
        tail.lineTo(cx, (h - dp(1)).toFloat())
        tail.close()
        path.op(tail, android.graphics.Path.Op.UNION)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        fill.color = color; fill.style = android.graphics.Paint.Style.FILL
        c.drawPath(path, fill)
        val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        line.color = Color.WHITE; line.style = android.graphics.Paint.Style.STROKE
        line.strokeWidth = dp(2).toFloat(); line.strokeJoin = android.graphics.Paint.Join.ROUND
        c.drawPath(path, line)
        val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        dot.color = Color.WHITE; dot.style = android.graphics.Paint.Style.FILL
        c.drawCircle(cx, cy, r * 0.34f, dot)
        return android.graphics.drawable.BitmapDrawable(resources, b)
    }

    // Círculo con el número de parada
    private fun numberedDot(color: Int, sizeDp: Int, n: Int): android.graphics.drawable.BitmapDrawable {
        val sz = dp(sizeDp)
        val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val r = sz / 2f - dp(1)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        fill.color = color; fill.style = android.graphics.Paint.Style.FILL
        c.drawCircle(sz / 2f, sz / 2f, r, fill)
        val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        line.color = Color.WHITE; line.style = android.graphics.Paint.Style.STROKE
        line.strokeWidth = dp(2).toFloat()
        c.drawCircle(sz / 2f, sz / 2f, r - dp(1), line)
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.WHITE; tp.textAlign = android.graphics.Paint.Align.CENTER
        tp.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tp.textSize = sz * (if (n >= 10) 0.46f else 0.56f)
        val fm = tp.fontMetrics
        c.drawText(n.toString(), sz / 2f, sz / 2f - (fm.ascent + fm.descent) / 2f, tp)
        return android.graphics.drawable.BitmapDrawable(resources, b)
    }

    private fun updateRoutePointMarkers() {
        startMarker?.let { map.overlays.remove(it) }; startMarker = null
        endMarker?.let { map.overlays.remove(it) }; endMarker = null
        stopMarkers.forEach { map.overlays.remove(it) }; stopMarkers.clear()
        startPt?.let {
            val m = Marker(map); m.position = it; m.icon = pin(Color.parseColor("#1B8A2F"), 30)
            m.title = startName.ifEmpty { "Salida" }
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(m); startMarker = m
        }
        paradas.forEachIndexed { i, pr ->
            val m = Marker(map); m.position = pr.first
            m.icon = numberedDot(ORANGE, 30, i + 1)
            m.title = "Parada ${i + 1}: ${pr.second}"
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            map.overlays.add(m); stopMarkers.add(m)
        }
        endPt?.let {
            val m = Marker(map); m.position = it; m.icon = pin(Color.parseColor("#E53935"), 34)
            m.title = endName.ifEmpty { "Destino" }
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(m); endMarker = m
        }
        map.invalidate()
    }

    private fun toggleRouteActions() {
        routeActionsHidden = !routeActionsHidden
        actionsBox?.visibility = if (routeActionsHidden) View.GONE else View.VISIBLE
        actionsHeader?.text = if (routeActionsHidden) "⚙️  Acciones de ruta    ▼" else "⚙️  Acciones de ruta    ▲"
        routeInfo.post { layoutFab() }
    }

    private fun undoRouteStep() {
        when {
            paradas.isNotEmpty() -> { paradas.removeAt(paradas.size - 1); toast("Parada quitada (${paradas.size}/10)") }
            endPt != null -> { endPt = null; endName = ""; toast("Destino quitado") }
            startPt != null -> { startPt = null; startName = ""; toast("Salida quitada") }
            else -> { toast("No hay nada que quitar"); return }
        }
        routePts.clear(); stepPts.clear(); steps.clear()
        hideRouteStats()
        drawRoute(); updRouteInfo(); map.invalidate()
    }

    private fun resetRoute() {
        startPt = null; startName = ""; gpsStartOn = false
        endPt = null; endName = ""
        paradas.clear()
        routePts.clear(); stepPts.clear(); steps.clear()
        hideRouteStats()
        if (navMode) stopNav()
        drawRoute(); updRouteInfo(); map.invalidate(); toast("🗑️ Ruta borrada")
    }

    private fun syncRouteButtons() {
        val on = routePts.isNotEmpty() && map.visibility == View.VISIBLE && !navMode
        if (::centrarBtn.isInitialized) centrarBtn.visibility = if (on) View.VISIBLE else View.GONE
        routeCenterActive = null
        updateRouteCenterButton()
    }

    private fun drawRoute() {
        routeBounds = if (routePts.size >= 2) try { BoundingBox.fromGeoPoints(routePts) } catch (_: Exception) { null } else null
        routeCenterActive = null
        syncRouteButtons()
        routeLine?.let { map.overlays.remove(it) }
        routeCasing?.let { map.overlays.remove(it) }
        routeLine = null; routeCasing = null
        if (routePts.isEmpty()) { scheduleRefresh(); map.invalidate(); return }
        // Contorno blanco para que el azul se vea sobre cualquier capa (satelite, NASA, topo)
        val caso = Polyline(map); caso.setPoints(ArrayList(routePts))
        caso.outlinePaint.color = Color.WHITE; caso.outlinePaint.strokeWidth = dp(12).toFloat()
        map.overlays.add(caso); routeCasing = caso
        val pl = Polyline(map); pl.setPoints(ArrayList(routePts)); pl.outlinePaint.color = ROUTE_BLUE; pl.outlinePaint.strokeWidth = dp(8).toFloat()
        map.overlays.add(pl); routeLine = pl
        try { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(routePts).increaseByScale(1.15f), true) } catch (_: Exception) {}
        scheduleRefresh()
        map.invalidate()
    }

    private fun toggleNav() {
        if (navMode) { stopNav(); return }
        if (routePts.isEmpty()) { navAfterCalc = true; toast("Calculando ruta…"); calcRoute(); return }
        startNav()
    }
    private fun startNav() {
        navMode = true; navFreePan = false; lastNavStep = -1; navBanner.visibility = View.VISIBLE; statusText.visibility = View.GONE
        main.removeCallbacks(refreshRunnable)
        syncRouteButtons()
        navBanner.post { layoutFab() }
        if (!routePanelHidden) { routePanelHidden = true; routeInfo.visibility = View.GONE; syncBarRings(); layoutFab() }
        navBtn.background = ring(GREEN_FUENTE, 50, 3); myLoc.enableMyLocation(); toast("\uD83E\uDDED Navegaci\u00F3n iniciada")
    }
    private fun stopNav() {
        navMode = false; navFreePan = false; lastNavStep = -1; navBanner.visibility = View.GONE
        scheduleRefresh()
        syncRouteButtons()
        navBanner.post { layoutFab() }
        navBtn.background = ring(ORANGE, 50, 3); followArrow?.let { map.overlays.remove(it) }; followArrow = null; toast("Navegaci\u00F3n detenida")
    }

    // ---------------- GRABAR ----------------
    private fun toggleRecord() {
        recording = !recording
        if (recording) { lastLoc = null; trackDist = 0.0; myLoc.enableMyLocation(); recordBtn.text = "DETENER GRABACIÓN"; recordBtn.background = round(BAR, 10); recordText.text = "Grabando… 0.00 km" }
        else { recordBtn.text = "INICIAR GRABACIÓN"; recordBtn.background = round(ORANGE, 10); recordText.text = String.format(Locale.US, "Grabación finalizada · %.2f km", trackDist) }
    }

    // Créditos con tarjeta naranja a todo color
    private fun showCredits() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#FFA22B"), Color.parseColor("#DD4E00"))
            )
            bg.cornerRadius = dp(24).toFloat()
            background = bg
            setPadding(dp(22), dp(26), dp(22), dp(18))
        }
        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.credito_moto)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.WHITE)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat()) }
            }
            clipToOutline = true
            elevation = dp(4).toFloat()
        }, LinearLayout.LayoutParams(dp(250), dp(186)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) })
        box.addView(tv("IDEA ORIGINAL DE", 13f, Color.parseColor("#FFE7CC"), true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.22f; setPadding(0, dp(8), 0, 0)
        })
        box.addView(tv("LUCIANO CJ", 34f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
        box.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#66FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(16); bottomMargin = dp(16) }
        })
        box.addView(tv("«No me copies sino es para mejorarme»", 17f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.ITALIC)
            setLineSpacing(dp(5).toFloat(), 1f)
        })
        box.addView(tv("Rutas Moteras -Fuentes de Andaluc\u00EDa- \u00B7 13.719 fuentes", 12.5f, Color.parseColor("#FFE7CC")).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(16))
        })
        val btn = tv("ACEPTAR", 15f, Color.parseColor("#DD4E00"), true).apply {
            gravity = Gravity.CENTER; background = round(Color.WHITE, 26); setPadding(0, dp(13), 0, dp(13))
            elevation = dp(3).toFloat()
        }
        box.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        btn.setOnClickListener { dlg.dismiss() }
        dlg.show()
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun onMore(item: String) {
        when {
            item.contains("Orientaci") -> orientDialog()
            item.contains("Fotos: carpeta") -> photoSettings()
            item.contains("CP de la ruta") -> mostrarCPs()
            item.contains("COPIA") -> android.app.AlertDialog.Builder(this).setTitle("COPIA de datos").setItems(arrayOf("Exportar copia", "Importar copia")) { _, w -> if (w == 0) exportarDatos() else importarDatosPick() }.setNegativeButton("Cerrar", null).show()
            item.contains("GPX") -> android.app.AlertDialog.Builder(this).setTitle("GPX").setItems(arrayOf("Exportar GPX", "Importar GPX")) { _, w -> if (w == 0) exportGpx() else importGpx() }.setNegativeButton("Cerrar", null).show()
            item.contains("Grabar recorrido") -> selectTab(2)
            item.contains("Mis rutas") -> showSavedRoutes()
            item.contains("Exportar COPIA") -> exportarDatos()
            item.contains("Importar COPIA") -> importarDatosPick()
            item.contains("Exportar GPX") -> exportGpx()
            item.contains("Importar GPX") -> importGpx()
            item.contains("Tráfico") -> startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://infocar.dgt.es/etraffic/")))
            item.contains("Acerca") -> showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "1.6" }
        val sp = android.text.SpannableStringBuilder()
        fun add(text: String, green: Boolean = false, center: Boolean = false, bold: Boolean = false, scale: Float = 1f) {
            val start = sp.length
            sp.append(text)
            val end = sp.length
            if (green) sp.setSpan(android.text.style.ForegroundColorSpan(GREEN_FUENTE), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (scale != 1f) sp.setSpan(android.text.style.RelativeSizeSpan(scale), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (center) sp.setSpan(android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        add("Rutas Moteras V-$version\n", green = true, center = true, bold = true, scale = 1.30f)
        add("Fuentes de Andaluc\u00EDa\n\n", green = true, center = true, bold = true, scale = 1.10f)
        add("C\u00D3MO USAR:\n\n", green = true, center = true, bold = true, scale = 1.06f)
        add("\uD83C\uDFCD RUTA (icono moto)\n")
        add("Abre o cierra Configurar ruta: Salida, Paradas (m\u00E1x. 10) y Destino. Cada punto admite GPS, buscador o Manual. Atr\u00E1s quita el \u00FAltimo punto, Calcular traza la ruta y Reset la vac\u00EDa.\n\n")
        add("\u2699\uFE0F RUTA (engranaje)\n")
        add("Compartir por WhatsApp o Google Maps, Guardar, Grabar recorrido y Mis rutas guardadas. Tambi\u00E9n abre el perfil de altura y la navegaci\u00F3n giro a giro.\n\n")
        add("\u26F2 FUENTES\n")
        add("Fuentes y manantiales cerca de ti, de 10 a 200 km. La lupa busca por nombre, pueblo o c\u00F3digo postal. Toca una fuente para ver distancia, acceso, fotos y notas, o usarla como Salida, Parada o Destino.\n\n")
        add("\u2630 DATOS\n")
        add("CP de la ruta, GPX (exportar o importar), Orientaci\u00F3n del mapa, Copia ZIP con fotos y notas, y carpeta de Fotos.\n\n")
        add("\u2139\uFE0F INFO\n")
        add("Esta ayuda, Acerca de y Cr\u00E9ditos.\n\n")
        add("\uD83D\uDD0D \u00BFA d\u00F3nde?\n")
        add("Busca fuentes, pueblos o c\u00F3digos postales. Manual abre un punto de mira: mueve el mapa y pulsa + o toca el mapa para fijarlo.\n\n")
        add("\uD83D\uDDFA\uFE0F Capas\n")
        add("Mapa, Sat\u00E9lite, Relieve, Bici y Tr\u00E1fico DGT. El bot\u00F3n de nubes abre NASA, radar de lluvia y clima.\n\n")
        add("\uD83C\uDFAF Diana\n")
        add("Centra el mapa en tu posici\u00F3n y lo deja Norte arriba. El aro es verde cuando est\u00E1s centrado al norte y naranja cuando no.\n\n")
        add("\uD83E\uDDED Modo Br\u00FAjula / GPS\n")
        add("El bot\u00F3n de la br\u00FAjula alterna entre rumbo del sensor y rumbo GPS. En GPS, si no hay rumbo fiable, usa la br\u00FAjula. La rosa muestra el Norte del mapa y tu rumbo; gira el mapa con dos dedos.\n\n")
        add("Barra inferior: Ruta, engranaje Ruta, Fuentes, Datos e Info. Cada icono abre su pantalla; otra pulsaci\u00F3n vuelve al mapa. Atr\u00E1s cierra men\u00FAs, nunca la app.")
        android.app.AlertDialog.Builder(this)
            .setMessage(sp)
            .setNeutralButton("\uD83C\uDFCD Cr\u00E9ditos") { _, _ -> showCredits() }
            .setPositiveButton("OK", null)
            .setOnDismissListener { infoIcon?.background = ring(ORANGE, 50) }
            .show()
    }

    // ---------------- GPX ----------------
    private fun routePointList(): List<GeoPoint> {
        val pts = ArrayList<GeoPoint>(); startPt?.let { pts.add(it) }; paradas.forEach { pts.add(it.first) }; endPt?.let { pts.add(it) }
        return if (routePts.size >= 2) routePts else pts
    }
    private fun exportGpx() {
        val pts = routePointList()
        if (pts.size < 2) { toast("Calcula o define una ruta primero"); return }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Rutas Fuentes\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n<trk><name>Ruta Fuentes</name><trkseg>\n")
        pts.forEach { sb.append("<trkpt lat=\"").append(it.latitude).append("\" lon=\"").append(it.longitude).append("\"></trkpt>\n") }
        sb.append("</trkseg></trk>\n</gpx>")
        try {
            val f = File(cacheDir, "ruta_fuentes.gpx"); f.writeText(sb.toString())
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "application/gpx+xml"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(android.content.Intent.createChooser(i, "Exportar GPX"))
        } catch (e: Exception) { toast("Error GPX: ${e.message}") }
    }
    private fun importGpx() {
        val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE); type = "*/*" }
        gpxPicker.launch(i)
    }
    private val gpxPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val re = Regex("<(?:trkpt|rtept)[^>]*lat=\"([-0-9.]+)\"[^>]*lon=\"([-0-9.]+)\"")
            val pts = re.findAll(text).map { GeoPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }.toList()
            if (pts.size < 2) { toast("GPX sin puntos suficientes"); return@registerForActivityResult }
            routePts.clear(); routePts.addAll(pts)
            paradas.clear(); if (pts.size > 2) for (i in 1 until pts.size - 1) { if (paradas.size >= 10) break; paradas.add(pts[i] to "GPX") }
            startPt = pts.first(); startName = "GPX"; gpsStartOn = false; endPt = pts.last(); endName = "GPX"
            selectTab(0); drawRoute(); updRouteInfo(); toast("GPX importado (${pts.size} puntos)")
        } catch (e: Exception) { toast("Error al importar: ${e.message}") }
    }

    // ---------------- CLIMA ----------------
    private fun showWeather() {
        val c = myLoc.myLocation ?: map.mapCenter ?: return
        statusText.text = "⏳ Clima…"; statusText.visibility = View.VISIBLE
        scope.launch {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${c.latitude}&longitude=${c.longitude}&current=temperature_2m,wind_speed_10m,weather_code"
                val body = withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(url).header("User-Agent", "rutasfuentes/1.0").build()).execute().use { it.body?.string() ?: "" } }
                val cur = JSONObject(body).getJSONObject("current")
                val t = cur.getDouble("temperature_2m"); val w = cur.getDouble("wind_speed_10m"); val code = cur.getInt("weather_code")
                android.app.AlertDialog.Builder(this@MainActivity).setTitle("🌤️ Clima aquí")
                    .setMessage("Temperatura: $t °C\nViento: $w km/h\nCielo: ${wmo(code)}").setPositiveButton("OK", null).show()
                statusText.visibility = View.GONE
            } catch (e: Exception) { statusText.text = "❌ Clima: ${e.message}" }
        }
    }
    private fun wmo(c: Int): String = when (c) { 0 -> "Despejado"; in 1..3 -> "Poco nuboso"; 45, 48 -> "Niebla"; in 51..67 -> "Lluvia débil"; in 71..77 -> "Nieve"; in 80..82 -> "Chubascos"; in 95..99 -> "Tormenta"; else -> "—" }

    // ---------------- GPS ----------------
    private fun askLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    private val locLoop = object : Runnable {
        override fun run() {
            val fix = myLoc.lastFix
            val p = myLoc.myLocation
            if (fix != null && p != null && (fix.time != lastProcessedFixTime || lastProcessedFixTime == 0L)) {
                lastProcessedFixTime = fix.time
                val gp = GeoPoint(p.latitude, p.longitude)
                val bearing = lastLoc?.let { b -> bearingBetween(b, gp) } ?: 0f
                onLocation(gp, bearing, fix.speed)
            }
            main.postDelayed(this, 2000)
        }
    }

    private fun bearingBetween(a: GeoPoint, b: GeoPoint): Float {
        val r = FloatArray(2); android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r); return r[1]
    }

    // ---------------- BRUJULA (capa satelite) ----------------
    private var gpsBearing = -1f
    private var gpsBearingAt = 0L
    private var compassRoseOn = true

    private fun centerOnGps() {
        myLoc.enableMyLocation()
        val p = myLoc.myLocation
        if (p == null) { toast("Esperando GPS\u2026"); return }
        myLoc.disableFollowLocation()
        navFreePan = false
        map.controller.animateTo(p)
        map.setMapOrientation(0f)
        map.invalidate()
        lastRoseDeg = -1
        applyHeading()
        map.post { placeRoseOnMap() }
        map.postDelayed({ placeRoseOnMap() }, 350L)
        map.postDelayed({ updateCenterButton() }, 700L)
        toast("Centrado \u00B7 Norte arriba")
    }

    private fun placeRoseOnMap() {
        val c = roseCard ?: return
        if (!layerShowsRose()) { c.visibility = View.INVISIBLE; return }
        val p = myLoc.myLocation ?: lastLoc
                if (p == null) { c.visibility = View.INVISIBLE; return }
        if (c.visibility != View.VISIBLE) c.visibility = View.VISIBLE
        val rv = roseView
        if (c.width == 0 || rv == null || rv.width == 0 || rv.top == 0) {
            if (!roseRetryPending) {
                roseRetryPending = true
                c.postDelayed({ roseRetryPending = false; placeRoseOnMap() }, 80L)
            }
            return
        }
        val pt = android.graphics.Point()
        map.projection.toPixels(p, pt)
        // El mapa puede estar girado: Projection no aplica la rotacion, la aplicamos aqui
        val ang = Math.toRadians(map.mapOrientation.toDouble())
        if (Math.abs(ang) > 0.001) {
            val cpt = android.graphics.Point()
            map.projection.toPixels(map.mapCenter, cpt)
            val dx = (pt.x - cpt.x).toFloat()
            val dy = (pt.y - cpt.y).toFloat()
            val cosA = Math.cos(ang).toFloat()
            val sinA = Math.sin(ang).toFloat()
            pt.x = Math.round(map.width / 2f + dx * cosA - dy * sinA)
            pt.y = Math.round(map.height / 2f + dx * sinA + dy * cosA)
        }
        val rw = rv.width
        val rh = rv.height
        val left = pt.x - rv.left - rw / 2
        val top = pt.y - rv.top - rh / 2
        val lp = c.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.leftMargin != left || lp.topMargin != top) {
            lp.leftMargin = left
            lp.topMargin = top
            c.layoutParams = lp
        }
        c.visibility = View.VISIBLE
        c.alpha = 1f
    }

    private fun cardinalOf(h: Float): String {
        val i = ((((h % 360f) + 360f) % 360f + 22.5f) / 45f).toInt() % 8
        return arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[i]
    }

    private fun headingForMode(sensorAz: Float? = null): Float {
        val comp = sensorAz ?: compassHeading
        val bear = if (gpsBearing >= 0f && android.os.SystemClock.elapsedRealtime() - gpsBearingAt < 20000L) gpsBearing else -1f
        val h = if (compassRoseOn) {
            when { comp >= 0f -> comp; bear >= 0f -> bear; else -> 0f }
        } else {
            when { bear >= 0f -> bear; comp >= 0f -> comp; else -> 0f }
        }
        return ((h % 360f) + 360f) % 360f
    }

    private fun updateArrow(h: Float): Boolean {
        if (!::arrowDrawable.isInitialized) return false
        var changed = false
        val north = ((map.mapOrientation % 360f) + 360f) % 360f
        if (::locMarker.isInitialized) {
            val en = !compassRoseOn
            if (locMarker.isEnabled != en) { locMarker.isEnabled = en; changed = true }
        }
        if (!compassRoseOn) {
            val rot = ((north + h) % 360f + 360f) % 360f
            val d = Math.abs(((rot - lastArrowRot + 540f) % 360f) - 180f)
            if (d > 0.35f || lastArrowRot < 0f) {
                lastArrowRot = rot
                arrowDrawable.level = ((rot / 360f) * 10000f).toInt()
                changed = true
            }
            if (::locMarker.isInitialized && locMarker.icon !== arrowDrawable) { locMarker.icon = arrowDrawable; changed = true }
        }
        return changed
    }

    private fun updateCenterButton() {
        if (!::gpsCenterBtn.isInitialized) return
        var ok = false
        val p = myLoc.myLocation
        if (p != null) {
            val north = ((map.mapOrientation % 360f) + 360f) % 360f
            if (Math.min(north, 360f - north) <= 1.2f) {
                val pt = android.graphics.Point()
                map.projection.toPixels(p, pt)
                val dx = (pt.x - map.width / 2).toDouble()
                val dy = (pt.y - map.height / 2).toDouble()
                ok = Math.hypot(dx, dy) <= dp(16).toDouble()
            }
        }
        if (ok == centerBtnOk) return
        centerBtnOk = ok
        gpsCenterBtn.background = round(Color.parseColor("#CC000000"), 19, 4, if (ok) GREEN_FUENTE else ORANGE)
        gpsCenterBtn.contentDescription = if (ok) "Centrado al norte" else "Centrar en tu ubicaci\u00F3n"
    }

    private fun updateRose(hIn: Float? = null) {
        val v = roseView ?: return
        val h = hIn ?: headingForMode()
        v.northDeg = ((map.mapOrientation % 360f) + 360f) % 360f
        v.headingDeg = h
        val deg = (((Math.round(h) % 360) + 360) % 360).toInt()
        if (deg != lastRoseDeg) {
            lastRoseDeg = deg
            roseText?.text = "$deg\u00B0" + cardinalOf(h)
        }
    }

    private fun applyHeading(sensorAz: Float? = null) {
        if (!::arrowDrawable.isInitialized) return
        if (map.visibility != View.VISIBLE) return
        val h = headingForMode(sensorAz)
        val changed = updateArrow(h)
        if (compassRoseOn) updateRose(h)
        if (changed) map.invalidate()
    }

    private fun orientDialog() {
        android.app.AlertDialog.Builder(this).setTitle("Orientaci\u00F3n del mapa")
            .setMessage("La rosa y la flecha muestran el rumbo real (br\u00FAjula o GPS). El bot\u00F3n de centrar deja el mapa Norte arriba; con dos dedos puedes girarlo libremente.")
            .setPositiveButton("Vale", null).show()
    }

    private var lastPainting: GeoPoint? = null
    private var lastHeading = -999f
    private var lastStatsAt = 0L

    private fun onLocation(p: GeoPoint, bearing: Float, speed: Float) {
        if (recording) { lastLoc?.let { trackDist += it.distanceToAsDouble(p) / 1000.0 }; recordText.text = String.format(Locale.US, "Grabando… %.2f km", trackDist) }
        val prev = lastLoc
        if (prev != null && speed >= 0.8f && prev.distanceToAsDouble(p) > 1.0) {
            gpsBearing = ((bearing % 360f) + 360f) % 360f
            gpsBearingAt = android.os.SystemClock.elapsedRealtime()
        }
        lastLoc = p
        val heading = headingForMode()
        locMarker.position = p
        val arrowChanged = updateArrow(heading)
        if (compassRoseOn && layerIdx != 4) {
            updateRose(heading)
            if (layerShowsRose()) placeRoseOnMap()
        }
        if (navMode) {
            if (!navFreePan) { map.controller.setCenter(p); if (map.zoomLevelDouble < 16.5) map.controller.setZoom(17.0) }
            var nxtIndex = -1; var nxtDist = Double.MAX_VALUE
            for (i in stepPts.indices) {
                val d = stepPts[i].distanceToAsDouble(p)
                if (d > 12 && d < nxtDist) { nxtDist = d; nxtIndex = i }
            }
            val navText = if (nxtIndex >= 0) "▲ ${nxtDist.toInt()} m   " + (steps.getOrNull(nxtIndex) ?: "") else "▲  Sigue la ruta"
            if (navBanner.text.toString() != navText) navBanner.text = navText
            if (nxtIndex != lastNavStep) { lastNavStep = nxtIndex; navBanner.post { layoutFab() } }
        }
        // Solo redibujar si te has movido (>4 m) o si gira bastante la brújula
        val moved = lastPainting == null || lastPainting!!.distanceToAsDouble(p) > 4.0
        val turned = Math.abs(heading - lastHeading) > 10f && !navMode
        // Los km/tiempo restantes se recalculan como mucho cada 5 s
        val now = System.currentTimeMillis()
        if (routeStatsText.isNotEmpty() && routeTotalKm > 0.0 && (moved || now - lastStatsAt > 5000)) {
            routeStatsText = fmtRouteStats(); lastStatsAt = now; styleStats()
        }
        updateCenterButton()
        if (moved || turned || navMode || arrowChanged) { map.invalidate(); lastPainting = p; lastHeading = heading }
    }

    private fun registerCompass() {
        if (compassRegistered) return
        val sm = getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager ?: return
        sensorMgr = sm
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) ?: return
        compassRegistered = sm.registerListener(compassListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun unregisterCompass() {
        if (!compassRegistered) return
        try { sensorMgr?.unregisterListener(compassListener) } catch (_: Exception) {}
        compassRegistered = false
    }

    override fun onResume() {
        super.onResume(); map.onResume(); myLoc.enableMyLocation()
        lastProcessedFixTime = 0L
        main.removeCallbacks(locLoop); main.postDelayed(locLoop, 1500)
        if (currentTab == 0) registerCompass() else unregisterCompass()
    }
    override fun onPause() {
        super.onPause(); map.onPause(); myLoc.disableMyLocation(); main.removeCallbacks(locLoop); unregisterCompass()
    }
    override fun onDestroy() {
        main.removeCallbacks(locLoop); main.removeCallbacks(refreshRunnable); main.removeCallbacks(statusHide)
        unregisterCompass(); scope.cancel(); http.dispatcher.cancelAll()
        super.onDestroy()
    }

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    private val statusHide = Runnable { statusText.visibility = View.GONE }
    // Muestra un aviso; con autoHideMs=0 se queda hasta que cambie
    private fun showStatus(msg: String, autoHideMs: Long = 5000) {
        statusText.text = msg
        statusText.visibility = View.VISIBLE
        main.removeCallbacks(statusHide)
        if (autoHideMs > 0) main.postDelayed(statusHide, autoHideMs)
    }
}
