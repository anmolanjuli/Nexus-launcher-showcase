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
 * The Premium features that are about something *changing*, shown changing: a badge count ticking
 * up, home pages turning, a sample word cycling fonts, a dim and tint handle sweeping a wallpaper,
 * an app hiding and coming back.
 *
 * Like [PremiumShowcaseMock], drawn in a 120-unit square from the tokens so it suits a tile and the
 * full-screen preview alike; runs on [ShowcaseTicker] while attached.
 */
internal class PremiumShowcaseMotion(
    context: Context,
    private val feature: PremiumFeature,
    private val tokens: NexusColorTokens,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private var u = 1f
    private var t = 0f
    private val ticker = ShowcaseTicker(this, startAt = openAt(feature)) { seconds -> t = seconds }

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
            PremiumFeature.NOTIFICATION_BADGES -> badges(canvas)
            PremiumFeature.PAGE_TRANSITIONS -> transitions(canvas)
            PremiumFeature.CUSTOM_FONTS -> fonts(canvas)
            PremiumFeature.WALLPAPER_EFFECTS -> wallpaper(canvas)
            else -> hidden(canvas)
        }
        canvas.restore()
    }

    /** Where each loop's first change happens, so none of them opens on a held still frame. */
    private fun openAt(feature: PremiumFeature): Float = when (feature) {
        PremiumFeature.NOTIFICATION_BADGES -> 0.75f
        PremiumFeature.PAGE_TRANSITIONS -> 0.95f
        PremiumFeature.CUSTOM_FONTS -> 1.0f
        PremiumFeature.WALLPAPER_EFFECTS -> 0.8f
        else -> 0.45f
    }

    private fun box(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, outlined: Boolean = false) {
        fill.shader = null
        fill.color = color
        rect.set(l * u, t * u, r * u, b * u)
        canvas.drawRoundRect(rect, radius * u, radius * u, fill)
        if (outlined) {
            stroke.color = tokens.divider
            stroke.strokeWidth = u
            canvas.drawRoundRect(rect, radius * u, radius * u, stroke)
        }
    }

    private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** App icons whose badge count ticks up, each new number popping in; a dot pulsing on another. */
    private fun badges(canvas: Canvas) {
        for (row in 0..1) for (col in 0..2) {
            val l = 14f + col * 34f
            val top = 26f + row * 38f
            box(canvas, l, top, l + 24f, top + 24f, 7f, tokens.surface, outlined = true)
            fill.color = ColorUtils.setAlphaComponent(tokens.textSecondary, 0x55)
            canvas.drawCircle((l + 12f) * u, (top + 12f) * u, 5f * u, fill)
        }
        val step = 0.9f
        val count = 1 + (t / step).toInt() % 9
        val sinceChange = (t / step) % 1f
        val pop = 1f + 0.35f * (1f - smooth(sinceChange * 3f))

        fill.color = ColorUtils.setAlphaComponent(tokens.danger, (150 + 105 * (0.5f + 0.5f * sin(t * 4f))).toInt())
        canvas.drawCircle(37f * u, 27f * u, 4f * u, fill)

        fill.color = tokens.danger
        canvas.drawCircle(72f * u, 27f * u, 8.5f * u * pop, fill)
        text.color = if (ColorUtils.calculateLuminance(tokens.danger) > 0.5) Color.BLACK.let { tokens.bg } else tokens.textPrimary.let {
            if (ColorUtils.calculateLuminance(it) > 0.5) it else tokens.bg
        }
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 10f * u * pop
        canvas.drawText(count.toString(), 72f * u, (27f + 3.6f * pop) * u, text)
    }

    /** Home pages turning as a cube: the page in front folds away as the next swings in. */
    private fun transitions(canvas: Canvas) {
        val cycle = 2.6f
        val index = (t / cycle).toInt()
        val turn = smooth(((t % cycle) - 1.1f) / 1.5f)
        val left = 20f
        val width = 80f
        val split = left + width * (1f - turn)
        page(canvas, left, split, farInset = 14f * turn, farOnLeft = true, pageIndex = index)
        page(canvas, split, left + width, farInset = 14f * (1f - turn), farOnLeft = false, pageIndex = index + 1)
    }

    private fun page(canvas: Canvas, l: Float, r: Float, farInset: Float, farOnLeft: Boolean, pageIndex: Int) {
        if (r - l < 0.5f) return
        val top = 18f
        val bottom = 102f
        path.reset()
        if (farOnLeft) {
            path.moveTo(l * u, (top + farInset) * u); path.lineTo(r * u, top * u)
            path.lineTo(r * u, bottom * u); path.lineTo(l * u, (bottom - farInset) * u)
        } else {
            path.moveTo(l * u, top * u); path.lineTo(r * u, (top + farInset) * u)
            path.lineTo(r * u, (bottom - farInset) * u); path.lineTo(l * u, bottom * u)
        }
        path.close()
        fill.shader = null
        fill.color = tokens.surface
        canvas.drawPath(path, fill)
        stroke.color = tokens.divider
        stroke.strokeWidth = u
        canvas.drawPath(path, stroke)

        canvas.save()
        canvas.clipPath(path)
        val hue = FloatArray(3).also { Color.colorToHSV(tokens.accent, it) }
        hue[0] = (hue[0] + pageIndex * 50f) % 360f
        fill.color = ColorUtils.setAlphaComponent(Color.HSVToColor(hue), 0xB0)
        val pageW = r - l
        for (row in 0..2) for (col in 0..1) {
            val cx = l + pageW * (0.3f + col * 0.4f)
            canvas.drawCircle(cx * u, (40f + row * 22f) * u, 5f * u * (pageW / 80f).coerceIn(0.2f, 1f), fill)
        }
        canvas.restore()
    }

    /** One sample word, cross-fading through type families, with the choice marked below. */
    private fun fonts(canvas: Canvas) {
        val faces = listOf(
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            Typeface.SERIF,
            Typeface.MONOSPACE,
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("cursive", Typeface.NORMAL),
        )
        val step = 1.3f
        val index = (t / step).toInt() % faces.size
        val fade = smooth(((t % step) - (step - 0.35f)) / 0.35f)
        text.textSize = 30f * u
        text.color = ColorUtils.setAlphaComponent(tokens.textPrimary, (255 * (1f - fade)).toInt())
        text.typeface = faces[index]
        canvas.drawText("Nexus", 60f * u, 64f * u, text)
        text.color = ColorUtils.setAlphaComponent(tokens.textPrimary, (255 * fade).toInt())
        text.typeface = faces[(index + 1) % faces.size]
        canvas.drawText("Nexus", 60f * u, 64f * u, text)

        faces.indices.forEach { i ->
            val on = i == index
            fill.color = if (on) tokens.textPrimary else ColorUtils.setAlphaComponent(tokens.textSecondary, 0x66)
            canvas.drawCircle((44f + i * 8f) * u, 88f * u, (if (on) 2.8f else 2f) * u, fill)
        }
    }

    /** A wallpaper with a handle sweeping across: left of it as it is, right of it dimmed and tinted. */
    private fun wallpaper(canvas: Canvas) {
        rect.set(10f * u, 12f * u, 110f * u, 108f * u)
        val radius = 12f * u
        fill.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            tokens.accent, ColorUtils.blendARGB(tokens.accent, tokens.bg, 0.75f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null

        val handle = 60f + 32f * sin(t * 2f * PI.toFloat() / 3.2f)
        canvas.save()
        canvas.clipRect(handle * u, 0f, 120f * u, 120f * u)
        fill.color = ColorUtils.setAlphaComponent(tokens.bg, 0x8C)
        canvas.drawRoundRect(rect, radius, radius, fill)
        val tint = FloatArray(3).also { Color.colorToHSV(tokens.accent, it) }
        tint[0] = (tint[0] + 180f) % 360f
        fill.color = ColorUtils.setAlphaComponent(Color.HSVToColor(tint), 0x3A)
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.restore()

        box(canvas, handle - 1f, 12f, handle + 1f, 108f, 1f, tokens.textPrimary)
        fill.color = tokens.textPrimary
        canvas.drawCircle(handle * u, 60f * u, 6f * u, fill)
    }

    /** A drawer grid where one app fades and shrinks away behind a hidden mark, then returns. */
    private fun hidden(canvas: Canvas) {
        val cycle = 3.2f
        val local = t % cycle
        val gone = smooth((local - 0.6f) / 0.5f) * (1f - smooth((local - 2.3f) / 0.5f))
        for (row in 0..2) for (col in 0..3) {
            val l = 12f + col * 26f
            val top = 22f + row * 26f
            val target = row == 1 && col == 2
            if (!target) {
                box(canvas, l, top, l + 18f, top + 18f, 5f, tokens.surface, outlined = true)
                continue
            }
            val shrink = 9f * gone
            box(canvas, l + shrink * 0.5f, top + shrink * 0.5f, l + 18f - shrink * 0.5f, top + 18f - shrink * 0.5f, 5f,
                ColorUtils.setAlphaComponent(tokens.surface, (255 * (1f - gone * 0.8f)).toInt()), outlined = true)
            val eye = ContextCompat.getDrawable(context, R.drawable.ic_visibility_off)?.mutate() ?: continue
            eye.setTint(ColorUtils.setAlphaComponent(tokens.textPrimary, (255 * gone).toInt()))
            val half = 7f * u
            val cx = (l + 9f) * u
            val cy = (top + 9f) * u
            eye.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            eye.draw(canvas)
        }
    }
}
