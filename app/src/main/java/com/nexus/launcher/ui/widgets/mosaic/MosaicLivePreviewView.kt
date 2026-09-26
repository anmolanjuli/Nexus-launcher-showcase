package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import android.view.View

/**
 * Mosaic Studio's preview: the real Mosaic, drawn again at a smaller size.
 *
 * ## Why a mirror, not a snapshot
 *
 * The preview used to be `source.draw(Canvas(bitmap))` — a *software* canvas. The Mosaic's glass
 * backdrop is a child of the Mosaic and does draw into that, but its blur is a [RenderNode]
 * [android.graphics.RenderEffect], which only runs on a hardware canvas; on software it falls back
 * to the unblurred wallpaper slice plus tint. So the preview got the opacity roughly right and
 * lost the refraction entirely, and did not look like the widget it was editing.
 *
 * This records the Mosaic into a [RenderNode] instead. That canvas is hardware-accelerated, so the
 * real backdrop draws itself with its real blur and tint, sampling the wallpaper from where the
 * Mosaic actually sits — the same pixels the home screen shows, only scaled down. Opacity,
 * refraction, shape and border are exact by construction rather than approximated here.
 *
 * Under the mirror goes the wallpaper that sits behind the real Mosaic ([drawBehind]). A
 * transparent Mosaic shows the wallpaper on the home screen; without this, the same transparency in
 * the preview showed the sheet instead, and read as a light frosted panel that was not there.
 *
 * A recording is a moment. A config change starts the Mosaic's own animations — the tiles relayout
 * and the border breathes for a second — and the preview used to record once, on the next frame,
 * which caught them part-way (tiles still fading in) and then held that frame. So after each
 * [refresh] the preview follows the Mosaic for [FOLLOW_MS], re-recording every frame, and settles
 * on what the Mosaic settles on.
 *
 * Below Android 10 there is no public [RenderNode]; the old bitmap snapshot is kept there.
 */
class MosaicLivePreviewView(context: Context, private val source: View) : View(context) {

    private val node: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) RenderNode("MosaicLivePreview") else null
    private var fallback: Bitmap? = null
    private var sourceW = 0
    private var sourceH = 0
    private val sourceLoc = IntArray(2)
    private val clipPath = android.graphics.Path()
    private val clipRect = android.graphics.RectF()

    private var followUntil = 0L
    private val follow = object : Runnable {
        override fun run() {
            record()
            if (isAttachedToWindow && android.os.SystemClock.uptimeMillis() < followUntil) postOnAnimation(this)
        }
    }

    /** Re-records the Mosaic, and keeps doing so while it animates. Call on every change. */
    fun refresh() {
        record()
        followUntil = android.os.SystemClock.uptimeMillis() + FOLLOW_MS
        removeCallbacks(follow)
        postOnAnimation(follow)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(follow)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) node?.discardDisplayList()
    }

    private fun record() {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return
        sourceW = w
        sourceH = h
        source.getLocationOnScreen(sourceLoc)
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            node.setPosition(0, 0, w, h)
            val recording = node.beginRecording(w, h)
            try {
                source.draw(recording)
            } finally {
                node.endRecording()
            }
        } else {
            val bmp = fallback?.takeIf { it.width == w && it.height == h }
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { fallback = it }
            bmp.eraseColor(android.graphics.Color.TRANSPARENT)
            source.draw(Canvas(bmp))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (sourceW <= 0 || sourceH <= 0 || width <= 0 || height <= 0) return
        // Fit-centre, as the ImageView it replaces did.
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        val scale = minOf(availW / sourceW, availH / sourceH)
        val dx = paddingLeft + (availW - sourceW * scale) / 2f
        val dy = paddingTop + (availH - sourceH * scale) / 2f
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        drawBehind(canvas, scale)
        if (node != null && canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            canvas.drawRenderNode(node)
        } else {
            fallback?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }
        canvas.restore()
    }

    /** The unblurred wallpaper slice at the Mosaic's own screen position, in its coordinates. */
    private fun drawBehind(canvas: Canvas, scale: Float) {
        // 14dp on screen, expressed in the Mosaic's own (unscaled) coordinates.
        val corner = 14f * resources.displayMetrics.density / scale.coerceAtLeast(0.01f)
        clipRect.set(0f, 0f, sourceW.toFloat(), sourceH.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, corner, corner, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        com.nexus.launcher.ui.folder.HomeScreenFrameCache.drawWallpaperOnly(
            canvas, context, sourceLoc[0], sourceLoc[1], sourceW, sourceH,
        )
        canvas.restore()
    }

    private companion object {
        /** Covers the relayout and the one-second border breath (LivingMosaicBreathPulse). */
        const val FOLLOW_MS = 1400L
    }
}
