package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Neumorphism/Default backdrop for a Glass-mode-disabled Mosaic tile or child cell.
 *
 * Mosaic previously had NO integration with [NexusNeumorphicDraw] at all — with the master toggle
 * off, it just tore down its live blur backdrop and left a plain semi-transparent
 * [android.graphics.drawable.GradientDrawable] fill (`LivingMosaicGlass.build`'s non-glass
 * branch), which reads as "see-through" without a blur layer behind it to give it substance. Every
 * other surface (widgets, boxes, folders, dock) falls back to a genuine opaque Neumorphic/Default
 * plate via [NexusNeumorphicDraw.drawRaisedSurface]/`drawFlatSurface` instead — this gives Mosaic
 * the same fallback, as a dedicated backdrop child view (matching the existing
 * [LivingMosaicGlassBackdropView] pattern) since those draw calls involve several layered
 * shadow/highlight passes that don't reduce to a single `Drawable`.
 */
class LivingMosaicNeumorphicBackdropView(context: Context) : View(context) {

    private var flat = false
    private var palette: NexusNeumorphicDraw.SoftPalette? = null
    private var cornerRadiusPx = 0f
    private var shapeStyle = 1
    private val bounds = RectF()

    init {
        setWillNotDraw(false)
    }

    /** The palette when this backdrop is raised (Neumorphic), for [MosaicRaisedShadow]; else null. */
    fun raisedPalette(): NexusNeumorphicDraw.SoftPalette? = if (flat) null else palette

    fun configure(flat: Boolean, palette: NexusNeumorphicDraw.SoftPalette, cornerRadiusPx: Float, shapeStyle: Int = 1) {
        this.flat = flat
        this.palette = palette
        this.cornerRadiusPx = cornerRadiusPx
        this.shapeStyle = shapeStyle
        invalidate()
        // The shadow is drawn by the parent of the tile or cell, so that parent redraws too.
        (parent as? View)?.let { (it.parent as? View)?.invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        if (width <= 0 || height <= 0) return
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val dp = resources.displayMetrics.density
        if (flat) {
            NexusNeumorphicDraw.drawFlatSurface(canvas, bounds, cornerRadiusPx, shapeStyle, p, dp)
        } else {
            // Face only, in the tile's or cell's own outline shape: the clip would cut any shadow
            // off here, so MosaicRaisedShadow draws it from outside (see there).
            val outlineRadius = (parent as? View)?.let { MosaicRaisedShadow.outlineRadius(it) }
            if (outlineRadius == null) {
                // Not a round-rect outline (MosaicRaisedShadow logs it): the old, clipped surface.
                NexusNeumorphicDraw.drawRaisedSurface(canvas, bounds, cornerRadiusPx, shapeStyle, p, dp)
                return
            }
            NexusNeumorphicDraw.drawRaisedRoundRectFace(canvas, bounds, outlineRadius, p, dp)
        }
    }
}
