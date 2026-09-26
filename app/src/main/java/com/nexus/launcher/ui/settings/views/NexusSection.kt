package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.typography.NexusTypeScale
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Section container for settings screens with support for Segmented Card pillows.
 */
class NexusSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val titleView: TextView
    private val cardContainer: LinearLayout
    private val cardBackground = GradientDrawable()
    private val dividers = mutableListOf<View>()
    private val pillBackgrounds = mutableListOf<GradientDrawable>()
    private var attachListener: ThemeAttachListener? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    var isSegmented: Boolean = true
        set(value) {
            field = value
            applyCardBackground()
        }

    var isTransparentCard: Boolean = false
        set(value) {
            field = value
            applyCardBackground()
        }

    /**
     * Frosted Glass renders a section as one translucent pane rather than a stack of opaque
     * pills.
     *
     * Segmented mode gives every row its own card filled with `tokens.surface` — fully opaque.
     * On this screen those cards tile the whole viewport, so they painted over the frosted
     * wallpaper backdrop [com.nexus.launcher.ui.settings.SettingsActivity] draws behind them and
     * the mode had no visible effect here at all. Glass in this app means a translucent pane over
     * a blurred backdrop with a hairline edge; a wall of opaque cards is the one arrangement that
     * cannot show it.
     *
     * So under glass the section falls back to the single-container layout this class already
     * supports — one rounded pane, rows separated by hairline dividers rather than by gaps
     * between cards, which is also how the reference glass menus are built. Rows keep their own
     * `selectableItemBackground` ripple, so press feedback is unchanged.
     */
    private val useGlassPane: Boolean
        get() = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled

    /** True when rows should be stacked in one pane instead of individually carded. */
    private val stacksInOnePane: Boolean
        get() = !isSegmented || isTransparentCard || useGlassPane

    /** True when the container actually paints a pane behind the rows — the only case where
     *  clipping children to its rounded outline is wanted. Clipping an unfilled container would
     *  shave the corners off the individually-carded pills it is merely holding. */
    private val drawsPane: Boolean
        get() = !isTransparentCard && (!isSegmented || useGlassPane)

    private fun applyCardBackground() {
        when {
            !drawsPane -> {
                cardBackground.setColor(Color.TRANSPARENT)
                cardBackground.setStroke(0, Color.TRANSPARENT)
            }
            useGlassPane -> {
                val frosted = com.nexus.launcher.ui.glass.FrostedGlassEngine
                    .resolveFrostedTokens(currentTokens)
                val fillAlpha =
                    (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f)
                        .toInt().coerceIn(0, 255)
                cardBackground.setColor((frosted.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cardBackground.setStroke(
                    (1 * density).toInt().coerceAtLeast(1),
                    frosted.border
                )
            }
            else -> {
                cardBackground.setColor(currentTokens.surface)
                cardBackground.setStroke(0, Color.TRANSPARENT)
            }
        }
        cardContainerOrNull?.clipToOutline = drawsPane
    }

    private var cardContainerOrNull: LinearLayout? = null

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        titleView = TextView(context).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (20 * density).toInt()
                bottomMargin = (8 * density).toInt()
                marginStart = (4 * density).toInt()
            }
            visibility = GONE
        }
        NexusTypeScale.sectionLabel.bindTo(titleView)
        addView(titleView)

        cardBackground.cornerRadius = 16 * density

        cardContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = cardBackground
        }
        cardContainerOrNull = cardContainer
        addView(cardContainer)
        applyCardBackground()

        setupThemeBinding()
    }

    fun setTitle(title: String?) {
        if (!title.isNullOrEmpty()) {
            titleView.text = title.uppercase()
            titleView.visibility = VISIBLE
        } else {
            titleView.visibility = GONE
        }
    }

    fun addRow(row: View) {
        if (isTransparentCard || (isSegmented && !useGlassPane)) {
            cardContainer.addView(row)
        } else {
            if (cardContainer.childCount > 0) {
                val divider = createDivider()
                dividers.add(divider)
                cardContainer.addView(divider)
            }
            cardContainer.addView(row)
        }
        applyTokensToChild(row, currentTokens)
    }

    fun setRows(vararg rows: View) {
        setRows(rows.toList())
    }

    fun setRows(rows: List<View>) {
        cardContainer.removeAllViews()
        dividers.clear()
        pillBackgrounds.clear()

        if (stacksInOnePane) {
            applyCardBackground()
            rows.forEach { row -> addRow(row) }
            return
        }

        val n = rows.size
        val radiusOuter = 16f * density
        val radiusInner = 4f * density
        val gap = (3 * density).toInt()

        for (i in 0 until n) {
            val row = rows[i]
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

            pillBg.setColor(currentTokens.surface)
            pillBg.setStroke((1 * density).toInt().coerceAtLeast(1), currentTokens.divider)
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

            cardContainer.addView(pill)
            applyTokensToChild(row, currentTokens)
        }
    }

    fun getCardContainer(): LinearLayout = cardContainer

    private fun createDivider(): View {
        val dividerColor = (currentTokens.textSecondary and 0x00FFFFFF) or (0x33 shl 24)
        return View(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1.5f * density).toInt().coerceAtLeast(1)
            ).apply {
                marginStart = (16 * density).toInt()
                marginEnd = (16 * density).toInt()
            }
            setBackgroundColor(dividerColor)
        }
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        titleView.setTextColor(tokens.textSecondary)
        applyCardBackground()
        pillBackgrounds.forEach {
            it.setColor(tokens.surface)
            it.setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
        val dividerColor = (tokens.textSecondary and 0x00FFFFFF) or (0x33 shl 24)
        dividers.forEach { it.setBackgroundColor(dividerColor) }

        for (i in 0 until cardContainer.childCount) {
            val child = cardContainer.getChildAt(i)
            if (child is LinearLayout && child.childCount > 0) {
                for (j in 0 until child.childCount) {
                    applyTokensToChild(child.getChildAt(j), tokens)
                }
            } else {
                applyTokensToChild(child, tokens)
            }
        }
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

    private class ThemeAttachListener(private val section: NexusSection) : OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? NexusSection ?: section)
        }

        override fun onViewDetachedFromWindow(v: View) {
            cleanup()
        }

        fun cleanup() {
            job?.cancel()
            job = null
        }

        fun subscribe(target: NexusSection) {
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
                                target.applyTokens(tokens)
                            }
                        }
                    }
                } catch (_: Exception) {
                    target.applyTokens(NexusColorTokens.Dark)
                }
            } else {
                target.applyTokens(NexusColorTokens.Dark)
            }
        }
    }
}
