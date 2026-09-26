package com.nexus.launcher.feed

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedPanelLayout

/**
 * Bottom navigation bar with a 1dp top divider, polished vertical padding and 11sp typography.
 *
 * A panel, not a tinted strip. It used to be an 85% surface fill over the feed's own translucent
 * background, which let articles scrolling underneath read straight through the tabs. A
 * [FrostedPanelLayout] frosts itself from the wallpaper over an opaque base, so the bar stays
 * glass against the home screen while nothing inside the feed can bleed through it — the same
 * treatment the drawer's search pill and the overflow menu already use.
 */
class NexusFeedBottomBar(context: Context) : FrostedPanelLayout(context) {

    enum class Tab { FEED, EXPLORE, SAVED, LIBRARY, SETTINGS }

    private val dp = resources.displayMetrics.density
    var onTabSelected: ((Tab) -> Unit)? = null
    private var currentTab = Tab.FEED
    private val tabViews = mutableMapOf<Tab, LinearLayout>()
    private var tokens: NexusColorTokens = ThemeObserver.currentTokens(context)

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val rootInsets = androidx.core.view.ViewCompat.getRootWindowInsets((context as? android.app.Activity)?.window?.decorView ?: this)
        val initialNb = rootInsets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom?.coerceAtLeast((16 * dp).toInt()) ?: (22 * dp).toInt()
        val bottomPad = initialNb + (6 * dp).toInt()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (60 * dp).toInt() + bottomPad)
        setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), bottomPad)

        addView(createTab(Tab.FEED, context.getString(R.string.nexus_feed_tab_feed), R.drawable.ic_feed_rss))
        addView(createTab(Tab.EXPLORE, context.getString(R.string.nexus_feed_tab_explore), R.drawable.ic_explore))
        addView(createTab(Tab.SAVED, context.getString(R.string.nexus_feed_tab_saved), R.drawable.ic_bookmark))
        addView(createTab(Tab.LIBRARY, context.getString(R.string.nexus_feed_tab_library), R.drawable.ic_library))
        addView(createTab(Tab.SETTINGS, context.getString(R.string.nexus_feed_tab_settings), R.drawable.ic_settings))

        applyTokens(tokens)
        selectTab(Tab.FEED, notify = false)
    }

    fun updateInsets(navBarHeight: Int) {
        val bottomPad = navBarHeight.coerceAtLeast((16 * dp).toInt()) + (6 * dp).toInt()
        setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), bottomPad)
        val lp = layoutParams
        if (lp != null) {
            lp.height = (60 * dp).toInt() + bottomPad
            layoutParams = lp
        }
    }

    /** Vertical icon-only rail (phone landscape) instead of a bottom bar; see NexusFeedInsetsLayout. */
    var isRail = false
        private set

    fun setRailMode(rail: Boolean) {
        if (rail == isRail) return
        isRail = rail
        orientation = if (rail) VERTICAL else HORIZONTAL
        gravity = if (rail) Gravity.CENTER else Gravity.CENTER_VERTICAL
        for (view in tabViews.values) {
            view.findViewWithTag<TextView>("text")?.visibility = if (rail) GONE else VISIBLE
            view.layoutParams = if (rail) {
                LayoutParams((56 * dp).toInt(), (52 * dp).toInt()).apply {
                    topMargin = (4 * dp).toInt()
                    bottomMargin = (4 * dp).toInt()
                }
            } else {
                LayoutParams(0, (60 * dp).toInt(), 1f)
            }
        }
        invalidate()
    }

    /** Rail padding: clear the cutout / side nav bar at [startInset] and the system bars. */
    fun updateRailInsets(startInset: Int, statusBar: Int, navBottom: Int) {
        setPadding(startInset + (8 * dp).toInt(), statusBar, (8 * dp).toInt(), navBottom)
    }

    fun applyTokens(newTokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens

        if (isEInk) {
            // Paper is opaque by definition; no wallpaper reads through it.
            setBlurEnabled(false)
            setBaseColor(tokens.bg)
            setTint(tokens.bg or 0xFF000000.toInt())
            setBorder(android.graphics.Color.TRANSPARENT, 0f)
        } else {
            applyStyle(tokens, FrostedPanelLayout.Density.DENSE)
        }
        selectTab(currentTab, notify = false)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1dp divider on the edge facing the content: above the bar, or right of the rail.
        dividerPaint.color = tokens.divider
        val line = (1 * dp).coerceAtLeast(1f)
        if (isRail) {
            canvas.drawRect(width - line, 0f, width.toFloat(), height.toFloat(), dividerPaint)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), line, dividerPaint)
        }
    }

    private fun createTab(tab: Tab, label: String, iconRes: Int): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, (60 * dp).toInt(), 1f)
            // The label is hidden in rail mode; keep the tab named for accessibility.
            contentDescription = label
            // Increased vertical padding by 4dp (from 2dp to 6dp)
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())

            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                tag = "icon"
                layoutParams = LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
            }
            addView(icon)

            // Text size: 11sp
            val text = TextView(context).apply {
                this.text = label
                tag = "text"
                textSize = 11f
                setTextColor(tokens.textSecondary)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * dp).toInt()
                }
            }
            addView(text)

            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectTab(tab, notify = true)
            }
        }
        tabViews[tab] = container
        return container
    }

    fun selectTab(tab: Tab, notify: Boolean = true) {
        if (tab == currentTab && notify) return
        currentTab = tab
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        for ((t, view) in tabViews) {
            val isSelected = (t == tab)
            val icon = view.findViewWithTag<ImageView>("icon")
            val text = view.findViewWithTag<TextView>("text")

            if (isEInk) {
                text?.typeface = NexusFeedEInkStyler.monospaceFont
            }

            if (isSelected) {
                view.background = GradientDrawable().apply {
                    setColor(if (isEInk) tokens.surfaceRaised else ((tokens.surfaceRaised and 0x00FFFFFF) or (0xD9 shl 24)))
                    cornerRadius = if (isEInk) 0f else 16 * dp
                    if (isEInk) setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                icon?.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                text?.setTextColor(tokens.textPrimary)
            } else {
                view.background = null
                icon?.imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                text?.setTextColor(tokens.textSecondary)
            }
        }
        if (notify) onTabSelected?.invoke(tab)
    }
}
