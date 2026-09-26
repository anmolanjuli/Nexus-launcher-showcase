package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.theme.NexusColorTokens
import kotlin.math.PI
import kotlin.math.sin

/**
 * The three features that used device screenshots, drawn instead — so every picture on the Premium
 * page is the same kind of thing: E-Ink Paper Mode, the document Library, and gestures on an icon.
 *
 * All three carry their real text rather than grey bars: a page you can read, book titles, the
 * gesture rows with the action each one runs. E-Ink uses the paper and ink the reader really
 * produces, so its half is that exact colour; everything else comes from the theme tokens, like
 * [PremiumShowcaseMotion]. Same 120-unit square, same [ShowcaseTicker].
 */
internal class PremiumShowcaseFeatureArt(
    context: Context,
    private val feature: PremiumFeature,
    private val tokens: NexusColorTokens,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private var u = 1f
    private var t = 0f
    private val ticker = ShowcaseTicker(
        this,
        startAt = if (feature == PremiumFeature.ADVANCED_GESTURES) 0.6f else 0.9f,
    ) { seconds -> t = seconds }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawColor(tokens.surfaceRaised)
        u = minOf(w, h) / 120f
        canvas.save()
        canvas.translate((w - 120f * u) / 2f, (h - 120f * u) / 2f)
        when (feature) {
            PremiumFeature.EINK_FEED -> eInk(canvas)
            PremiumFeature.DOCUMENT_LIBRARY -> library(canvas)
            else -> gestures(canvas)
        }
        canvas.restore()
    }

    // ---------------------------------------------------------------- drawing helpers

    private fun box(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        rect.set(l * u, t * u, r * u, b * u)
        canvas.drawRoundRect(rect, radius * u, radius * u, fill)
    }

    private fun outline(canvas: Canvas, radius: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = u
        canvas.drawRoundRect(rect, radius * u, radius * u, stroke)
    }

    private fun label(canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        text.textAlign = Paint.Align.LEFT
        text.color = color
        text.textSize = size * u
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawText(s, x * u, y * u, text)
    }

    /** Draws [s] wrapped inside [maxW] units, returning the baseline after the last line drawn. */
    private fun wrapped(
        canvas: Canvas, s: String, x: Float, firstBaseline: Float, maxW: Float,
        size: Float, color: Int, bold: Boolean, step: Float, maxLines: Int,
    ): Float {
        text.textAlign = Paint.Align.LEFT
        text.color = color
        text.textSize = size * u
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        var line = StringBuilder()
        var baseline = firstBaseline
        var drawn = 0
        for (word in s.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (text.measureText(candidate) <= maxW * u) {
                line = StringBuilder(candidate)
                continue
            }
            canvas.drawText(line.toString(), x * u, baseline * u, text)
            drawn++
            baseline += step
            line = StringBuilder(word)
            if (drawn >= maxLines) return baseline
        }
        if (line.isNotEmpty() && drawn < maxLines) {
            canvas.drawText(line.toString(), x * u, baseline * u, text)
            baseline += step
        }
        return baseline
    }

    /** [s] cut to [maxW] units, with an ellipsis when it does not fit. */
    private fun fitted(s: String, maxW: Float, size: Float, bold: Boolean = false): String {
        text.textSize = size * u
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (text.measureText(s) <= maxW * u) return s
        var cut = s
        while (cut.length > 1 && text.measureText("$cut…") > maxW * u) cut = cut.dropLast(1)
        return "$cut…"
    }

    private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    // ---------------------------------------------------------------- E-Ink

    /**
     * One page, both ways: the normal display on one side of a sweeping divider and E-Ink paper on
     * the other, so the difference is the picture rather than something to take on trust.
     */
    private fun eInk(canvas: Canvas) {
        val split = 60f + 34f * sin(t * 2f * PI.toFloat() / 5.2f)
        canvas.save()
        clip.reset()
        rect.set(6f * u, 4f * u, 114f * u, 116f * u)
        clip.addRoundRect(rect, 6f * u, 6f * u, Path.Direction.CW)
        canvas.clipPath(clip)

        canvas.save()
        canvas.clipRect(6f * u, 4f * u, split * u, 116f * u)
        page(canvas, paper = false)
        canvas.restore()

        canvas.save()
        canvas.clipRect(split * u, 4f * u, 114f * u, 116f * u)
        page(canvas, paper = true)
        canvas.restore()
        canvas.restore()

        // The divider, and its handle.
        box(canvas, split - 0.6f, 4f, split + 0.6f, 116f, 0.6f, tokens.textPrimary)
        fill.color = tokens.textPrimary
        canvas.drawCircle(split * u, 60f * u, 5f * u, fill)
        fill.color = tokens.bg
        canvas.drawCircle(split * u, 60f * u, 1.6f * u, fill)
    }

    private fun page(canvas: Canvas, paper: Boolean) {
        val surface = if (paper) PAPER_SURFACE else tokens.surface
        val ink = if (paper) PAPER_INK else tokens.textPrimary
        val soft = if (paper) ColorUtils.setAlphaComponent(PAPER_INK, 0xB0) else tokens.textSecondary
        box(canvas, 6f, 4f, 114f, 116f, 6f, surface)

        wrapped(canvas, context.getString(R.string.premium_art_headline), 14f, 20f, 92f, 8f, ink, bold = true, step = 10f, maxLines = 2)

        rect.set(14f * u, 32f * u, 106f * u, 56f * u)
        fill.shader = if (paper) {
            LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF8E8E8E.toInt(), 0xFFDAD5CB.toInt(), Shader.TileMode.CLAMP)
        } else {
            val hsv = FloatArray(3).also { Color.colorToHSV(tokens.accent, it) }
            LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                Color.HSVToColor(floatArrayOf(hsv[0], 0.72f, 0.92f)),
                Color.HSVToColor(floatArrayOf((hsv[0] + 60f) % 360f, 0.68f, 0.78f)),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, 3f * u, 3f * u, fill)
        fill.shader = null
        if (paper) {
            // E-Ink renders photographs as grey, with the panel's grain showing through.
            fill.color = ColorUtils.setAlphaComponent(PAPER_INK, 0x2E)
            for (row in 0 until 4) for (col in 0 until 14) {
                canvas.drawCircle((18f + col * 6.6f) * u, (37f + row * 5.6f) * u, 0.8f * u, fill)
            }
        }

        wrapped(canvas, context.getString(R.string.premium_art_body), 14f, 66f, 92f, 5f, soft, bold = false, step = 7.5f, maxLines = 6)

        val name = context.getString(if (paper) R.string.premium_art_label_eink else R.string.premium_art_label_original)
        val chipW = 4f + name.length * 2.7f
        val chipL = if (paper) 106f - chipW else 14f
        box(canvas, chipL, 104f, chipL + chipW, 113f, 4.5f, ColorUtils.setAlphaComponent(ink, 0x1F))
        label(canvas, name, chipL + 3f, 110.5f, 5f, ink, bold = true)
    }

    // ---------------------------------------------------------------- Library

    /** The Library as it really looks: a search field, Continue Reading, then the shelf. */
    private fun library(canvas: Canvas) {
        label(canvas, context.getString(R.string.premium_art_library_title), 10f, 14f, 9f, tokens.textPrimary, bold = true)

        box(canvas, 10f, 18f, 110f, 30f, 6f, tokens.surface)
        outline(canvas, 6f, tokens.divider)
        stroke.color = tokens.textSecondary
        stroke.strokeWidth = 1.1f * u
        canvas.drawCircle(18f * u, 24f * u, 2.8f * u, stroke)
        canvas.drawLine(20f * u, 26f * u, 22f * u, 28f * u, stroke)
        label(canvas, context.getString(R.string.premium_art_library_search), 26f, 26f, 5f, tokens.textSecondary)

        label(canvas, context.getString(R.string.premium_art_library_continue), 10f, 38f, 4f, tokens.textSecondary, bold = true)

        val hsv = FloatArray(3).also { Color.colorToHSV(tokens.accent, it) }
        val value = if (ColorUtils.calculateLuminance(tokens.bg) > 0.5) 0.82f else 0.6f
        fun cover(i: Int) = Color.HSVToColor(floatArrayOf((hsv[0] + i * 47f) % 360f, 0.42f, value))

        // Continue reading: three covers, each with how far in you are.
        for (i in 0 until 3) {
            val l = 10f + i * 34f
            val lift = 1.4f * sin((t + i * 0.8f) * 1.3f)
            box(canvas, l, 42f + lift, l + 22f, 68f + lift, 2.5f, cover(i))
            box(canvas, l, 42f + lift, l + 3f, 68f + lift, 2.5f, ColorUtils.blendARGB(cover(i), Color.BLACK, 0.3f))
            label(canvas, fitted(TITLES[i], 30f, 4.2f), l, 74f, 4.2f, tokens.textPrimary)
            box(canvas, l, 77f, l + 22f, 79f, 1f, ColorUtils.setAlphaComponent(tokens.textSecondary, 0x44))
            val progress = PROGRESS[i] * smooth(((t + i * 0.4f) % 5.5f) / 1.6f)
            box(canvas, l, 77f, l + 22f * progress, 79f, 1f, tokens.accent)
        }

        // The shelf below: the cards the Library lists documents in.
        for (i in 0 until 2) {
            val l = 10f + i * 52f
            box(canvas, l, 86f, l + 48f, 114f, 5f, tokens.surface)
            outline(canvas, 5f, tokens.divider)
            box(canvas, l + 4f, 90f, l + 18f, 110f, 2f, cover(i + 3))
            label(canvas, fitted(TITLES[i + 3], 26f, 4.4f, bold = true), l + 22f, 97f, 4.4f, tokens.textPrimary, bold = true)
            label(canvas, PAGES[i], l + 22f, 104f, 4f, tokens.textSecondary)
            box(canvas, l + 22f, 106f, l + 44f, 108f, 1f, ColorUtils.setAlphaComponent(tokens.textSecondary, 0x44))
            box(canvas, l + 22f, 106f, l + 22f + 22f * PROGRESS[i + 3], 108f, 1f, tokens.accent)
        }
    }

    // ---------------------------------------------------------------- Gestures

    /** Each gesture an icon can carry, with the action it runs, taking its turn. */
    private fun gestures(canvas: Canvas) {
        val rows = listOf(
            Triple(R.drawable.ic_swipe_up, R.string.premium_art_gesture_up, R.string.premium_art_action_search),
            Triple(R.drawable.ic_swipe_down, R.string.premium_art_gesture_down, R.string.premium_art_action_notifications),
            Triple(R.drawable.ic_double_tap, R.string.premium_art_gesture_double, R.string.premium_art_action_app_info),
        )
        val step = 1.6f
        val active = ((t / step).toInt() % rows.size)
        val within = (t % step) / step

        // The icon the gestures belong to, answering whichever row is live.
        val lift = when (active) {
            0 -> -3f * smooth(within * 2f)
            1 -> 3f * smooth(within * 2f)
            else -> 0f
        }
        val pulse = if (active == 2) 1f + 0.12f * sin(within * 4f * PI.toFloat()) else 1f
        val iconW = 22f * pulse
        box(canvas, 60f - iconW, 8f + lift, 60f + iconW, 8f + iconW * 2f + lift, 7f, tokens.surface)
        outline(canvas, 7f, tokens.divider)
        fill.color = ColorUtils.setAlphaComponent(tokens.textSecondary, 0x66)
        canvas.drawCircle(60f * u, (8f + iconW + lift) * u, 7f * u, fill)

        rows.forEachIndexed { i, (glyphRes, labelRes, actionRes) ->
            val top = 58f + i * 20f
            val on = i == active
            box(canvas, 8f, top, 112f, top + 17f, 5f, if (on) tokens.surfaceRaised else tokens.surface)
            outline(canvas, 5f, if (on) tokens.accent else tokens.divider)

            ContextCompat.getDrawable(context, glyphRes)?.mutate()?.let { glyph ->
                glyph.setTint(if (on) tokens.textPrimary else tokens.textSecondary)
                val cx = 17f * u
                val cy = (top + 8.5f) * u
                val half = 5f * u
                glyph.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
                glyph.draw(canvas)
            }
            label(canvas, context.getString(labelRes), 26f, top + 10.5f, 5f, tokens.textPrimary, bold = on)

            val action = context.getString(actionRes)
            text.textSize = 4.8f * u
            text.typeface = Typeface.DEFAULT
            val w = text.measureText(action) / u
            val alpha = if (on) (155 + 100 * smooth(within * 4f)).toInt() else 150
            label(canvas, action, 106f - w, top + 10.5f, 4.8f, ColorUtils.setAlphaComponent(tokens.accent, alpha))
        }
    }

    private companion object {
        /** The paper and ink Paper Tint really makes, so the page is that colour exactly. */
        val PAPER_SURFACE = Color.parseColor("#F9F1E6")
        val PAPER_INK = Color.parseColor("#3A342B")

        /** Sample shelf: public-domain titles, as in a real library. */
        val TITLES = listOf("The Acorn Planter", "Leonardo Notebook", "Sherlock Holmes", "Moby Dick", "Walden")
        val PAGES = listOf("p. 8 / 64", "p. 112 / 262")
        val PROGRESS = listOf(0.7f, 0.35f, 0.9f, 0.12f, 0.43f)
    }
}
