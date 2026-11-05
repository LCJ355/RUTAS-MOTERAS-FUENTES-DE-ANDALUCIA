package com.lcj355.rutasfuentes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Rosa de brujula transparente (sin fondo), estilo app Brujula:
 * marcas cada 5 grados, etiquetas cada 30, cardinales, cruz central,
 * arco naranja desde el rumbo hasta el Norte y marca triangular del Norte.
 */
class CompassRoseView(ctx: Context) : View(ctx) {

    /** Angulo de pantalla del Norte del mapa (horario desde arriba) */
    var northDeg: Float = 0f
        set(v) {
            val n = ((v % 360f) + 360f) % 360f
            if (Math.abs(n - field) > 0.35f) { field = n; invalidate() }
        }

    /** Rumbo real del sensor (brujula o GPS) */
    var headingDeg: Float = 0f
        set(v) {
            val n = ((v % 360f) + 360f) % 360f
            if (Math.abs(n - field) > 0.35f) { field = n; invalidate() }
        }

    private val density = resources.displayMetrics.density
    private fun d(v: Float) = v * density

    private val needleBmp: Bitmap? = try {
        BitmapFactory.decodeResource(resources, R.drawable.brujula_needle)
    } catch (_: Exception) { null }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val ACCENT = 0xFFFF6A00.toInt()
    private val TICK = 0xFF9AA6B4.toInt()
    private val CARDINAL = 0xFFD8DEE6.toInt()
    private val NUMGREEN = 0xFF2ECC71.toInt()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(d(3.6f), 0f, d(1.4f), 0xE6000000.toInt())
    }
    private val cardinals = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    private val degreeLabels = (0 until 360 step 30).map { it.toString() }
    private val arcRect = RectF()
    private val northPath = Path()
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d(2.4f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = 0xFF000000.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun outlinedText(canvas: Canvas, s: String, x: Float, y: Float, p: Paint) {
        halo.textSize = p.textSize
        canvas.drawText(s, x, y, halo)
        canvas.drawText(s, x, y, p)
    }

    private fun screenAngle(az: Float): Float = northDeg + az

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val R = Math.min(w, h) / 2f - d(2f)
        if (R <= d(10f)) return

        // 1) Aro exterior fino
        stroke.color = TICK
        stroke.alpha = 120
        stroke.strokeWidth = d(1f)
        canvas.drawCircle(cx, cy, R, stroke)
        stroke.alpha = 255

        // 2) Arco naranja: parte del Norte y llega al rumbo por el lado con menos grados
        // Arco del Norte al rumbo por el lado con menos grados
        val sweep = ((headingDeg + 540f) % 360f) - 180f
        if (Math.abs(sweep) > 1.5f) {
            stroke.color = ACCENT
            stroke.strokeWidth = d(5f)
            arcRect.set(cx - R + d(3f), cy - R + d(3f), cx + R - d(3f), cy + R - d(3f))
            canvas.drawArc(arcRect, screenAngle(0f) - 90f, sweep, false, stroke)
        }

        // 3) Marcas cada 5 grados (las cercanas al rumbo, en naranja)
        val rOut = R - d(10f)
        for (a in 0 until 360 step 5) {
            val major = a % 30 == 0
            val len = if (major) d(10f) else d(5.5f)
            val rad = Math.toRadians(screenAngle(a.toFloat()).toDouble())
            val ux = Math.sin(rad).toFloat()
            val uy = -Math.cos(rad).toFloat()
            val dist = Math.abs(((a - headingDeg + 540f) % 360f) - 180f)
            val near = dist <= 15f
            stroke.color = if (near) ACCENT else TICK
            stroke.alpha = if (near) 255 else 190
            stroke.strokeWidth = if (major) d(1.9f) else d(1.1f)
            canvas.drawLine(cx + ux * (rOut - len), cy + uy * (rOut - len), cx + ux * rOut, cy + uy * rOut, stroke)
        }
        stroke.alpha = 255

        // 4) Etiquetas de grados cada 30 (siempre rectas)
        label.textSize = d(20f)
        label.color = NUMGREEN
        val rNum = R * 0.80f
        for (a in 0 until 360 step 30) {
            val rad = Math.toRadians(screenAngle(a.toFloat()).toDouble())
            val x = cx + Math.sin(rad).toFloat() * rNum
            val y = cy - Math.cos(rad).toFloat() * rNum + d(3.8f)
            outlinedText(canvas, degreeLabels[a / 30], x, y, label)
        }

        // 5) Cardinales
        val rCard = R * 0.44f
        for (i in 0 until 8) {
            val rad = Math.toRadians(screenAngle(i * 45f).toDouble())
            val x = cx + Math.sin(rad).toFloat() * rCard
            val y = cy - Math.cos(rad).toFloat() * rCard + d(4.6f)
            label.color = if (i == 0) ACCENT else CARDINAL
            label.textSize = if (i == 0) d(20f) else d(16f)
            if (i == 0) canvas.drawText(cardinals[i], x, y, label) else outlinedText(canvas, cardinals[i], x, y, label)
        }

        // 7) Triangulo del Norte en el aro
        val north = Math.toRadians(screenAngle(0f).toDouble())
        val nx = cx + Math.sin(north).toFloat() * (R - d(2.5f))
        val ny = cy - Math.cos(north).toFloat() * (R - d(2.5f))
        val ix = cx + Math.sin(north).toFloat() * (R - d(9.5f))
        val iy = cy - Math.cos(north).toFloat() * (R - d(9.5f))
        val px = Math.cos(north).toFloat() * d(5.2f)
        val py = Math.sin(north).toFloat() * d(5.2f)
        northPath.reset()
        northPath.moveTo(ix, iy)
        northPath.lineTo(nx + px, ny + py)
        northPath.lineTo(nx - px, ny - py)
        northPath.close()
        fill.color = ACCENT
        canvas.drawPath(northPath, fill)

        // Aguja clasica encima de los numeros, girando con el rumbo
        val nb = needleBmp
        if (nb != null) {
            val nh = R * 1.24f
            val nw = nh * nb.width.toFloat() / nb.height.toFloat()
            val dst = RectF(cx - nw / 2f, cy - nh / 2f, cx + nw / 2f, cy + nh / 2f)
            canvas.save()
            canvas.rotate(northDeg + headingDeg, cx, cy)
            canvas.drawBitmap(nb, null, dst, needlePaint)
            canvas.restore()

            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(cx, cy, d(2f), fill)
            stroke.color = 0xFF555555.toInt()
            stroke.strokeWidth = d(0.8f)
            canvas.drawCircle(cx, cy, d(2f), stroke)
        }
    }
}
