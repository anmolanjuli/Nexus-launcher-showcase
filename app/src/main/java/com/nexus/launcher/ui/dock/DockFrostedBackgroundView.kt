package com.nexus.launcher.ui.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Frosted background layer behind dock icons — strictly clipped to active dock bounds.
 * Uses GPU hardware blur (API 31+) confined inside the dock pill geometry with zero bleed.
 */
internal class DockFrostedBackgroundView(context: Context) : View(context) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = FROST_OVERLAY_ARGB
    }

    private var gradientStartArgb: Int = DockFrostedGradients.startArgb(0)
    private var gradientEndArgb: Int = DockFrostedGradients.endArgb(0)
    private var clipLeft: Float = 0f
    private var clipRight: Float = 0f

    private var isFrostedGlass: Boolean = false
    private var useGradientOverlay: Boolean = false
    private var surfaceTintArgb: Int = 0
    private var surfaceRaisedArgb: Int = 0
    private var surfaceTintAlpha: Float = FrostedGlassEngine.DEFAULT_TINT_ALPHA
    private var currentOpacity: Float = 1f
    private var glassRefraction: Float = 0.70f

    private val screenLoc = IntArray(2)
    private val clipPath = Path()
    private val clipRectF = RectF()

    private var blurRenderNode: RenderNode? = null
    private var lastRecordedW = -1
    private var lastRecordedH = -1
    private var lastScreenX = -1
    private var lastScreenY = -1
    private var lastBackdropVersion = -1L

    fun invalidateBackdrop() {
        lastRecordedW = -1
        lastRecordedH = -1
        lastBackdropVersion = -1L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
        invalidate()
    }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val node = RenderNode("DockFrostedBlurNode")
            val r = blurRadiusForRefraction(glassRefraction)
            node.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
            blurRenderNode = node
        }
    }

    /** Same range as WidgetGlassLiveBackdropView/FolderCardFrostApplier — kept identical across
     * every frosted surface so refraction reads the same everywhere. */
    private fun blurRadiusForRefraction(refraction: Float): Float =
        FrostedGlassEngine.glassBlurRadius(currentOpacity, refraction)

    private fun applyBlurRadius() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = blurRadiusForRefraction(glassRefraction)
            blurRenderNode?.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        }
    }

    fun setFrostedGlassState(
        enabled: Boolean,
        surfaceColorArgb: Int,
        surfaceRaisedColorArgb: Int,
        alpha: Float,
        hasGradient: Boolean = false,
        refraction: Float = glassRefraction
    ) {
        var changed = false
        if (isFrostedGlass != enabled) {
            isFrostedGlass = enabled
            changed = true
        }
        if (surfaceTintArgb != surfaceColorArgb) {
            surfaceTintArgb = surfaceColorArgb
            changed = true
        }
        if (surfaceRaisedArgb != surfaceRaisedColorArgb) {
            surfaceRaisedArgb = surfaceRaisedColorArgb
            changed = true
        }
        val effectiveAlpha = (alpha * 0.85f).coerceIn(0f, 0.95f)
        if (surfaceTintAlpha != effectiveAlpha) {
            surfaceTintAlpha = effectiveAlpha
            changed = true
        }
        var radiusChanged = false
        if (currentOpacity != alpha) {
            currentOpacity = alpha
            radiusChanged = true
            changed = true
        }
        if (useGradientOverlay != hasGradient) {
            useGradientOverlay = hasGradient
            changed = true
        }
        if (glassRefraction != refraction) {
            glassRefraction = refraction
            radiusChanged = true
            changed = true
        }
        if (radiusChanged) applyBlurRadius()
        if (changed) {
            invalidateBackdrop()
        }
    }

    fun setFrostedGradient(startArgb: Int, endArgb: Int) {
        if (gradientStartArgb != startArgb || gradientEndArgb != endArgb) {
            gradientStartArgb = startArgb
            gradientEndArgb = endArgb
            invalidate()
        }
    }

    private var clipTop: Float = 0f
    private var clipBottom: Float = 0f
    private var isVertical: Boolean = false

    fun setClipBounds(left: Float, right: Float) {
        setClipBounds(left, 0f, right, height.toFloat(), false)
    }

    fun setClipBounds(left: Float, top: Float, right: Float, bottom: Float, vertical: Boolean) {
        if (clipLeft != left || clipTop != top || clipRight != right || clipBottom != bottom || isVertical != vertical) {
            clipLeft = left
            clipTop = top
            clipRight = right
            clipBottom = bottom
            isVertical = vertical
            invalidate()
        }
    }

    fun invalidateBlurCache() {
        invalidateBackdrop()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val l = if (clipRight > clipLeft) clipLeft else 0f
        val r = if (clipRight > clipLeft) clipRight else width.toFloat()
        val t = if (clipBottom > clipTop) clipTop else 0f
        val b = if (clipBottom > clipTop) clipBottom else height.toFloat()
        if (r <= l || b <= t) return

        val density = resources.displayMetrics.density
        val thickness = if (isVertical) width else height
        val cornerRadius = DockCornerRadius.cornerRadiusPx(thickness, density)

        clipPath.reset()
        clipRectF.set(l, t, r, b)
        if (cornerRadius > 0f) {
            clipPath.addRoundRect(clipRectF, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        if (isFrostedGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getLocationOnScreen(screenLoc)
            val curX = screenLoc[0]
            val curY = screenLoc[1]
            val node = blurRenderNode
            val currentVersion = HomeScreenFrameCache.getBackdropVersion()

            if (node != null && (width != lastRecordedW || height != lastRecordedH ||
                    curX != lastScreenX || curY != lastScreenY ||
                    currentVersion != lastBackdropVersion || !node.hasDisplayList())) {
                lastRecordedW = width
                lastRecordedH = height
                lastScreenX = curX
                lastScreenY = curY
                lastBackdropVersion = currentVersion
                node.setPosition(0, 0, width, height)
                val nodeCanvas = node.beginRecording()
                HomeScreenFrameCache.drawWallpaperOnly(
                    nodeCanvas, context, curX, curY, width, height
                )
                node.endRecording()
            }

            canvas.save()
            if (cornerRadius > 0f) {
                canvas.clipPath(clipPath)
            } else {
                canvas.clipRect(clipRectF)
            }

            if (node != null && canvas.isHardwareAccelerated) {
                canvas.drawRenderNode(node)
            } else {
                HomeScreenFrameCache.drawWallpaperOnly(
                    canvas, context, curX, curY, width, height
                )
            }

            if (useGradientOverlay) {
                val alphaInt = (currentOpacity * 255).toInt().coerceIn(0, 255)
                val sColor = (gradientStartArgb and 0x00FFFFFF) or (alphaInt shl 24)
                val eColor = (gradientEndArgb and 0x00FFFFFF) or (alphaInt shl 24)
                val x0 = if (isVertical) 0f else l
                val y0 = if (isVertical) t else 0f
                val x1 = if (isVertical) 0f else r
                val y1 = if (isVertical) b else 0f
                gradientPaint.shader = LinearGradient(
                    x0, y0, x1, y1,
                    sColor, eColor,
                    Shader.TileMode.CLAMP
                )
                drawRoundOrRect(canvas, clipRectF, cornerRadius, gradientPaint)
            } else {
                FrostedGlassEngine.drawGlassTint(
                    canvas,
                    l,
                    t,
                    r,
                    b,
                    cornerRadius,
                    surfaceTintArgb,
                    surfaceRaisedArgb,
                    surfaceTintAlpha
                )
            }
            canvas.restore()
        } else {
            // Classic non-GPU or pre-API 31 fallback
            canvas.save()
            if (cornerRadius > 0f) {
                canvas.clipPath(clipPath)
            } else {
                canvas.clipRect(clipRectF)
            }
            val alphaInt = (currentOpacity * 255).toInt().coerceIn(0, 255)
            val sColor = (gradientStartArgb and 0x00FFFFFF) or (alphaInt shl 24)
            val eColor = (gradientEndArgb and 0x00FFFFFF) or (alphaInt shl 24)
            val x0 = if (isVertical) 0f else l
            val y0 = if (isVertical) t else 0f
            val x1 = if (isVertical) 0f else r
            val y1 = if (isVertical) b else 0f
            gradientPaint.shader = LinearGradient(
                x0, y0, x1, y1,
                sColor, eColor,
                Shader.TileMode.CLAMP
            )
            drawRoundOrRect(canvas, clipRectF, cornerRadius, gradientPaint)
            if (isFrostedGlass) {
                drawRoundOrRect(canvas, clipRectF, cornerRadius, frostPaint)
            }
            canvas.restore()
        }
    }

    private fun drawRoundOrRect(canvas: Canvas, rect: RectF, radius: Float, paint: Paint) {
        if (radius <= 0f) {
            canvas.drawRect(rect, paint)
        } else {
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
    }

    companion object {
        private const val FROST_OVERLAY_ARGB = 0x26FFFFFF
    }
}
