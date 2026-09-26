package com.nexus.launcher.ui.widgets.mosaic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.nexus.launcher.ui.widgets.NexusWidgetView
import com.nexus.launcher.ui.widgets.WidgetHostContext
import kotlin.math.roundToInt

/** Hosts mosaic children in clipped tile cells; widgets always fill the cell via options. */
class LivingMosaicChildHost(
    private val parent: FrameLayout,
    private val density: Float
) {

    private data class Tile(
        val cell: FrameLayout,
        val widget: View,
        val appWidgetId: Int
    )

    private val tiles = mutableListOf<Tile>()
    private var layoutAnimator: ValueAnimator? = null
    private val interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private var currentRects: List<LivingMosaicLayouts.NormRect> = emptyList()
    private var lastContentW = 0
    private var lastContentH = 0
    private var lastInset = 0
    private var activeChildren: List<MosaicChild> = emptyList()

    var onPlaceholderTapped: ((Int) -> Unit)? = null

    fun tileIndexAt(x: Float, y: Float): Int? =
        tiles.indexOfFirst { tile ->
            tile.cell.visibility == View.VISIBLE &&
                x >= tile.cell.left && x <= tile.cell.right &&
                y >= tile.cell.top && y <= tile.cell.bottom
        }.takeIf { it >= 0 }

    fun tileBounds(index: Int): Rect? = tiles.getOrNull(index)?.cell?.let {
        Rect(it.left, it.top, it.right, it.bottom)
    }

    fun detachTile(index: Int): MosaicFocusedTile? {
        val tile = tiles.getOrNull(index) ?: return null
        if (tile.appWidgetId == -1) return null
        parent.removeView(tile.cell)
        return MosaicFocusedTile(index, tile.cell, tile.widget)
    }

    fun attachFocusedTile(tile: MosaicFocusedTile) {
        (tile.cell.parent as? android.view.ViewGroup)?.removeView(tile.cell)
        parent.addView(tile.cell, tile.index.coerceIn(0, parent.childCount))
        if (lastContentW > 0 && lastContentH > 0) {
            applyRects(currentRects, lastContentW, lastContentH, lastInset, immediate = true)
            // Focus-size options may still be queued after an app return. Reassert the
            // Mosaic tile bounds on the next layout pass so the last provider update wins.
            parent.post {
                applyRects(currentRects, lastContentW, lastContentH, lastInset, immediate = true)
            }
        }
    }

    fun showFocusedTile(tile: MosaicFocusedTile, target: FrameLayout, width: Int, height: Int) {
        (tile.cell.parent as? android.view.ViewGroup)?.removeView(tile.cell)
        target.addView(tile.cell, FrameLayout.LayoutParams(width, height))
        tile.cell.layoutParams = FrameLayout.LayoutParams(width, height)
        tile.cell.alpha = 1f
        tile.cell.scaleX = 1f
        tile.cell.scaleY = 1f
        LivingMosaicMiniature.apply(
            view = tile.widget,
            density = density,
            tileLeft = 0,
            tileTop = 0,
            tileW = width,
            tileH = height,
            opacity = 1f,
            extraScale = 1f,
            immediate = true
        )
    }

    fun sync(
        children: List<MosaicChild>,
        mode: String,
        templateId: String?,
        focusIndex: Int,
        contentW: Int,
        contentH: Int,
        inset: Int,
        host: AppWidgetHost,
        manager: AppWidgetManager,
        animate: Boolean
    ) {
        lastContentW = contentW
        lastContentH = contentH
        lastInset = inset
        activeChildren = children
        val target = if (mode == MosaicConfig.MODE_SINGLE) {
            LivingMosaicLayouts.singleFocus(children.size, focusIndex)
        } else if (templateId != null && LivingMosaicLayouts.template(templateId) != null) {
            LivingMosaicLayouts.template(templateId)!!
        } else {
            LivingMosaicLayouts.packed(children.size)
        }

        val paddedChildren = if (templateId != null && target.size > children.size) {
            children + List(target.size - children.size) { MosaicChild(-1, "", "") }
        } else children

        ensureTiles(paddedChildren, host, manager)
        val focusMode = mode == MosaicConfig.MODE_SINGLE
        if (!animate || currentRects.isEmpty() || currentRects.size != target.size || currentRects == target) {
            applyRects(target, contentW, contentH, inset, immediate = true)
        } else {
            animateRects(
                currentRects, target, contentW, contentH, inset,
                durationMs = if (focusMode) 260L else 1000L,
                onEnd = null
            )
        }
        currentRects = target
        tiles.forEachIndexed { i, tile ->
            val rel = if (focusMode) i - focusIndex else 0
            tile.cell.isClickable = false
            tile.widget.isClickable = !focusMode || rel == 0
            tile.widget.importantForAccessibility =
                if (focusMode && rel != 0) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
        }
    }

    /** Re-applies every child cell's background against the CURRENT global toggle state without
     *  a full relayout — see [LivingMosaicView.dispatchDraw]'s defensive re-check doc. */
    fun refreshBackgrounds() {
        if (lastContentW > 0 && lastContentH > 0 && currentRects.isNotEmpty()) {
            applyRects(currentRects, lastContentW, lastContentH, lastInset, immediate = true)
        }
    }

    fun clear() {
        layoutAnimator?.cancel()
        tiles.forEach { parent.removeView(it.cell) }
        tiles.clear()
        currentRects = emptyList()
    }

    private fun ensureTiles(
        children: List<MosaicChild>,
        host: AppWidgetHost,
        manager: AppWidgetManager
    ) {
        val desiredIds = children.map { it.appWidgetId }
        val existingIds = tiles.map { it.appWidgetId }
        if (desiredIds == existingIds) return

        // A real widget id that WAS a mosaic child and no longer is (removed from the mosaic, or
        // the mosaic itself deleted) must revert to its own independent background — otherwise it
        // would keep rendering with a suppressed plate forever the next time it's shown, even
        // standalone. This is the one chokepoint every add/remove path funnels through (ensureTiles
        // only runs at all when membership actually changed), so it covers all of them uniformly.
        existingIds.filter { it != -1 && it !in desiredIds }
            .forEach { setMosaicEmbedded(parent.context, it, false) }

        tiles.forEach { parent.removeView(it.cell) }
        tiles.clear()

        val ctx = WidgetHostContext.themed(parent.context)
        for ((i, child) in children.withIndex()) {
            val widget: View = if (child.appWidgetId == -1) {
                android.widget.TextView(ctx).apply {
                    text = "+\nAdd"
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE)
                    background = android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.parseColor("#1AFFFFFF")
                    )
                    setOnClickListener { onPlaceholderTapped?.invoke(i) }
                }
            } else {
                val info = manager.getAppWidgetInfo(child.appWidgetId) ?: continue
                val hostView = host.createView(ctx, child.appWidgetId, info)
                hostView.setAppWidget(child.appWidgetId, info)
                if (hostView is NexusWidgetView) {
                    hostView.suppressHostGestures = true
                    hostView.onLongPressDetected = null
                } else {
                    hostView.isLongClickable = false
                }
                // Every mosaic child inherits the tile's own single background instead of each
                // carrying an independent Glass/Neumorphic choice — suppress its own plate.
                setMosaicEmbedded(parent.context, child.appWidgetId, true)
                hostView
            }

            val cell = FrameLayout(parent.context).apply {
                clipChildren = true
                clipToPadding = true
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) {
                        val r = 12f * density
                        outline.setRoundRect(0, 0, v.width, v.height, r)
                    }
                }
            }
            cell.addView(widget, FrameLayout.LayoutParams(0, 0))
            parent.addView(cell, FrameLayout.LayoutParams(0, 0))
            tiles += Tile(cell, widget, child.appWidgetId)
        }
    }

    /** Sets/clears [com.nexus.launcher.ui.widgets.NexusWidgetConfig.InstanceConfig.isMosaicEmbedded]
     *  for a real widget instance and forces it to rebake immediately (same broadcast every other
     *  widget-settings change uses) so the effect is visible without waiting for an unrelated
     *  data tick. No-ops if already in the desired state. */
    private fun setMosaicEmbedded(context: android.content.Context, appWidgetId: Int, embedded: Boolean) {
        val current = com.nexus.launcher.ui.widgets.NexusWidgetConfig.read(context, appWidgetId)
        if (current.isMosaicEmbedded == embedded) return
        com.nexus.launcher.ui.widgets.NexusWidgetConfig.write(context, current.copy(isMosaicEmbedded = embedded))
        val intent = android.content.Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED").apply {
            putExtra("appWidgetId", appWidgetId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private fun applyRects(
        rects: List<LivingMosaicLayouts.NormRect>,
        contentW: Int,
        contentH: Int,
        inset: Int,
        immediate: Boolean
    ) {
        val availW = (contentW - inset * 2).coerceAtLeast(1)
        val availH = (contentH - inset * 2).coerceAtLeast(1)
        tiles.forEachIndexed { i, tile ->
            val r = rects.getOrNull(i)
            if (r == null) {
                tile.cell.visibility = View.GONE
                return@forEachIndexed
            }
            if (tile.cell.visibility != View.VISIBLE) {
                tile.cell.visibility = View.VISIBLE
            }

            val tileW = (r.width * availW).roundToInt().coerceAtLeast(1)
            val tileH = (r.height * availH).roundToInt().coerceAtLeast(1)
            val tileLeft = inset + (r.left * availW).roundToInt()
            val tileTop = inset + (r.top * availH).roundToInt()

            val cellLp = tile.cell.layoutParams as FrameLayout.LayoutParams
            cellLp.width = tileW
            cellLp.height = tileH
            cellLp.leftMargin = tileLeft
            cellLp.topMargin = tileTop
            tile.cell.layoutParams = cellLp
            tile.cell.alpha = r.opacity
            tile.cell.scaleX = 1f
            tile.cell.scaleY = 1f
            applyChildBackground(tile.cell, activeChildren.getOrNull(i))

            LivingMosaicMiniature.apply(
                view = tile.widget,
                density = density,
                tileLeft = 0,
                tileTop = 0,
                tileW = tileW,
                tileH = tileH,
                opacity = 1f,
                extraScale = 1f,
                immediate = immediate
            )
        }
    }

    private fun applyChildBackground(cell: FrameLayout, child: MosaicChild?) {
        LivingMosaicChildBackgroundApplier.apply(parent, cell, child, density)
    }

    private fun animateRects(
        from: List<LivingMosaicLayouts.NormRect>,
        to: List<LivingMosaicLayouts.NormRect>,
        contentW: Int,
        contentH: Int,
        inset: Int,
        durationMs: Long,
        onEnd: (() -> Unit)?
    ) {
        layoutAnimator?.cancel()
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = this@LivingMosaicChildHost.interpolator
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val lerped = from.indices.map { i ->
                    val aR = from[i]
                    val bR = to[i]
                    LivingMosaicLayouts.NormRect(
                        left = aR.left + (bR.left - aR.left) * t,
                        top = aR.top + (bR.top - aR.top) * t,
                        width = aR.width + (bR.width - aR.width) * t,
                        height = aR.height + (bR.height - aR.height) * t,
                        opacity = aR.opacity + (bR.opacity - aR.opacity) * t,
                        scale = 1f
                    )
                }
                applyRects(lerped, contentW, contentH, inset, immediate = false)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
        }
        layoutAnimator = anim
        anim.start()
    }

    fun invalidateBackdropsForScroll() {
        for (tile in tiles) {
            tile.cell.findViewWithTag<LivingMosaicGlassBackdropView>(
                LivingMosaicChildBackgroundApplier.GLASS_BACKDROP_TAG
            )?.invalidate()
        }
    }
}

