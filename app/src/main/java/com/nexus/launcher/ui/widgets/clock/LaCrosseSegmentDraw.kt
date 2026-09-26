package com.nexus.launcher.ui.widgets.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.graphics.Typeface

/**
 * High-performance, zero-allocation LCD segment and indicator renderer.
 * Pre-builds 7-segment geometries in normalized unit coordinate spaces (100x180)
 * and renders active + 8% ghost segments with matrix scaling.
 */
object LaCrosseSegmentDraw {

    const val UNIT_W = 100f
    const val UNIT_H = 180f

    // Pre-allocated Paints (Zero allocation inside draw calls)
    val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    val silkscreenPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 7-segment unit paths: A, B, C, D, E, F, G
    // Index 0: A (Top), 1: B (Top-Right), 2: C (Bottom-Right), 3: D (Bottom)
    // 4: E (Bottom-Left), 5: F (Top-Left), 6: G (Middle)
    private val seg7Paths = Array(7) { Path() }

    // Colon, degree symbol, and status icon paths
    private val colonPath = Path()
    private val degreePath = Path()
    private val towerPath = Path()
    private val bellPath = Path()

    init {
        build7SegmentPaths()
        buildSymbols()
    }

    private fun build7SegmentPaths() {
        val t = 18f // segment thickness
        val g = 3f  // segment gap
        val midY = UNIT_H / 2f

        // A (Top)
        seg7Paths[0].apply {
            reset()
            moveTo(g + t / 2f, g)
            lineTo(UNIT_W - g - t / 2f, g)
            lineTo(UNIT_W - g - t, g + t)
            lineTo(g + t, g + t)
            close()
        }

        // B (Top-Right)
        seg7Paths[1].apply {
            reset()
            moveTo(UNIT_W - g, g + t / 2f)
            lineTo(UNIT_W - g, midY - g)
            lineTo(UNIT_W - g - t, midY - g - t / 2f)
            lineTo(UNIT_W - g - t, g + t)
            close()
        }

        // C (Bottom-Right)
        seg7Paths[2].apply {
            reset()
            moveTo(UNIT_W - g, midY + g)
            lineTo(UNIT_W - g, UNIT_H - g - t / 2f)
            lineTo(UNIT_W - g - t, UNIT_H - g - t)
            lineTo(UNIT_W - g - t, midY + g + t / 2f)
            close()
        }

        // D (Bottom)
        seg7Paths[3].apply {
            reset()
            moveTo(g + t, UNIT_H - g - t)
            lineTo(UNIT_W - g - t, UNIT_H - g - t)
            lineTo(UNIT_W - g - t / 2f, UNIT_H - g)
            lineTo(g + t / 2f, UNIT_H - g)
            close()
        }

        // E (Bottom-Left)
        seg7Paths[4].apply {
            reset()
            moveTo(g, midY + g)
            lineTo(g + t, midY + g + t / 2f)
            lineTo(g + t, UNIT_H - g - t)
            lineTo(g, UNIT_H - g - t / 2f)
            close()
        }

        // F (Top-Left)
        seg7Paths[5].apply {
            reset()
            moveTo(g, g + t / 2f)
            lineTo(g + t, g + t)
            lineTo(g + t, midY - g - t / 2f)
            lineTo(g, midY - g)
            close()
        }

        // G (Middle)
        seg7Paths[6].apply {
            reset()
            moveTo(g + t / 2f, midY)
            lineTo(g + t, midY - t / 2f)
            lineTo(UNIT_W - g - t, midY - t / 2f)
            lineTo(UNIT_W - g - t / 2f, midY)
            lineTo(UNIT_W - g - t, midY + t / 2f)
            lineTo(g + t, midY + t / 2f)
            close()
        }
    }

    private fun buildSymbols() {
        // Colon: two square dots
        colonPath.apply {
            reset()
            addRect(RectF(38f, 46f, 62f, 70f), Path.Direction.CW)
            addRect(RectF(38f, 110f, 62f, 134f), Path.Direction.CW)
        }

        // Degree symbol (°): top-right circle loop
        degreePath.apply {
            reset()
            addCircle(50f, 40f, 22f, Path.Direction.CW)
        }

        // WWVB Radio sync tower icon
        towerPath.apply {
            reset()
            moveTo(50f, 25f)
            lineTo(50f, 75f)
            moveTo(40f, 75f)
            lineTo(60f, 75f)
            moveTo(45f, 50f)
            lineTo(55f, 50f)
            addArc(RectF(25f, 20f, 75f, 70f), 200f, 140f)
            addArc(RectF(12f, 8f, 88f, 84f), 200f, 140f)
        }

        // Alarm bell icon
        bellPath.apply {
            reset()
            moveTo(50f, 20f)
            lineTo(50f, 26f)
            addArc(RectF(30f, 26f, 70f, 66f), 180f, 180f)
            lineTo(76f, 68f)
            lineTo(24f, 68f)
            lineTo(30f, 50f)
            close()
            addCircle(50f, 74f, 5f, Path.Direction.CW)
        }
    }

    /**
     * Renders a 7-segment digit (0-9, space, minus) at (x, y) with bounding width w and height h.
     */
    fun draw7SegmentDigit(canvas: Canvas, char: Char, x: Float, y: Float, w: Float, h: Float, showGhost: Boolean) {
        val mask = when (char) {
            '0' -> 0b0111111 // A, B, C, D, E, F
            '1' -> 0b0000110 // B, C
            '2' -> 0b1011011 // A, B, G, E, D
            '3' -> 0b1001111 // A, B, G, C, D
            '4' -> 0b1100110 // F, G, B, C
            '5' -> 0b1101101 // A, F, G, C, D
            '6' -> 0b1111101 // A, F, G, E, C, D
            '7' -> 0b0000111 // A, B, C
            '8' -> 0b1111111 // All 7
            '9' -> 0b1101111 // A, B, C, D, F, G
            '-' -> 0b1000000 // G
            else -> 0b0000000
        }

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(w / UNIT_W, h / UNIT_H)

        for (i in 0 until 7) {
            val isActive = (mask and (1 shl i)) != 0
            if (isActive) {
                canvas.drawPath(seg7Paths[i], activePaint)
            } else if (showGhost) {
                canvas.drawPath(seg7Paths[i], ghostPaint)
            }
        }
        canvas.restore()
    }

    /**
     * Delegates 14-segment character rendering to [LaCrosseSegment14].
     */
    fun draw14SegmentChar(canvas: Canvas, char: Char, x: Float, y: Float, w: Float, h: Float, showGhost: Boolean) {
        LaCrosseSegment14.draw(canvas, char, x, y, w, h, showGhost)
    }

    fun drawColon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, isColonOn: Boolean, showGhost: Boolean) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(w / UNIT_W, h / UNIT_H)
        val paint = if (isColonOn) activePaint else if (showGhost) ghostPaint else null
        if (paint != null) {
            canvas.drawPath(colonPath, paint)
        }
        canvas.restore()
    }

    fun drawDegreeSymbol(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(w / UNIT_W, h / UNIT_H)
        canvas.drawPath(degreePath, activePaint)
        canvas.restore()
    }

    fun drawStatusIcons(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.save()
        canvas.translate(x, y)
        val scale = size / 100f
        canvas.scale(scale, scale)

        // Radio sync tower icon
        iconStrokePaint.strokeWidth = 3f / scale
        iconStrokePaint.color = activePaint.color
        canvas.drawPath(towerPath, iconStrokePaint)

        canvas.translate(110f, 0f)
        canvas.drawPath(bellPath, activePaint)
        canvas.restore()
    }
}
