package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Live blurred-wallpaper backdrop for a Glass-mode Mosaic tile.
 *
 * Unlike widgets/boxes/folders, the Mosaic tile's own surface (`LivingMosaicGlass.build`) never
 * implemented any wallpaper blur at all — it only ever drew a flat tinted/stroked `Drawable`,
 * so "Glass" mode on a Mosaic tile was Glass in name only. This view is added as the FIRST child
 * of [LivingMosaicView] (a `FrameLayout` with `clipToOutline = true`, so it's automatically
 * clipped to the tile's rounded shape without needing its own outline provider) and draws the
 * same RenderNode GPU blur + translucent tint used everywhere else, matching the canonical
 * 90-60×refraction radius and [FrostedGlassEngine.frostFillAlpha] tint formula. Reuses the exact
 * technique proven out in [com.nexus.launcher.ui.widgets.WidgetGlassLiveBackdropView].
 */
class LivingMosaicGlassBackdropView(context: Context) : View(context) {

    private val screenLoc = IntArray(2)
    private var lastRecordedW = -1
    private var lastRecordedH = -1
    private var lastScreenX = -1
    private var lastScreenY = -1
    private var lastBackdropVersion = -1L
    private var blurRadiusPx = 60f

    private var tintAlpha = 0
    private var tintColor = 0
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var blurRenderNode: RenderNode? = null

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode = RenderNode("MosaicGlassBlurNode").apply {
                setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
            }
        }
    }

    /** @param refraction 0..1 — lower is softer/frostier, higher is sharper/more bg bleed. */
    fun setRefraction(refraction: Float, opacity: Float = 1f) {
        val radius = com.nexus.launcher.ui.glass.FrostedGlassEngine.glassBlurRadius(opacity, refraction)
        if (radius != blurRadiusPx) {
            blurRadiusPx = radius
            blurRenderNode?.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            invalidateBackdrop()
        }
    }

    fun setTint(opacity: Float, refraction: Float, baseColor: Int) {
        tintColor = baseColor
        tintAlpha = (FrostedGlassEngine.frostFillAlpha(opacity, refraction) * 255f).toInt().coerceIn(0, 255)
        invalidate()
    }

    fun invalidateBackdrop() {
        lastRecordedW = -1
        lastRecordedH = -1
        lastBackdropVersion = -1L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val node = blurRenderNode
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getLocationOnScreen(screenLoc)
            val curX = screenLoc[0]
            val curY = screenLoc[1]
            val currentVersion = HomeScreenFrameCache.getBackdropVersion()

            if (width != lastRecordedW || height != lastRecordedH ||
                curX != lastScreenX || curY != lastScreenY ||
                currentVersion != lastBackdropVersion || !node.hasDisplayList()
            ) {
                lastRecordedW = width
                lastRecordedH = height
                lastScreenX = curX
                lastScreenY = curY
                lastBackdropVersion = currentVersion
                node.setPosition(0, 0, width, height)
                val nodeCanvas = node.beginRecording()
                HomeScreenFrameCache.drawWallpaperOnly(nodeCanvas, context, curX, curY, width, height)
                node.endRecording()
            }

            if (canvas.isHardwareAccelerated) {
                canvas.drawRenderNode(node)
            } else {
                HomeScreenFrameCache.drawWallpaperOnly(canvas, context, curX, curY, width, height)
            }
        } else {
            getLocationOnScreen(screenLoc)
            HomeScreenFrameCache.drawWallpaperOnly(canvas, context, screenLoc[0], screenLoc[1], width, height)
        }

        if (tintAlpha > 0) {
            tintPaint.color = Color.argb(tintAlpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
    }
}
