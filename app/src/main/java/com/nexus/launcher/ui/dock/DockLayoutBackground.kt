package com.nexus.launcher.ui.dock

import android.graphics.Canvas
import android.graphics.RectF
import android.widget.FrameLayout
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/** Frosted gradient layer, corner radius, solid fill, and soft UI for [DockLayout]. */
internal class DockLayoutBackground(private val dock: DockLayout) {

    val frostedView = DockFrostedBackgroundView(dock.context)

    private var lastClipLeft = Float.NaN
    private var lastClipRight = Float.NaN
    private val tempNeumorphicRect = RectF()

    fun install() {
        dock.addView(
            frostedView,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun attach() {
        DockBackgroundRenderer.attachFrostedLayer(dock, frostedView)
        lastClipLeft = Float.NaN
        lastClipRight = Float.NaN
    }

    fun detach() {
        DockBackgroundRenderer.detachFrostedLayer()
        lastClipLeft = Float.NaN
        lastClipRight = Float.NaN
    }

    fun onSizeChanged() {
        val isVertical = DockAxis.isVertical(dock)
        val mainStart = DockLayoutRenderer.currentAnimatedBgLeft()
        val mainLen = DockLayoutRenderer.currentAnimatedBgWidth()
        val mainEnd = if (mainLen > 0f) mainStart + mainLen else DockAxis.mainSize(dock.width, dock.height, isVertical).toFloat()
        val crossSize = DockAxis.crossSize(dock.width, dock.height, isVertical).toFloat()
        val pillRect = DockAxis.rectF(mainStart, mainEnd, 0f, crossSize, isVertical)
        val radius = DockCornerRadius.cornerRadiusPx(crossSize.toInt(), dock.resources.displayMetrics.density)
        lastClipLeft = mainStart
        lastClipRight = mainEnd
        applyCornerRadiusOutline(pillRect, radius)
        DockBackgroundRenderer.syncBackgroundLayer(dock, pillRect, isVertical, requestDockInvalidate = false)
    }

    /**
     * True when the renderer's pill bounds moved after this frame's [draw] applied the clip —
     * the icon pass sets the bounds, and it runs after the background pass.
     */
    fun isClipStale(): Boolean {
        if (isSuppressed) return false
        val mainStart = DockLayoutRenderer.currentAnimatedBgLeft()
        val mainLen = DockLayoutRenderer.currentAnimatedBgWidth()
        val isVertical = DockAxis.isVertical(dock)
        val mainEnd = if (mainLen > 0f) mainStart + mainLen else DockAxis.mainSize(dock.width, dock.height, isVertical).toFloat()
        return mainStart != lastClipLeft || mainEnd != lastClipRight
    }

    fun applyCornerRadiusOutline(left: Float = 0f, right: Float = Float.NaN) {
        val isVertical = DockAxis.isVertical(dock)
        val mainMax = if (isVertical) dock.height.toFloat() else dock.width.toFloat()
        val effectiveRight = if (right.isNaN()) mainMax else right
        val crossSize = DockAxis.crossSize(dock.width, dock.height, isVertical).toFloat()
        val pillRect = DockAxis.rectF(left, effectiveRight, 0f, crossSize, isVertical)
        val radius = DockCornerRadius.cornerRadiusPx(crossSize.toInt(), dock.resources.displayMetrics.density)
        applyCornerRadiusOutline(pillRect, radius)
    }

    fun applyCornerRadiusOutline(pillRect: RectF, radius: Float) {
        DockCornerRadius.applyClipOutline(frostedView, pillRect, radius)
        DockBackgroundRenderer.applyDockClipOutline(dock, pillRect, radius)
    }

    var isSuppressed: Boolean = false
        set(value) {
            field = value
            frostedView.visibility = if (value) android.view.View.GONE else android.view.View.VISIBLE
            dock.invalidate()
        }

    fun draw(canvas: Canvas, density: Float) {
        if (isSuppressed) return
        val isVertical = DockAxis.isVertical(dock)
        val mainStart = DockLayoutRenderer.currentAnimatedBgLeft()
        val mainLen = DockLayoutRenderer.currentAnimatedBgWidth()
        val mainEnd = if (mainLen > 0f) mainStart + mainLen else DockAxis.mainSize(dock.width, dock.height, isVertical).toFloat()
        val crossSize = DockAxis.crossSize(dock.width, dock.height, isVertical).toFloat()
        val pillRect = DockAxis.rectF(mainStart, mainEnd, 0f, crossSize, isVertical)
        val radius = DockCornerRadius.cornerRadiusPx(crossSize.toInt(), density)

        // Only update clip/frosted when the animated bg bounds change — never
        // invalidate the dock from inside onDraw (that caused continuous flicker).
        if (mainStart != lastClipLeft || mainEnd != lastClipRight) {
            lastClipLeft = mainStart
            lastClipRight = mainEnd
            applyCornerRadiusOutline(pillRect, radius)
            DockBackgroundRenderer.syncBackgroundLayer(
                dock, pillRect, isVertical, requestDockInvalidate = false
            )
        }

        if (DockBackgroundRenderer.shouldDrawNeumorphicOnCanvas()) {
            val palette = NexusNeumorphicDraw.resolvePalette(dock.currentThemeTokens)
            tempNeumorphicRect.set(pillRect)
            if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled) {
                NexusNeumorphicDraw.drawFlatSurface(
                    canvas = canvas,
                    bounds = tempNeumorphicRect,
                    radius = radius,
                    shapeStyle = 1,
                    palette = palette,
                    dp = density
                )
            } else {
                NexusNeumorphicDraw.drawRaisedSurface(
                    canvas = canvas,
                    bounds = tempNeumorphicRect,
                    radius = radius,
                    shapeStyle = 1,
                    palette = palette,
                    dp = density
                )
            }
        }

        if (DockBackgroundRenderer.shouldDrawSolidOnCanvas()) {
            DockCornerRadius.drawSolidRect(
                canvas,
                pillRect,
                radius,
                DockBackgroundRenderer.resolvedSolidColor(dock.context, dock)
            )
        }

        val isGlassActive = (DockBackgroundRenderer.backgroundMode == com.nexus.launcher.ui.dock.settings.DockBackgroundMode.FROSTED ||
            DockBackgroundRenderer.backgroundMode == com.nexus.launcher.ui.dock.settings.DockBackgroundMode.TRANSPARENT) &&
            DockBackgroundRenderer.frostedGlassEnabled
        if (isGlassActive) {
            val borderColor = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveGlassBorderColor(dock.currentThemeTokens)
            com.nexus.launcher.ui.glass.FrostedGlassEngine.drawGlassBorder(
                canvas = canvas,
                left = pillRect.left,
                top = pillRect.top,
                right = pillRect.right,
                bottom = pillRect.bottom,
                radiusPx = radius,
                density = density,
                borderColorArgb = borderColor
            )
        }
    }
}
