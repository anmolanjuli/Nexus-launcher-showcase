package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.theme.CalmPalette
import com.nexus.launcher.theme.NexusColorTokens

/**
 * A small drawn picture of a Premium feature that has no settings preview to borrow.
 *
 * Drawn in a 120-unit square centred in the view, so one drawing serves the 120dp tile and the
 * full-screen preview alike. Colours come from the tokens; the exceptions are the pictures *of*
 * a colour — Calm's palettes and AMOLED's black — which are the feature itself.
 */
internal class PremiumShowcaseMock(
    context: Context,
    private val feature: PremiumFeature,
    private val tokens: NexusColorTokens,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    /** One drawing unit. */
    private var u = 1f

    /** Set by [PremiumShowcaseThemeChips]: draw the Extra themes cards in this theme instead. */
    private var previewTheme: NexusColorTokens? = null
    private var previewPalette: CalmPalette? = null

    fun setPreviewTheme(theme: NexusColorTokens, palette: CalmPalette?) {
        previewTheme = theme
        previewPalette = palette
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        fill.shader = null
        fill.color = tokens.surfaceRaised
        canvas.drawRect(0f, 0f, w, h, fill)

        if (feature == PremiumFeature.SMART_SEARCH) return searchResults(canvas, w, h)

        u = minOf(w, h) / 120f
        canvas.save()
        canvas.translate((w - 120f * u) / 2f, (h - 120f * u) / 2f)
        when (feature) {
            PremiumFeature.EXTRA_THEMES -> themes(canvas)
            PremiumFeature.COLOR_ACCESSIBILITY -> accessibility(canvas)
            else -> icon(canvas, feature.iconRes, 60f, 60f, 44f, tokens.textSecondary)
        }
        canvas.restore()
    }

    private fun box(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        rect.set(l * u, t * u, r * u, b * u)
        canvas.drawRoundRect(rect, radius * u, radius * u, fill)
    }

    private fun outline(canvas: Canvas, radius: Float) {
        stroke.color = tokens.divider
        stroke.strokeWidth = u
        canvas.drawRoundRect(rect, radius * u, radius * u, stroke)
    }

    private fun label(canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface = Typeface.DEFAULT) {
        text.color = color
        text.textSize = size * u
        text.typeface = face
        canvas.drawText(s, x * u, y * u, text)
    }

    private fun icon(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float, tint: Int) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(tint)
        val half = size * u / 2f
        d.setBounds((cx * u - half).toInt(), (cy * u - half).toInt(), (cx * u + half).toInt(), (cy * u + half).toInt())
        d.draw(canvas)
    }

    /** A small screen of cards painted entirely in [theme]'s tokens, over its Calm gradient if any. */
    private fun themedCards(canvas: Canvas, theme: NexusColorTokens, palette: CalmPalette?) {
        rect.set(16f * u, 4f * u, 104f * u, 116f * u)
        if (palette != null) {
            fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, palette.previewTopColor, palette.previewBottomColor, Shader.TileMode.CLAMP)
        } else {
            fill.shader = null
            fill.color = theme.bg
        }
        canvas.drawRoundRect(rect, 12f * u, 12f * u, fill)
        outline(canvas, 12f)
        for (i in 0..1) {
            val t = 16f + i * 34f
            box(canvas, 24f, t, 96f, t + 28f, 7f, if (i == 0) theme.surface else theme.surfaceRaised)
            box(canvas, 31f, t + 8f, 72f, t + 12f, 2f, theme.textPrimary)
            box(canvas, 31f, t + 17f, 86f, t + 20f, 1.5f, theme.textSecondary)
        }
        box(canvas, 24f, 88f, 60f, 102f, 7f, theme.accent)
        fill.color = theme.accentMuted
        canvas.drawCircle(84f * u, 95f * u, 7f * u, fill)
    }

    /** Calm's Ocean Blue beside AMOLED's pure black, and a row of Calm palettes. */
    private fun themes(canvas: Canvas) {
        previewTheme?.let { return themedCards(canvas, it, previewPalette) }
        val calm = CalmPalette.OCEAN_BLUE
        rect.set(8f * u, 10f * u, 60f * u, 96f * u)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, calm.previewTopColor, calm.previewBottomColor, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 10f * u, 10f * u, fill)
        box(canvas, 60f, 10f, 112f, 96f, 10f, Color.BLACK)
        for (side in 0..1) {
            val x = 14f + side * 52f
            for (i in 0..2) box(canvas, x, 20f + i * 22f, x + 40f, 34f + i * 22f, 5f, 0x26FFFFFF)
        }
        CalmPalette.entries.take(5).forEachIndexed { i, palette ->
            fill.shader = null
            fill.color = palette.previewTopColor
            canvas.drawCircle((36f + i * 12f) * u, 108f * u, 5f * u, fill)
        }
    }

    /** The same three colours as most people see them, and as a deuteranope does. */
    private fun accessibility(canvas: Canvas) {
        val green = FloatArray(3).also { Color.colorToHSV(tokens.danger, it) }
            .also { it[0] = (it[0] + 120f) % 360f }.let { Color.HSVToColor(it) }
        val colors = listOf(tokens.danger, green, tokens.accent)
        colors.forEachIndexed { i, c ->
            fill.shader = null
            fill.color = c
            canvas.drawCircle((30f + i * 30f) * u, 36f * u, 11f * u, fill)
            fill.color = deuteranope(c)
            canvas.drawCircle((30f + i * 30f) * u, 84f * u, 11f * u, fill)
        }
        box(canvas, 20f, 59f, 100f, 61f, 1f, tokens.divider)
    }

    private fun deuteranope(c: Int): Int {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        fun ch(v: Double) = v.toInt().coerceIn(0, 255)
        return Color.rgb(
            ch(0.367 * r + 0.861 * g - 0.228 * b),
            ch(0.280 * r + 0.673 * g + 0.047 * b),
            ch(-0.012 * r + 0.043 * g + 0.969 * b),
        )
    }

    /**
     * The search pill over as many conversion results as the frame holds. Every query and answer
     * is one NexusSearchEngine returns exactly, in its own wording: it only knows full unit names
     * such as "miles", and prints the target unit as typed.
     */
    private fun searchResults(canvas: Canvas, w: Float, h: Float) {
        u = minOf(w / 120f, h / 150f)
        val side = 6f * u
        fun rowBox(top: Float, height: Float) {
            fill.shader = null
            fill.color = tokens.surface
            rect.set(side, top, w - side, top + height)
            canvas.drawRoundRect(rect, 7f * u, 7f * u, fill)
            stroke.color = tokens.divider
            stroke.strokeWidth = u * 0.6f
            canvas.drawRoundRect(rect, 7f * u, 7f * u, stroke)
        }
        fun write(s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, bold: Boolean = false) {
            text.textAlign = align
            text.color = color
            text.textSize = size * u
            text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(s, x, y, text)
        }

        var y = 6f * u
        val pillH = 22f * u
        rowBox(y, pillH)
        stroke.color = tokens.textSecondary
        stroke.strokeWidth = 1.4f * u
        canvas.drawCircle(side + 11f * u, y + pillH / 2f, 3.6f * u, stroke)
        canvas.drawLine(side + 13.6f * u, y + pillH / 2f + 2.6f * u, side + 16.5f * u, y + pillH / 2f + 5.5f * u, stroke)
        write(CONVERSIONS.first().first, side + 21f * u, y + pillH / 2f + 3f * u, 9f, tokens.textPrimary, Paint.Align.LEFT)
        y += pillH + 6f * u

        val rowH = 20f * u
        for ((query, answer) in CONVERSIONS) {
            if (y + rowH > h - 4f * u) break
            rowBox(y, rowH)

            // The answer is the point of the row, so it gets its width first and the query takes
            // what is left — otherwise a long pair ("2 gallons to liters" / "7.57 liters") overlaps.
            text.textSize = 9f * u
            text.typeface = Typeface.DEFAULT_BOLD
            val answerW = text.measureText(answer)
            val room = w - side * 2f - 16f * u - answerW - 6f * u
            text.textSize = 7.5f * u
            text.typeface = Typeface.DEFAULT
            var shown = query
            while (shown.length > 1 && text.measureText("$shown…") > room) shown = shown.dropLast(1)
            if (shown != query) shown = "$shown…"

            write(shown, side + 8f * u, y + rowH / 2f + 2.8f * u, 7.5f, tokens.textSecondary, Paint.Align.LEFT)
            write(answer, w - side - 8f * u, y + rowH / 2f + 3.2f * u, 9f, tokens.textPrimary, Paint.Align.RIGHT, bold = true)
            y += rowH + 5f * u
        }
        text.textAlign = Paint.Align.CENTER
    }

    private companion object {
        val CONVERSIONS = listOf(
            "5 km to miles" to "3.11 miles",
            "70 kg to lbs" to "154.32 lbs",
            "30 c to f" to "86 f",
            "12 inches to cm" to "30.48 cm",
            "2 gallons to liters" to "7.57 liters",
            "10 miles to km" to "16.09 km",
        )
    }
}
