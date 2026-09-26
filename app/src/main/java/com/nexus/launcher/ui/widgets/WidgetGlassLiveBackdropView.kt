package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import com.nexus.launcher.ui.folder.HomeScreenFrameCache

/**
 * Live blurred-wallpaper backdrop for a Glass-mode widget — a plain sibling View kept
 * size/position-synced with its [android.appwidget.AppWidgetHostView] (see
 * [AppWidgetOverlayBinder] / [WidgetOverlayLayoutParams]).
 *
 * A widget bakes its own content into a *static* RemoteViews bitmap (an `AppWidgetProvider`
 * can't host a live `View`), so it cannot itself track the wallpaper as it slides across the
 * screen during a page swipe — even though the wallpaper is static (no parallax in this app),
 * the widget's own screen position changes continuously during a swipe, and different screen
 * positions sit over different wallpaper pixels. A live sibling View, redrawn on scroll, is the
 * only way to keep that in sync.
 *
 * This reuses the exact `RenderNode` + `RenderEffect.createBlurEffect` technique already proven
 * out (and known smooth) in [com.nexus.launcher.ui.dock.DockFrostedBackgroundView] — NOT the
 * Compose/AGSL pipeline from earlier attempts, which is what caused the stutter/flash/allocation
 * problems this session. A `RenderNode` re-records only when position, size, or the wallpaper
 * itself actually changed (same caching fields as the Dock), so a stationary frame costs only a
 * cheap `drawRenderNode` call, and a moving one costs one Canvas draw + GPU blur composite — no
 * Compose recomposition, no GraphicsLayer capture, no shader recompilation.
 */
class WidgetGlassLiveBackdropView(context: Context) : View(context) {

    var appWidgetId: Int = -1

    private val screenLoc = IntArray(2)
    private var lastRecordedW = -1
    private var lastRecordedH = -1
    private var lastScreenX = -1
    private var lastScreenY = -1
    private var lastBackdropVersion = -1L
    private var blurRadiusPx = 60f

    private var blurRenderNode: RenderNode? = null

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode = RenderNode("WidgetGlassBlurNode").apply {
                setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
            }
        }
    }

    /** Refraction (0..1): lower = softer/frostier blur, higher = sharper — more "bg bleed". */
    fun setRefraction(refraction: Float, opacity: Float = 1f) {
        val radius = com.nexus.launcher.ui.glass.FrostedGlassEngine.glassBlurRadius(opacity, refraction)
        if (radius != blurRadiusPx) {
            blurRadiusPx = radius
            blurRenderNode?.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            invalidateBackdrop()
        }
    }

    /**
     * @param shapeStyle Same convention as [NexusWidgetHostChrome.apply]'s outline: 0/11 = full
     * circle (radius from the view's own live width/height, not [cornerRadiusPx]), 2 = square
     * (no rounding), anything else = [cornerRadiusPx]. This MUST compute the identical shape the
     * host view's own outline clip uses — this backdrop sits directly behind the host, so any
     * mismatch (e.g. the host clipping to a circle/squircle while this clips to a plainer
     * rounded-rect) shows up as a visibly different-shaped halo peeking out from behind the
     * widget's own silhouette (previously an actual bug here: this used a separate, uncoordinated
     * `visualR / 0.4f` adjustment for shapeStyle 1 that didn't match the host's own radius at
     * all, producing exactly that "pill wrapping a squircle" mismatch).
     */
    fun applyShape(shapeStyle: Int, cornerRadiusPx: Float) {
        // Defensively zero out any native ambient/spot shadow — this sits directly behind the
        // widget's host view, so a non-zero elevation here would cast a soft directional shadow
        // onto the canvas around the widget's silhouette, outside its own clipped bounds.
        elevation = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineAmbientShadowColor = android.graphics.Color.TRANSPARENT
            outlineSpotShadowColor = android.graphics.Color.TRANSPARENT
        }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                // Inset by this view's own padding (set to match the host's "Padding" toggle —
                // see AppWidgetOverlayBinder) so the clip matches the widget's own visually
                // padded plate exactly. Previously this always clipped to the FULL (unpadded)
                // bounds while the host's own content was inset by View.setPadding, leaving an
                // untinted ring of raw, unclipped blur in the padding gap — reported as a stray
                // "glass shadow" around the widget specifically when Padding was on.
                val left = v.paddingLeft
                val top = v.paddingTop
                val right = v.width - v.paddingRight
                val bottom = v.height - v.paddingBottom
                if (right <= left || bottom <= top) return
                NexusWidgetShapeGeometry.outline(outline, shapeStyle, left, top, right, bottom, cornerRadiusPx)
            }
        }
        invalidateOutline()
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
    }
}
