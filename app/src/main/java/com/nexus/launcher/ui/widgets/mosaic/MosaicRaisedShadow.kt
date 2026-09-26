package com.nexus.launcher.ui.widgets.mosaic

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * The Neumorphic raised shadow of a Living Mosaic tile and its cells.
 *
 * A tile and each cell clip to their outline, so a shadow drawn inside them — as every other
 * raised surface does — was cut off entirely, and in Neumorphic the Mosaic read as flat panels
 * beside raised widgets (logged 2026-09-24). The backdrop now draws only the face, and:
 *
 * - **The tile** draws its own shadow ([drawOwn], from `LivingMosaicView.dispatchDraw`). The widget
 *   layer does not clip its children, so the tile can draw past its edges once it stops clipping
 *   to its outline; [applyTileClip] moves that clip to a fixed frame around the content. Drawn by
 *   the tile itself, the shadow follows drags (translation) and the edit lift (scale), which only
 *   update the tile's render node and never redraw its parent.
 * - **A cell** has its shadow drawn by the content frame, all cells' shadows first ([drawBehind], from
 *   [MosaicContentFrame]). Cells are placed by layout, which redraws that frame; the one transform
 *   it misses is the small scale of a drop preview, where the shadow stays put.
 *
 * The shape is read from the view's own outline, the one thing that defines what is visible, so
 * the shadow cannot disagree with the clip. Main thread only (shared scratch objects).
 */
internal object MosaicRaisedShadow {

    private val outline = Outline()
    private val rect = Rect()
    private val bounds = RectF()
    private var warnedShape = false

    /**
     * Raised tiles stop clipping to their outline (so their shadow can show) and [contentClip],
     * which holds the tile's content, clips instead. Returns whether the tile should draw its
     * own shadow.
     */
    fun applyTileClip(tile: ViewGroup, contentClip: View, raised: Boolean): Boolean {
        if (contentClip.outlineProvider !is TileOutline) contentClip.outlineProvider = TileOutline(tile)
        tile.clipToOutline = !raised
        contentClip.clipToOutline = raised
        contentClip.invalidateOutline()
        tile.invalidate()
        return raised
    }

    /** The tile's shadow, in its own coordinates, before its children. */
    fun drawOwn(canvas: Canvas, tile: ViewGroup) {
        val backdrop = tile.getChildAt(0) as? LivingMosaicNeumorphicBackdropView ?: return
        val palette = backdrop.raisedPalette() ?: return
        val radius = outlineRadius(tile) ?: return
        bounds.set(rect)
        NexusNeumorphicDraw.drawRaisedRoundRectShadow(canvas, bounds, radius, palette, tile.resources.displayMetrics.density, 255)
    }

    /** A cell's shadow, drawn by its parent before any cell is; nothing unless it is raised. */
    fun drawBehind(canvas: Canvas, child: View) {
        if (child.visibility != View.VISIBLE || child.alpha <= 0f) return
        val backdrop = (child as? ViewGroup)?.getChildAt(0) as? LivingMosaicNeumorphicBackdropView ?: return
        val palette = backdrop.raisedPalette() ?: return
        val radius = outlineRadius(child) ?: return
        bounds.set(rect)
        canvas.save()
        canvas.translate(child.left.toFloat(), child.top.toFloat())
        if (!child.matrix.isIdentity) canvas.concat(child.matrix)
        val dp = child.resources.displayMetrics.density
        NexusNeumorphicDraw.drawRaisedRoundRectShadow(canvas, bounds, radius, palette, dp, (child.alpha * 255f).toInt())
        canvas.restore()
    }

    /** The corner radius of [view]'s round-rect outline, leaving its rect in [rect]. */
    fun outlineRadius(view: View): Float? {
        val provider = view.outlineProvider ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        outline.setEmpty()
        provider.getOutline(view, outline)
        if (!outline.getRect(rect)) {
            // Every Mosaic outline is a round rect today. A new shape needs its own shadow path.
            if (!warnedShape) Log.w("MosaicRaisedShadow", "Outline of ${view.javaClass.simpleName} is not a round rect; no shadow")
            warnedShape = true
            return null
        }
        return outline.radius
    }

    /** The tile's own outline, for the content frame that fills it — one shape, not a copy. */
    private class TileOutline(private val tile: View) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            tile.outlineProvider?.getOutline(tile, outline)
        }
    }
}
