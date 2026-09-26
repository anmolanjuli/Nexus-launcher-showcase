package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Segmented Card container for grouped settings controls.
 * Encapsulates child rows into individual pillows with 3dp recessed gaps.
 *
 * ## Neumorphism
 *
 * Under the Neumorphism UI Style the group draws as **one raised card** — the same
 * [NexusNeumorphicDraw.drawRaisedSurface] the widgets, dock and folders use, so Settings and the
 * home screen share one Neumorphism instead of Settings silently rendering as Default. The rows
 * become transparent segments on that card, separated by hairlines; separate raised pillows three
 * pixels apart would pile their shadows into each other.
 *
 * A raised surface is mostly shadow drawn *outside* the card. Rather than shrink every card to
 * leave room inside its own bounds — which would pull rows out of line with the section headers
 * above them — the shadow spills into the margins around it, via
 * [com.nexus.launcher.ui.glass.NeumorphicSurfaces.releaseClipping]. That stops *below* the scroll
 * view on purpose: an earlier version unclipped the scroll view itself, which would have let
 * scrolled-away content draw over a sheet's header and buttons.
 *
 * Settings is recreated when the UI Style changes, so the style is read, not observed.
 */
class SettingsSectionGroupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val childRows = mutableListOf<View>()
    private val pillBackgrounds = mutableListOf<GradientDrawable>()
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var attachListener: ThemeAttachListener? = null

    private val cardRect = RectF()
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette? = null

    /** Neither Frosted Glass nor the flat Default style. */
    private val isNeumorphic: Boolean
        get() = com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive

    /**
     * Paper styling, for the copy of this view that lives inside the Feed's E-Ink Paper Mode.
     *
     * Off by default and set only by the Feed. This used to read E-Ink Paper Mode straight from the
     * global switch, which meant turning on paper for the news feed also squared off and repainted
     * Settings, the context menus and the drawer — every screen built from these shared views. The
     * mode belongs to the Feed and its readers; a view cannot tell where it is, so the Feed says so.
     */
    var paperMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (4 * density).toInt()
            bottomMargin = (12 * density).toInt()
        }

        val isEInk = paperMode
        currentTokens = if (isEInk) com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(context) else NexusColorTokens.Dark

        setupThemeBinding()
    }

    /** Drops every row so the group can be rebuilt — used where the row set itself changes, not
     *  just its contents (the Premium feature list re-renders when entitlement flips). */
    fun clearRows() {
        childRows.clear()
        rebuildPillows()
    }

    fun addChildRow(row: View, isVisible: Boolean = true) {
        childRows.add(row)
        rebuildPillows()
        setRowVisibility(row, isVisible)
    }

    fun setRowVisibility(row: View, isVisible: Boolean) {
        row.visibility = if (isVisible) View.VISIBLE else View.GONE
        (row.parent as? View)?.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun rebuildPillows() {
        removeAllViews()
        pillBackgrounds.clear()

        val n = childRows.size
        val isEInk = paperMode
        val radiusOuter = if (isEInk) 0f else 16f * density
        val radiusInner = if (isEInk) 0f else 4f * density
        val gap = (3 * density).toInt()

        for (i in 0 until n) {
            val row = childRows[i]
            (row.parent as? ViewGroup)?.removeView(row)

            val pillBg = GradientDrawable()
            val bottomMargin: Int

            if (n == 1) {
                pillBg.cornerRadius = radiusOuter
                bottomMargin = 0
            } else when (i) {
                0 -> {
                    pillBg.cornerRadii = floatArrayOf(
                        radiusOuter, radiusOuter,
                        radiusOuter, radiusOuter,
                        radiusInner, radiusInner,
                        radiusInner, radiusInner
                    )
                    bottomMargin = gap
                }
                n - 1 -> {
                    pillBg.cornerRadii = floatArrayOf(
                        radiusInner, radiusInner,
                        radiusInner, radiusInner,
                        radiusOuter, radiusOuter,
                        radiusOuter, radiusOuter
                    )
                    bottomMargin = 0
                }
                else -> {
                    pillBg.cornerRadius = radiusInner
                    bottomMargin = gap
                }
            }

            paintPill(pillBg, currentTokens)
            pillBackgrounds.add(pillBg)

            val pill = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    this.bottomMargin = bottomMargin
                }
                background = pillBg
                clipToOutline = true
                addView(row)
            }

            addView(pill)
            applyTokensToChild(row, currentTokens)
        }
    }

    fun applyTokens(tokens: NexusColorTokens) {
        val isEInk = paperMode
        val effectiveTokens = if (isEInk) com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(context) else tokens
        currentTokens = effectiveTokens
        palette = if (isNeumorphic) com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(effectiveTokens) else null
        rebuildPillows()
        invalidate()

        for (row in childRows) {
            applyTokensToChild(row, effectiveTokens)
        }
    }

    /** A pillow's own fill: the card in the flat styles, nothing under Neumorphism's shared card. */
    private fun paintPill(pill: GradientDrawable, tokens: NexusColorTokens) {
        if (isNeumorphic) {
            pill.setColor(Color.TRANSPARENT)
            pill.setStroke(0, Color.TRANSPARENT)
        } else {
            val isEInk = paperMode
            val effectiveTokens = if (isEInk) com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(context) else tokens
            pill.setColor(effectiveTokens.surface)
            pill.setStroke((1 * density).toInt().coerceAtLeast(1), effectiveTokens.divider)
        }
    }

    /**
     * Under Neumorphism: the raised card behind every row, then a hairline between visible rows,
     * then the rows themselves. Hidden rows are GONE, so the card always hugs what is shown.
     */
    override fun dispatchDraw(canvas: Canvas) {
        val soft = palette
        if (isNeumorphic && soft != null && width > 0 && height > 0) {
            cardRect.set(0f, 0f, width.toFloat(), height.toFloat())
            // Shape 1 is a rounded rectangle whose corner is 0.4 of the radius passed in.
            com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawRaisedSurface(
                canvas, cardRect, CARD_CORNER_DP * density / 0.4f, 1, soft, density,
            )
            separatorPaint.color = if (soft.isLight) Color.argb(22, 0, 0, 0) else Color.argb(26, 255, 255, 255)
            val inset = 16f * density
            var previous: View? = null
            for (i in 0 until childCount) {
                val pill = getChildAt(i)
                if (pill.visibility != View.VISIBLE) continue
                previous?.let { above ->
                    val y = (above.bottom + pill.top) / 2f
                    canvas.drawRect(inset, y - 0.5f, width - inset, y + 0.5f, separatorPaint)
                }
                previous = pill
            }
        }
        super.dispatchDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isNeumorphic) com.nexus.launcher.ui.glass.NeumorphicSurfaces.releaseClipping(this)
    }

    private fun applyTokensToChild(child: View, tokens: NexusColorTokens) {
        (child as? NexusNavRow)?.applyTokens(tokens)
        (child as? NexusSegmentedRow)?.applyTokens(tokens)
        (child as? NexusSliderRow)?.applyTokens(tokens)
        (child as? NexusToggleRow)?.applyTokens(tokens)
    }

    private fun setupThemeBinding() {
        val listener = ThemeAttachListener(this)
        attachListener = listener
        addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(this)) {
            listener.subscribe(this)
        }
    }

    private companion object {
        /** Matches the outer corner of the flat pillows, so the card shape is the same in every style. */
        const val CARD_CORNER_DP = 16f
    }

    private class ThemeAttachListener(private val groupView: SettingsSectionGroupView) : OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? SettingsSectionGroupView ?: groupView)
        }

        override fun onViewDetachedFromWindow(v: View) {
            cleanup()
        }

        fun cleanup() {
            job?.cancel()
            job = null
        }

        fun subscribe(target: SettingsSectionGroupView) {
            cleanup()
            val owner = target.findViewTreeLifecycleOwner() ?: (target.context as? LifecycleOwner)
            if (owner != null) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        target.context.applicationContext,
                        ThemeEntryPoint::class.java
                    ).themeController()
                    job = owner.lifecycleScope.launch {
                        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            controller.currentTokens.collect { tokens ->
                                val effectiveTokens = if (target.paperMode) {
                                    com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(target.context)
                                } else {
                                    tokens
                                }
                                target.applyTokens(effectiveTokens)
                            }
                        }
                    }
                } catch (_: Exception) {
                    val fallback = if (target.paperMode) {
                        com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(target.context)
                    } else {
                        NexusColorTokens.Dark
                    }
                    target.applyTokens(fallback)
                }
            } else {
                val fallback = if (target.paperMode) {
                    com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(target.context)
                } else {
                    NexusColorTokens.Dark
                }
                target.applyTokens(fallback)
            }
        }
    }
}
