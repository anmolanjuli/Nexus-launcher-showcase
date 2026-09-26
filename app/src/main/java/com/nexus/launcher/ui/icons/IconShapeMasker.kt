package com.nexus.launcher.ui.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build

/**
 * Applies a custom global icon mask by baking layers to a software bitmap.
 *
 * Why not clip AdaptiveIconDrawable.draw()? That API already applies the OEM
 * system mask — every custom shape then looks identical (usually squircle/square).
 * We draw bg+fg layers unmasked, then SRC_IN with [IconShapePaths].
 */
class IconShapeMasker(
    base: Drawable,
    private val shapeType: Int
) : DrawableWrapper(base.constantState?.newDrawable()?.mutate() ?: base.mutate()) {

    private var cached: Bitmap? = null
    private var cacheW = -1
    private var cacheH = -1

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateCache()
    }

    override fun invalidateSelf() {
        invalidateCache()
        super.invalidateSelf()
    }

    private fun invalidateCache() {
        cached?.recycle()
        cached = null
        cacheW = -1
        cacheH = -1
    }

    override fun draw(canvas: Canvas) {
        if (shapeType == -1) {
            drawable?.draw(canvas)
            return
        }
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val bmp = obtainMasked(b.width(), b.height()) ?: run {
            drawable?.draw(canvas)
            return
        }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), bitmapPaint)
    }

    private fun obtainMasked(w: Int, h: Int): Bitmap? {
        if (cached != null && cacheW == w && cacheH == h) return cached
        val baked = bake(w, h) ?: return null
        cached?.recycle()
        cached = baked
        cacheW = w
        cacheH = h
        return baked
    }

    private fun bake(w: Int, h: Int): Bitmap? {
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcCanvas = Canvas(src)
        drawUnmaskedSource(srcCanvas, w, h)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)
        val path = IconShapePaths.build(shapeType, RectF(0f, 0f, w.toFloat(), h.toFloat()))
        maskPaint.xfermode = null
        outCanvas.drawPath(path, maskPaint)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        outCanvas.drawBitmap(src, 0f, 0f, maskPaint)
        maskPaint.xfermode = null
        src.recycle()
        return out
    }

    /**
     * Render icon content WITHOUT the OEM AdaptiveIconDrawable mask.
     * Adaptive layers use the extra-inset viewport so the safe zone fills [0,w]x[0,h].
     */
    private fun drawUnmaskedSource(canvas: Canvas, w: Int, h: Int) {
        val d = drawable ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
            val extra = AdaptiveIconDrawable.getExtraInsetFraction()
            val inset = (w * extra).toInt().coerceAtLeast(0)
            val layerBounds = Rect(-inset, -inset, w + inset, h + inset)
            d.background?.let {
                it.bounds = layerBounds
                it.draw(canvas)
            }
            d.foreground?.let {
                it.bounds = layerBounds
                it.draw(canvas)
            }
        } else {
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
        }
    }
}
