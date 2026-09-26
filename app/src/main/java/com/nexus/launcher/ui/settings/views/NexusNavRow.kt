package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.typography.NexusTypeScale
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Clean, flat navigation row for [NexusSection] (Linear-inspired minimal style).
 * Replaces legacy colored icon badges with sleek, unbordered typography and icons.
 */
class NexusNavRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val content: LinearLayout
    private val iconView: ImageView
    private val titleView: TextView
    private val badgeView: TextView
    private val badgeBackground = android.graphics.drawable.GradientDrawable()
    private val subtitleView: TextView
    private val chevronView: ImageView
    private val toggleView: NexusElasticSwitch
    private var attachListener: ThemeAttachListener? = null

    private var tintIcon: Boolean = true
    private var endAsCheck: Boolean = false
    var onToggleChecked: ((Boolean) -> Unit)? = null

    private var currentTokens: NexusColorTokens = try {
        com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    constructor(
        context: Context,
        title: String,
        subtitle: String? = null,
        @DrawableRes iconRes: Int = 0,
        showChevron: Boolean = true,
        tintIcon: Boolean = true,
        onTap: (() -> Unit)? = null
    ) : this(context) {
        setTitle(title)
        setSubtitle(subtitle)
        setIcon(iconRes, tintIcon)
        setChevronVisible(showChevron)
        if (onTap != null) {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onTap()
            }
        }
    }

    init {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        minimumHeight = (64 * density).toInt()

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        foreground = ContextCompat.getDrawable(context, outValue.resourceId)
        isClickable = true
        isFocusable = true

        content = LinearLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = (16 * density).toInt()
            val vPad = (14 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }

        iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt()).apply {
                marginEnd = (14 * density).toInt()
            }
            visibility = GONE
        }
        content.addView(iconView)

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * density).toInt()
            }
        }

        titleView = TextView(context).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        NexusTypeScale.bodyStrong.bindTo(titleView)

        // Title and badge share a row so the badge sits beside the title rather than pushing the
        // subtitle around — same treatment NexusToggleRow already uses.
        val dpUnit = context.resources.displayMetrics.density
        badgeBackground.cornerRadius = 999f * dpUnit
        badgeView = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this)
            textSize = 10f
            isAllCaps = true
            letterSpacing = 0.06f
            background = badgeBackground
            setPadding((7 * dpUnit).toInt(), (2 * dpUnit).toInt(), (7 * dpUnit).toInt(), (2 * dpUnit).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = (8 * dpUnit).toInt() }
            visibility = GONE
        }
        textContainer.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                addView(titleView)
                addView(badgeView)
            },
        )

        subtitleView = TextView(context).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textSize = 12.5f
            visibility = GONE
        }
        NexusTypeScale.iconLabel.bindTo(subtitleView)
        textContainer.addView(subtitleView)

        content.addView(textContainer)

        chevronView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
            setImageResource(R.drawable.ic_chevron_forward)
            visibility = GONE
        }
        content.addView(chevronView)

        toggleView = NexusElasticSwitch(context).apply {
            visibility = GONE
            isClickable = false
            isFocusable = false
            onCheckedChange = { checked ->
                onToggleChecked?.invoke(checked)
            }
        }
        content.addView(toggleView)

        addView(content)

        setupThemeBinding()
    }

    private var isBoldTitle = true
    private var landingTitle = false

    fun setTitle(title: String, bold: Boolean = isBoldTitle) {
        titleView.text = title
        setTitleBold(bold)
    }

    fun setTitleBold(bold: Boolean) {
        isBoldTitle = bold
        applyTitleStyle()
    }

    fun setLandingTitle(enabled: Boolean = true) {
        landingTitle = enabled
        applyTitleStyle()
    }

    private fun applyTitleStyle() {
        when {
            landingTitle -> NexusTypeScale.hubRowTitle.bindTo(titleView)
            isBoldTitle -> NexusTypeScale.bodyStrong.bindTo(titleView)
            else -> NexusTypeScale.body.bindTo(titleView)
        }
    }

    fun setSubtitle(subtitle: String?) {
        if (subtitle != null) {
            subtitleView.text = subtitle
            subtitleView.visibility = VISIBLE
        } else {
            subtitleView.visibility = GONE
        }
    }

    fun setContentPadding(start: Int, top: Int, end: Int, bottom: Int) {
        content.setPadding(start, top, end, bottom)
    }

    fun setIcon(@DrawableRes iconRes: Int, tint: Boolean = true) {
        tintIcon = tint
        if (iconRes != 0) {
            iconView.setImageResource(iconRes)
            iconView.imageTintList = if (tint) ColorStateList.valueOf(currentTokens.textPrimary) else null
            iconView.visibility = VISIBLE
        } else {
            iconView.visibility = GONE
        }
    }

    fun setIconDrawable(drawable: android.graphics.drawable.Drawable?, tint: Boolean = false) {
        tintIcon = tint
        if (drawable != null) {
            iconView.setImageDrawable(drawable)
            iconView.imageTintList = if (tint) ColorStateList.valueOf(currentTokens.textPrimary) else null
            iconView.visibility = VISIBLE
        } else {
            iconView.visibility = GONE
        }
    }

    fun setChevronVisible(visible: Boolean) {
        chevronView.visibility = if (visible) VISIBLE else GONE
        if (visible) toggleView.visibility = GONE
    }

    fun setEndAsCheck(showCheck: Boolean) {
        endAsCheck = showCheck
        if (showCheck) {
            chevronView.setImageResource(R.drawable.ic_check)
            chevronView.visibility = VISIBLE
            toggleView.visibility = GONE
        } else {
            chevronView.setImageResource(R.drawable.ic_chevron_forward)
        }
        chevronView.imageTintList = ColorStateList.valueOf(
            if (showCheck) currentTokens.textPrimary else currentTokens.textSecondary
        )
    }

    fun setChevronRotation(rotationDeg: Float, animate: Boolean = true) {
        if (animate) {
            chevronView.animate().rotation(rotationDeg).setDuration(200).start()
        } else {
            chevronView.rotation = rotationDeg
        }
    }

    fun setToggle(visible: Boolean, checked: Boolean = false) {
        toggleView.visibility = if (visible) VISIBLE else GONE
        if (visible) {
            chevronView.visibility = GONE
            toggleView.setChecked(checked, animate = false)
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        toggleView.setChecked(checked, animate = animate)
    }

    fun setToggleChecked(checked: Boolean) {
        toggleView.setChecked(checked, animate = false)
    }

    fun isToggleChecked(): Boolean = toggleView.isChecked

    /** Shows a small pill beside the title, or hides it when [text] is null. */
    fun setBadge(text: String?) {
        if (text.isNullOrBlank()) {
            badgeView.visibility = GONE
            return
        }
        badgeView.text = text
        badgeView.visibility = VISIBLE
        badgeBackground.setColor(currentTokens.accentMuted)
        badgeView.setTextColor(currentTokens.accent)
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        titleView.setTextColor(tokens.textPrimary)
        badgeBackground.setColor(tokens.accentMuted)
        badgeView.setTextColor(tokens.accent)
        subtitleView.setTextColor(tokens.textSecondary)
        if (tintIcon) {
            iconView.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        } else {
            iconView.imageTintList = null
        }
        chevronView.imageTintList = ColorStateList.valueOf(
            if (endAsCheck) tokens.textPrimary else tokens.textSecondary
        )
        toggleView.applyTokens(tokens)
    }

    private fun setupThemeBinding() {
        val listener = ThemeAttachListener(this)
        attachListener = listener
        addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(this)) {
            listener.subscribe(this)
        }
    }

    private class ThemeAttachListener(private val row: NexusNavRow) : OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? NexusNavRow ?: row)
        }

        override fun onViewDetachedFromWindow(v: View) {
            job?.cancel()
            job = null
        }

        fun subscribe(target: NexusNavRow) {
            val owner: LifecycleOwner = target.findViewTreeLifecycleOwner() ?: return
            job?.cancel()
            job = owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        val controller = EntryPointAccessors.fromApplication(
                            target.context.applicationContext,
                            ThemeEntryPoint::class.java
                        ).themeController()
                        controller.currentTokens.collect { tokens ->
                            target.applyTokens(tokens)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
