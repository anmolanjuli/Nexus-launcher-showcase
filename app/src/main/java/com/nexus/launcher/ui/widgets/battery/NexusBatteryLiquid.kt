package com.nexus.launcher.ui.widgets.battery

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * The Fluid Level style's liquid: the whole widget filled to its charge like a vessel.
 *
 * It used to be a flat rectangle at about a quarter opacity, clipped to the *content* box — so it
 * read as a slab of tint, and it floated inside the widget with square corners and margins rather
 * than filling it. It now spans the entire plate: [Canvas.getClipBounds] is the shape the renderer
 * has already clipped content to, so the liquid takes the widget's own outline — squircle, pill
 * or circle — without knowing which it is. A gradient from surface to depth gives it body, and a
 * gently waved surface with a highlight line is what makes it read as liquid rather than paint.
 */
internal object NexusBatteryLiquid {

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bodyPath = Path()
    private val surfacePath = Path()
    private val clip = Rect()
    private val plate = RectF()

    /** Whether [bodyPath] holds the liquid from the last [draw] — false when the charge was 0. */
    private var hasBody = false

    private var cachedShader: LinearGradient? = null
    private var cachedKey = ""

    /** Fills the clipped plate to [pct], rising from the bottom — or from the left when [sideways]. */
    fun draw(canvas: Canvas, dp: Float, pct: Float, sideways: Boolean, stateColor: Int, isGlass: Boolean, isLight: Boolean) {
        hasBody = false
        if (pct <= 0f) return
        canvas.getClipBounds(clip)
        plate.set(clip)
        if (plate.isEmpty) return

        val r = Color.red(stateColor)
        val g = Color.green(stateColor)
        val b = Color.blue(stateColor)
        // Frosted Glass: translucent, so the liquid is frosted too and the glass still reads
        // through it — a solid fill turned a full battery into a white slab. The reading is then
        // not inverted (see [invertsReading]); its legibility shadow carries it instead.
        // Elsewhere: near-opaque, because the inverted reading needs a solid ground.
        val surface = Color.argb(if (isGlass) 125 else 240, r, g, b)
        val deep = Color.argb(if (isGlass) 45 else 200, r, g, b)
        val span = if (sideways) plate.width() else plate.height()
        val amp = minOf(3f * dp, span * 0.035f)

        bodyPath.reset()
        surfacePath.reset()
        if (!sideways) {
            val level = plate.bottom - plate.height() * pct
            wave(level, plate.left, plate.right, amp, horizontal = true)
            bodyPath.set(surfacePath)
            bodyPath.lineTo(plate.right, plate.bottom)
            bodyPath.lineTo(plate.left, plate.bottom)
            liquidPaint.shader = shader(0f, level, 0f, plate.bottom, surface, deep)
        } else {
            val level = plate.left + plate.width() * pct
            wave(level, plate.top, plate.bottom, amp, horizontal = false)
            bodyPath.set(surfacePath)
            bodyPath.lineTo(plate.left, plate.bottom)
            bodyPath.lineTo(plate.left, plate.top)
            liquidPaint.shader = shader(level, 0f, plate.left, 0f, surface, deep)
        }
        bodyPath.close()
        canvas.drawPath(bodyPath, liquidPaint)
        hasBody = true

        // The surface: a crisp edge in the state colour, and a faint sheen just behind it.
        linePaint.strokeWidth = 1.6f * dp
        linePaint.color = Color.argb(235, r, g, b)
        canvas.drawPath(surfacePath, linePaint)
        canvas.save()
        if (sideways) canvas.translate(-1.6f * dp, 0f) else canvas.translate(0f, -1.6f * dp)
        linePaint.strokeWidth = 1f * dp
        linePaint.color = if (isLight) Color.argb(120, 255, 255, 255) else Color.argb(64, 255, 255, 255)
        canvas.drawPath(surfacePath, linePaint)
        canvas.restore()
    }

    /** Whether the reading should be inverted inside the liquid: only where the liquid is solid. */
    fun invertsReading(isGlass: Boolean): Boolean = !isGlass

    /**
     * Clips [canvas] to the liquid just drawn, for painting the inverted reading inside it.
     * Returns false when there is no liquid, so the caller can skip that pass.
     */
    fun clipToLiquid(canvas: Canvas): Boolean {
        if (!hasBody) return false
        canvas.clipPath(bodyPath)
        return true
    }

    /** A gentle sine along the surface — one and a quarter cycles, a few dp high. */
    private fun wave(level: Float, from: Float, to: Float, amp: Float, horizontal: Boolean) {
        val steps = 24
        val cycles = 1.25
        for (i in 0..steps) {
            val along = from + (to - from) * i / steps
            val offset = amp * sin(2.0 * Math.PI * cycles * i / steps).toFloat()
            val x = if (horizontal) along else level + offset
            val y = if (horizontal) level + offset else along
            if (i == 0) surfacePath.moveTo(x, y) else surfacePath.lineTo(x, y)
        }
    }

    /** Rebuilt only when geometry or colour changes; a widget renders at a handful of sizes. */
    private fun shader(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int): LinearGradient {
        val key = "$x0|$y0|$x1|$y1|$c0|$c1"
        cachedShader?.let { if (key == cachedKey) return it }
        return LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP).also {
            cachedShader = it
            cachedKey = key
        }
    }
}
