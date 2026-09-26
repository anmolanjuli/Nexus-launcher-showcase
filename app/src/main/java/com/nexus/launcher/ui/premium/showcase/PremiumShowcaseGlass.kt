package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.WallpaperSheetFrost
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.settings.PendingDrawerSettings
import com.nexus.launcher.ui.settings.SettingsPreviewIconRenderer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Frosted Glass, shown the way it looks on a phone: a settings sheet of frosted glass drifting
 * over a vivid wallpaper with a clock and apps on it.
 *
 * The frost is real rather than painted: the wallpaper is rendered once, blurred once
 * ([WallpaperSheetFrost.fallbackBlur], the launcher's own software blur), and the sheet draws the
 * blurred copy of exactly the area it covers. As the sheet moves, the blur underneath moves with
 * it, so the shapes stay legible through the glass the way they do on the home screen. The
 * wallpaper's colours come from the accent, so the picture follows the theme.
 */
internal class PremiumShowcaseGlass(
    context: Context,
    private val tokens: NexusColorTokens,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val rect = RectF()
    private val dest = Rect()

    /** Whichever of the theme's text and background is lighter: the glass's sheen and edge. */
    private val light = if (ColorUtils.calculateLuminance(tokens.textPrimary) > ColorUtils.calculateLuminance(tokens.bg)) tokens.textPrimary else tokens.bg

    /** Arctic: the first frosted preset, the one a new widget and the dock start on. */
    private val FROSTED_PRESET = 0

    private var scene: Bitmap? = null
    private var frosted: Bitmap? = null
    private var drift = 0f
    private val ticker = ShowcaseTicker(this, startAt = 3.15f) { seconds -> drift = sin(seconds / 4.2f * 2f * PI.toFloat()) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        build(w, h)
    }

    /** Renders the wallpaper and its blurred copy at [w] by [h]. */
    private fun build(w: Int, h: Int) {
        release()
        if (w <= 0 || h <= 0) return
        val wallpaper = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawWallpaper(Canvas(wallpaper), w.toFloat(), h.toFloat())
        scene = wallpaper
        // fallbackBlur recycles what it is given, so it gets a copy.
        frosted = WallpaperSheetFrost.fallbackBlur(wallpaper.copy(Bitmap.Config.ARGB_8888, false), 14)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The bitmaps are freed on detach; a view attached again at the same size needs them back.
        if (scene == null) build(width, height)
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.stop()
        release()
        super.onDetachedFromWindow()
    }

    private fun release() {
        scene?.recycle()
        frosted?.recycle()
        scene = null
        frosted = null
    }

    /**
     * The launcher's own frosted gradient over the theme's background — the same family the dock
     * and the widgets frost with, adapted for a light theme the same way they adapt it — rather
     * than invented colours, so this reads as the theme the user is running.
     */
    private fun drawWallpaper(canvas: Canvas, w: Float, h: Float) {
        val isLight = ColorUtils.calculateLuminance(tokens.bg) > 0.5
        canvas.drawColor(tokens.bg)
        paint.shader = LinearGradient(
            0f, 0f, w, h,
            DockFrostedGradients.startArgb(FROSTED_PRESET, isLight),
            DockFrostedGradients.endArgb(FROSTED_PRESET, isLight),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        // One soft highlight, so the glass has something with shape to blur.
        paint.shader = RadialGradient(
            w * 0.24f, h * 0.2f, maxOf(w, h) * 0.55f,
            intArrayOf(ColorUtils.setAlphaComponent(light, 0x33), ColorUtils.setAlphaComponent(light, 0)),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val unit = minOf(w, h * 0.8f) / 360f
        text.textAlign = Paint.Align.CENTER
        text.color = light
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        text.textSize = 64f * unit
        canvas.drawText("9:41", w / 2f, h * 0.16f + 32f * unit, text)

        val shape = PendingDrawerSettings().iconShape
        val icon = 46f * unit
        val gap = (w - icon * 4f) / 5f
        for (i in 0 until 8) {
            val col = i % 4
            val row = i / 4
            val x = gap + col * (icon + gap)
            val y = h * 0.62f + row * (icon + 22f * unit)
            val bitmap = SettingsPreviewIconRenderer.getBitmap(context, i, shape, isLight, tokens)
            dest.set(x.toInt(), y.toInt(), (x + icon).toInt(), (y + icon).toInt())
            canvas.drawBitmap(bitmap, null, dest, bitmapPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val wallpaper = scene ?: return
        val blur = frosted ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawBitmap(wallpaper, 0f, 0f, bitmapPaint)

        val sheetW = w * 0.82f
        val sheetH = minOf(h * 0.62f, sheetW * 0.95f)
        val left = (w - sheetW) / 2f
        val top = h * 0.4f - sheetH / 2f + drift * h * 0.05f
        rect.set(left, top, left + sheetW, top + sheetH)
        val radius = sheetW * 0.08f

        canvas.save()
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawBitmap(blur, 0f, 0f, bitmapPaint)
        paint.shader = null
        paint.color = ColorUtils.setAlphaComponent(tokens.surface, 0x70)
        canvas.drawRect(rect, paint)
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.top + sheetH * 0.45f,
            ColorUtils.setAlphaComponent(light, 0x33), ColorUtils.setAlphaComponent(light, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        paint.shader = null
        drawSheetContent(canvas, sheetW)
        canvas.restore()

        stroke.color = ColorUtils.setAlphaComponent(light, 0x55)
        stroke.strokeWidth = maxOf(1f, sheetW / 300f)
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    /** A handle, the sheet's title, and three settings rows with their switches. */
    private fun drawSheetContent(canvas: Canvas, sheetW: Float) {
        val u = sheetW / 300f
        val left = rect.left + 18f * u
        val right = rect.right - 18f * u
        var y = rect.top + 12f * u

        paint.color = ColorUtils.setAlphaComponent(tokens.textSecondary, 0x99)
        canvas.drawRoundRect(rect.centerX() - 18f * u, y, rect.centerX() + 18f * u, y + 4f * u, 2f * u, 2f * u, paint)
        y += 26f * u

        text.textAlign = Paint.Align.LEFT
        text.color = tokens.textPrimary
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 17f * u
        canvas.drawText(context.getString(R.string.premium_feature_frosted), left, y, text)
        y += 16f * u

        for (i in 0 until 3) {
            if (y + 40f * u > rect.bottom - 8f * u) break
            val cy = y + 20f * u
            paint.color = ColorUtils.setAlphaComponent(light, 0x2E)
            canvas.drawCircle(left + 13f * u, cy, 13f * u, paint)
            paint.color = tokens.textPrimary
            canvas.drawRoundRect(left + 36f * u, cy - 7f * u, left + 36f * u + (110f - i * 18f) * u, cy - 2f * u, 2.5f * u, 2.5f * u, paint)
            paint.color = tokens.textSecondary
            canvas.drawRoundRect(left + 36f * u, cy + 3f * u, left + 36f * u + (80f + i * 12f) * u, cy + 7f * u, 2f * u, 2f * u, paint)

            val on = i != 1
            paint.color = if (on) tokens.accent else ColorUtils.setAlphaComponent(tokens.textSecondary, 0x55)
            canvas.drawRoundRect(right - 36f * u, cy - 10f * u, right, cy + 10f * u, 10f * u, 10f * u, paint)
            paint.color = light
            canvas.drawCircle(if (on) right - 10f * u else right - 26f * u, cy, 7.5f * u, paint)
            y += 44f * u
        }
    }
}
