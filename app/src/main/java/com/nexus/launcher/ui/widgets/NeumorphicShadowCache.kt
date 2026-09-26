package com.nexus.launcher.ui.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LongSparseArray

/**
 * The soft shadows under a Neumorphic raised surface, drawn once into a bitmap and reused.
 *
 * Each raised surface is two or three `BlurMaskFilter` path draws (ambient, key, highlight). A
 * blurred path cannot be kept ready by the renderer: every time a display list containing one is
 * replayed, the render thread re-rasterises the blur and uploads it. A static widget's list is
 * replayed on *every frame* while the page under it moves, so a Neumorphic home page paid for all
 * its shadows 120 times a second — measured 2026-09-24 (FrameMetrics): command issue 12–16 ms and
 * GPU 10–14 ms per frame, totals of 30–55 ms, against 2–5 ms and 3–10 ms in Frosted Glass, whose
 * glass is cached in RenderNodes. Here the blur runs once per distinct shape; after that a
 * surface's shadow is one bitmap draw, which the GPU keeps as a texture.
 *
 * Shadows are soft by nature, so they are rendered at [SCALE] and drawn stretched: a quarter of
 * the memory with no visible difference (the blur radius scales with the canvas, so it matches).
 *
 * Keyed by the shape and palette, not by position, so every surface of one size — every App Box
 * slot disc, say — shares one bitmap. Main-thread use is expected; access is synchronized anyway
 * since a renderer may be asked to draw off-thread for a thumbnail.
 */
internal object NeumorphicShadowCache {

    private const val SCALE = 0.5f
    private const val PAD_DP = 16f
    private const val MAX_ENTRIES = 48

    private val entries = LongSparseArray<Bitmap>()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    /**
     * Draws the shadow of [basePath] (in [bounds] coordinates) through the cache.
     * [shapeKey] identifies the path's shape beyond its size (corner radius, shape style), and
     * [drawShadows] draws the shadow layers of a path onto a canvas — the one real definition of
     * what the shadow looks like, so the cache cannot drift from it. Pass a function reference
     * made once, not a capturing lambda: this runs every frame. [alpha] fades the shadow with a
     * surface that is fading (the shadow is drawn outside that surface's own view).
     */
    @Synchronized
    fun draw(
        canvas: Canvas,
        bounds: RectF,
        basePath: Path,
        shapeKey: Long,
        palette: NexusNeumorphicDraw.SoftPalette,
        dp: Float,
        drawShadows: (Canvas, Path, NexusNeumorphicDraw.SoftPalette, Float) -> Unit,
        alpha: Int = 255,
    ) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0f || h <= 0f) return
        val pad = PAD_DP * dp
        val key = keyOf(w, h, shapeKey, palette, dp)
        var bitmap = entries.get(key)
        if (bitmap == null) {
            bitmap = render(bounds, basePath, pad, palette, dp, drawShadows) ?: return
            // Drop, never recycle: a display list may still reference an evicted bitmap.
            if (entries.size() >= MAX_ENTRIES) entries.clear()
            entries.put(key, bitmap)
        }
        dst.set(bounds.left - pad, bounds.top - pad, bounds.right + pad, bounds.bottom + pad)
        bitmapPaint.alpha = alpha
        canvas.drawBitmap(bitmap, null, dst, bitmapPaint)
    }

    private fun render(
        bounds: RectF,
        basePath: Path,
        pad: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        dp: Float,
        drawShadows: (Canvas, Path, NexusNeumorphicDraw.SoftPalette, Float) -> Unit,
    ): Bitmap? {
        val bw = ((bounds.width() + 2 * pad) * SCALE).toInt().coerceAtLeast(1)
        val bh = ((bounds.height() + 2 * pad) * SCALE).toInt().coerceAtLeast(1)
        val bitmap = runCatching { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bitmap)
        c.scale(SCALE, SCALE)
        c.translate(pad - bounds.left, pad - bounds.top)
        drawShadows(c, basePath, palette, dp)
        return bitmap
    }

    private fun keyOf(w: Float, h: Float, shapeKey: Long, p: NexusNeumorphicDraw.SoftPalette, dp: Float): Long {
        var k = 1125899906842597L
        k = 31 * k + w.toRawBits()
        k = 31 * k + h.toRawBits()
        k = 31 * k + shapeKey
        k = 31 * k + p.shadow
        k = 31 * k + p.highlight
        k = 31 * k + (if (p.isLight) 1 else 0) + (if (p.isAmoled) 2 else 0)
        k = 31 * k + dp.toRawBits()
        return k
    }
}
