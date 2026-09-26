@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
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

class NexusSegmentedRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

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


    private val iconView = ImageView(context)
    private val textContainer = LinearLayout(context)
    private val labelView = TextView(context)
    private val badgeView = TextView(context)
    private val subtitleView = TextView(context)
    private val segmentedContainer = GlideSegmentedContainer(context)
    private val containerBackground = GradientDrawable()
    private val scrollView = HorizontalScrollView(context)
    private val dp = resources.displayMetrics.density

    var onValueChanged: ((String) -> Unit)? = null
    var onValueReselected: ((String) -> Unit)? = null

    private var currentValue = ""
    private val buttons = mutableMapOf<String, TextView>()

    /** Options Premium unlocks, and which feature; see [setPremiumOptions]. */
    private var premiumFeature: com.nexus.launcher.premium.PremiumFeature? = null
    private var premiumLocked: (String) -> Boolean = { false }
    private var isInlineMode = false

    private var currentTokens: NexusColorTokens = if (paperMode) {
        com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(context)
    } else {
        try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    }
    private var attachListener: NexusSegmentedThemeListener? = null
    private var isLabelSubdued = false

    private fun applyLabelStyle() {
        if (isLabelSubdued) {
            NexusTypeScale.iconLabel.bindTo(labelView, currentTokens.textSecondary)
        } else {
            NexusTypeScale.body.bindTo(labelView, currentTokens.textPrimary)
        }
    }

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        val hPad = (16 * dp).toInt()
        val vPad = (10 * dp).toInt()
        setPadding(hPad, vPad, hPad, vPad)
        gravity = Gravity.START

        iconView.apply {
            layoutParams = LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
                marginEnd = (14 * dp).toInt()
            }
            visibility = GONE
        }

        labelView.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        applyLabelStyle()

        badgeView.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (4 * dp).toInt()
            }
            textSize = 8f
            includeFontPadding = false
            letterSpacing = 0.04f
            setPadding((3.5f * dp).toInt(), (1 * dp).toInt(), (3.5f * dp).toInt(), (1 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 3 * dp
                setColor((currentTokens.textPrimary and 0x00FFFFFF) or (0x24 shl 24))
            }
            setTextColor(currentTokens.textPrimary)
            isSingleLine = true
            visibility = GONE
        }

        val labelContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            addView(labelView)
            addView(badgeView)
        }

        subtitleView.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textSize = 12.5f
            visibility = GONE
        }
        NexusTypeScale.iconLabel.bindTo(subtitleView, currentTokens.textSecondary)

        textContainer.apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            addView(labelContainer)
            addView(subtitleView)
        }

        val isEInk = paperMode
        containerBackground.cornerRadius = if (isEInk) 0f else 16 * dp
        containerBackground.setColor(currentTokens.surfaceRaised)
        if (isEInk) {
            containerBackground.setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
        }

        segmentedContainer.apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            background = containerBackground
            setPadding((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            buttonsProvider = { buttons }
            onGlideSelect = { value ->
                if (currentValue != value && !refusedByPremium(value, offer = false)) {
                    currentValue = value
                    updateSelection()
                    performHapticFeedback(
                        android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    onValueChanged?.invoke(value)
                }
            }
        }

        scrollView.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(segmentedContainer)
        }
        segmentedContainer.scrollView = scrollView

        addView(iconView)
        addView(textContainer)
        addView(scrollView)

        setupThemeBinding()
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        if (iconRes != 0) {
            iconView.setImageResource(iconRes)
            iconView.imageTintList = ColorStateList.valueOf(currentTokens.textPrimary)
            iconView.visibility = VISIBLE
        } else {
            iconView.visibility = GONE
        }
    }

    fun setBadge(badge: String?) {
        if (!badge.isNullOrBlank()) {
            badgeView.text = badge.uppercase()
            badgeView.visibility = VISIBLE
        } else {
            badgeView.visibility = GONE
        }
    }

    fun setSubtitle(subtitle: String?) {
        if (!subtitle.isNullOrEmpty()) {
            subtitleView.text = subtitle
            subtitleView.visibility = VISIBLE
        } else {
            subtitleView.visibility = GONE
        }
    }

    fun setInline(inline: Boolean) {
        isInlineMode = inline
        if (inline) {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            textContainer.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * dp).toInt()
            }
            scrollView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        } else {
            orientation = VERTICAL
            gravity = Gravity.START
            textContainer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            scrollView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
    }

    /** Draws the label like a secondary line: for a row that belongs to the row above it. */
    fun setLabelSubdued(subdued: Boolean) {
        isLabelSubdued = subdued
        applyLabelStyle()
    }

    fun setContentPadding(start: Int, top: Int, end: Int, bottom: Int) {
        setPadding(start, top, end, bottom)
    }

    fun configure(
        label: String,
        options: List<Pair<String, String>>,
        initialValue: String,
        typefaceProvider: ((value: String) -> android.graphics.Typeface?)? = null,
        inline: Boolean = isInlineMode,
        @DrawableRes iconRes: Int = 0,
        subtitle: String? = null,
        badge: String? = null
    ) {
        labelView.text = label
        setIcon(iconRes)
        setSubtitle(subtitle)
        setBadge(badge)
        setInline(inline)
        currentValue = initialValue

        segmentedContainer.removeAllViews()
        buttons.clear()

        val buttonPadH = (12 * dp).toInt()
        val buttonPadV = (6 * dp).toInt()

        options.forEach { (key, text) ->
            val button = TextView(context).apply {
                this.text = text
                setPadding(buttonPadH, buttonPadV, buttonPadH, buttonPadV)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true

                val customTf = typefaceProvider?.invoke(key)
                if (customTf != null) {
                    typeface = customTf
                }

                setOnClickListener {
                    if (currentValue == key) {
                        onValueReselected?.invoke(key)
                    } else if (!refusedByPremium(key, offer = true)) {
                        currentValue = key
                        updateSelection()
                        performHapticFeedback(
                            android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                        onValueChanged?.invoke(key)
                    }
                }
            }
            buttons[key] = button
            segmentedContainer.addView(button)
        }

        updateSelection()
        scrollToSelected()
    }

    /**
     * Marks the options [isLocked] picks with a lock while [feature] is locked. Survives
     * [configure], and follows entitlement while the row is on screen.
     */
    fun setPremiumOptions(feature: com.nexus.launcher.premium.PremiumFeature, isLocked: (key: String) -> Boolean) {
        val first = premiumFeature == null
        premiumFeature = feature
        premiumLocked = isLocked
        updateSelection()
        if (first) com.nexus.launcher.ui.premium.PremiumBadges.bind(this) { updateSelection() }
    }

    /** A locked option is never selected: a tap offers the paywall, a glide passes over it. */
    private fun refusedByPremium(key: String, offer: Boolean): Boolean {
        val feature = premiumFeature ?: return false
        if (!premiumLocked(key) || !com.nexus.launcher.ui.premium.PremiumBadges.isShown(feature)) return false
        if (offer) com.nexus.launcher.premium.PremiumGate.allow(context, feature)
        return true
    }

    fun setSelectedValue(value: String) {
        if (currentValue != value) {
            currentValue = value
            updateSelection()
            scrollToSelected()
        }
    }

    fun setValue(value: String) = setSelectedValue(value)

    private fun scrollToSelected() {
        val btn = buttons[currentValue] ?: return
        scrollView.post {
            val scrollX = btn.left - (scrollView.width - btn.width) / 2
            scrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    fun getSelectedValue(): String = currentValue

    fun clearSelection() {
        currentValue = ""
        updateSelection()
    }

    fun applyAccentColor(accentArgb: Int) {
        updateSelection()
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        NexusSegmentedRowPainter.applyTokens(
            tokens, paperMode, dp, iconView, labelView, isLabelSubdued,
            subtitleView, badgeView, containerBackground,
        )
        updateSelection()
    }

    private fun updateSelection() {
        val isEInk = paperMode
        buttons.forEach { (value, btn) ->
            if (isEInk) {
                btn.typeface = android.graphics.Typeface.MONOSPACE
            }
            val ink: Int
            if (value == currentValue) {
                btn.background = GradientDrawable().apply {
                    setColor(currentTokens.textPrimary)
                    cornerRadius = if (isEInk) 0f else 14 * dp
                }
                btn.setTextColor(currentTokens.bg)
                NexusTypeScale.labelSmall.bindTo(btn, currentTokens.bg)
                ink = currentTokens.bg
            } else {
                btn.background = null
                btn.setTextColor(currentTokens.textSecondary)
                NexusTypeScale.caption.bindTo(btn, currentTokens.textSecondary)
                ink = currentTokens.textSecondary
            }
            val feature = premiumFeature
            val locked = feature != null && premiumLocked(value) && com.nexus.launcher.ui.premium.PremiumBadges.isShown(feature)
            com.nexus.launcher.ui.premium.PremiumBadges.decorateOption(btn, locked, ink)
        }
    }

    private fun setupThemeBinding() {
        val listener = NexusSegmentedThemeListener(this)
        attachListener = listener
        addOnAttachStateChangeListener(listener)
        if (isAttachedToWindow) {
            listener.subscribe(this)
        }
    }
}
