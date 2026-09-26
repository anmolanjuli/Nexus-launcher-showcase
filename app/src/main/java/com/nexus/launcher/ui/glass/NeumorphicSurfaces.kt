package com.nexus.launcher.ui.glass

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.view.doOnAttach
import androidx.core.widget.NestedScrollView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Neumorphism for the launcher's own UI — sheets, Settings, buttons — using the exact surfaces the
 * widgets, dock and folders already draw ([NexusNeumorphicDraw]), so the style means one thing
 * everywhere instead of stopping at the home screen.
 *
 * Two surfaces, for two jobs:
 * - [card]: a **raised** panel. Containers — preview panels, section cards, buttons.
 * - [field]: a **sunken** well. Text inputs; in neumorphism an input is pressed *into* the surface,
 *   which is what makes it read as "type here" rather than as one more card.
 *
 * Outside Neumorphism both return the flat card these call sites drew before — surface fill,
 * divider stroke — so Default and Frosted Glass are unchanged by switching a call site over.
 *
 * ## Shadow room
 *
 * A raised surface is mostly shadow drawn *outside* its bounds. [card] lets it spill by switching
 * off clipping on the containers between the view and its scroll container ([releaseClipping]) —
 * and deliberately stops *below* the scroll view. Unclipping a scroll view would let content that
 * has scrolled out of it draw over whatever sits above and below: a sheet's header, its buttons.
 * The shadow's room therefore has to exist inside the scroll content, as padding; [makeShadowRoom]
 * provides it for sheets whose padding sat outside the scroll.
 */
object NeumorphicSurfaces {

    /** Neither Frosted Glass nor the flat Default style. */
    val isActive: Boolean
        get() = !FrostedGlassEngine.isGlobalFrostedGlassEnabled && !FrostedGlassEngine.isDefaultFlatStyleEnabled

    /**
     * Horizontal room a raised surface's shadow needs inside its scroll content. The key shadow
     * reaches further than this at the right edge, but its outermost part is faint enough to lose.
     */
    const val SHADOW_ROOM_DP = 12f

    /** A raised panel background for [view]; the flat card outside Neumorphism. */
    fun card(view: View, tokens: NexusColorTokens, cornerPx: Float): Drawable {
        val density = view.resources.displayMetrics.density
        if (!isActive) return flat(tokens, cornerPx, density)
        view.doOnAttach { releaseClipping(it) }
        return NeumorphicDrawable(NexusNeumorphicDraw.resolvePalette(tokens), cornerPx, raised = true, density)
    }

    /** A sunken input well for [view]; the flat field outside Neumorphism. Draws inside its bounds. */
    fun field(view: View, tokens: NexusColorTokens, cornerPx: Float): Drawable {
        val density = view.resources.displayMetrics.density
        if (!isActive) return flat(tokens, cornerPx, density)
        return NeumorphicDrawable(NexusNeumorphicDraw.resolvePalette(tokens), cornerPx, raised = false, density)
    }

    /**
     * The selected segment in a sunken track: raised, with a shadow tight enough to stay inside the
     * segment's own bounds, the way the switch knob's does. A [card]'s shadow reaches several dp
     * past its edge, and in a track padded by 3dp it spilled out over whatever sat below.
     */
    fun thumb(view: View, tokens: NexusColorTokens, cornerPx: Float): Drawable {
        val density = view.resources.displayMetrics.density
        if (!isActive) return flat(tokens, cornerPx, density)
        return ThumbDrawable(NexusNeumorphicDraw.resolvePalette(tokens), cornerPx, density)
    }

    /**
     * [card] in Neumorphism, [fallback] otherwise — for call sites whose look outside Neumorphism
     * is their own (a frosted pill, a translucent chip), not the flat card.
     */
    inline fun raisedOr(view: View, tokens: NexusColorTokens, cornerPx: Float, fallback: () -> Drawable?): Drawable? =
        if (isActive) card(view, tokens, cornerPx) else fallback()

    /** [field] in Neumorphism, [fallback] otherwise. Also the "pressed / selected" state of a control. */
    inline fun sunkenOr(view: View, tokens: NexusColorTokens, cornerPx: Float, fallback: () -> Drawable?): Drawable? =
        if (isActive) field(view, tokens, cornerPx) else fallback()

    /**
     * Lets [view]'s shadow draw past its bounds, up to — never including — the scroll container.
     * Also stops at the window content root; nothing above it needs to change.
     *
     * Only the view's *ancestors* change. The view keeps clipping its own children: a card
     * holding a horizontal scroller depends on that to hide items scrolled out of it, and an
     * earlier version unclipped the card too — the icon sheet's shape and icon-pack rows then ran
     * past the card's edge.
     *
     * Every scrolling container is a stop, not only [ScrollView]: a [HorizontalScrollView], a
     * RecyclerView or a list is not a ScrollView subclass, and unclipping one lets items scrolled
     * out of it draw over the screen around it.
     *
     * The same goes for a scroller *beside* the path. A parent's `clipChildren` is what keeps each
     * child inside its own bounds, so unclipping a parent for one child's shadow also freed every
     * sibling — and a RecyclerView with `clipToPadding = false` does not clip itself. The app
     * picker's raised Save button did exactly that: its list, a sibling, scrolled up over the
     * search field. Such siblings are clipped to their own bounds by outline instead ([keepClipped]).
     */
    fun releaseClipping(view: View) {
        var parent = view.parent as? ViewGroup
        while (parent != null) {
            if (isScrollContainer(parent)) return
            if (parent.id == android.R.id.content) return
            parent.clipChildren = false
            parent.clipToPadding = false
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChildAt(i)
                if (sibling is ViewGroup && isScrollContainer(sibling)) keepClipped(sibling)
            }
            parent = parent.parent as? ViewGroup
        }
    }

    /** Clips [scroller] to its own bounds whatever its parent does; tracks its size by itself. */
    private fun keepClipped(scroller: View) {
        if (scroller.clipToOutline) return // already clipped, to its bounds or a shape of its own
        scroller.outlineProvider = android.view.ViewOutlineProvider.BOUNDS
        scroller.clipToOutline = true
    }

    /**
     * For sheets that put their side padding on a container *around* the scroll view rather than
     * inside it. Moves [SHADOW_ROOM_DP] of that padding inside the scroll's content and widens the
     * path to the scroll by the same amount, so the cards sit exactly where they did but their
     * shadows land inside the scroll's own bounds instead of being cut off at its edge.
     *
     * Call once the scroll is in the hierarchy. The widening is set, not added, so a repeat call
     * on the same container is harmless; use [clearShadowRoom] when that container is reused for
     * content that does not want it.
     */
    fun makeShadowRoom(scroll: ViewGroup) {
        val room = (SHADOW_ROOM_DP * scroll.resources.displayMetrics.density).toInt()
        val content = scroll.getChildAt(0) ?: return
        content.setPaddingRelative(
            content.paddingStart + room, content.paddingTop,
            content.paddingEnd + room, content.paddingBottom,
        )
        val (padded, child) = paddedAncestor(scroll, room) ?: return
        padded.clipToPadding = false
        (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = -room
            it.marginEnd = -room
            child.layoutParams = it
        }
    }

    /** Undoes [makeShadowRoom]'s widening on [child] — for a container reused by other content. */
    fun clearShadowRoom(child: View) {
        (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            if (it.marginStart != 0 || it.marginEnd != 0) {
                it.marginStart = 0
                it.marginEnd = 0
                child.layoutParams = it
            }
        }
    }

    private fun isScrollContainer(v: ViewGroup): Boolean =
        v is ScrollView || v is NestedScrollView || v is HorizontalScrollView ||
            v is androidx.recyclerview.widget.RecyclerView || v is android.widget.AbsListView

    /** The nearest ancestor with side padding to lend, and its child on the path to [from]. */
    private fun paddedAncestor(from: View, room: Int): Pair<ViewGroup, View>? {
        var child: View = from
        var parent = from.parent as? ViewGroup
        while (parent != null) {
            if (parent.paddingStart >= room && parent.paddingEnd >= room) return parent to child
            child = parent
            parent = parent.parent as? ViewGroup
        }
        return null
    }

    private fun flat(tokens: NexusColorTokens, cornerPx: Float, density: Float) = GradientDrawable().apply {
        setColor(tokens.surface)
        cornerRadius = cornerPx
        setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
    }

    private class ThumbDrawable(
        private val palette: NexusNeumorphicDraw.SoftPalette,
        private val cornerPx: Float,
        private val density: Float,
    ) : Drawable() {

        private val rect = RectF()
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(2.4f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        private val rim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }

        override fun draw(canvas: Canvas) {
            val inset = 2.5f * density
            rect.set(bounds)
            rect.inset(inset, inset)
            if (rect.isEmpty) return
            val r = (cornerPx - inset).coerceAtLeast(0f)
            // Shadow falls a little down, as the key light in NexusNeumorphicDraw comes from above.
            shadow.color = palette.shadow
            shadow.alpha = if (palette.isLight) 55 else 140
            canvas.save()
            canvas.translate(0f, 1f * density)
            canvas.drawRoundRect(rect, r, r, shadow)
            canvas.restore()
            fill.color = palette.surfaceLight
            canvas.drawRoundRect(rect, r, r, fill)
            if (palette.isLight) {
                rim.strokeWidth = 1f * density
                rim.color = android.graphics.Color.argb(170, 255, 255, 255)
                canvas.drawRoundRect(rect, r, r, rim)
            }
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class NeumorphicDrawable(
        private val palette: NexusNeumorphicDraw.SoftPalette,
        private val cornerPx: Float,
        private val raised: Boolean,
        private val density: Float,
    ) : Drawable() {

        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            rect.set(bounds)
            if (rect.isEmpty) return
            if (raised) {
                // Shape 1 is a rounded rectangle whose corner is 0.4 of the radius passed in.
                NexusNeumorphicDraw.drawRaisedSurface(canvas, rect, cornerPx / 0.4f, 1, palette, density)
            } else {
                NexusNeumorphicDraw.drawDebossedWell(canvas, rect, cornerPx, palette, density)
            }
        }

        override fun getOutline(outline: android.graphics.Outline) {
            outline.setRoundRect(bounds, cornerPx)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
