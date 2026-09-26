package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
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

class NexusToggleRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val iconView: android.widget.ImageView
    private val labelView: TextView
    private val badgeView: TextView
    private val badgeBackground = android.graphics.drawable.GradientDrawable()
    private val subtitleView: TextView
    private val toggle: NexusElasticSwitch
    private var attachListener: ThemeAttachListener? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    var onCheckedChanged: ((Boolean) -> Unit)? = null

    val isChecked: Boolean
        get() = toggle.isChecked

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val dp = context.resources.displayMetrics.density
        minimumHeight = (44 * dp).toInt()
        val hPad = (16 * dp).toInt()
        val vPad = (4 * dp).toInt()
        setPadding(hPad, vPad, hPad, vPad)
        isClickable = true
        isFocusable = true

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Leading glyph, same unfilled treatment NexusNavRow uses — tinted by tokens, no chip.
        iconView = android.widget.ImageView(context).apply {
            val glyph = (24 * dp).toInt()
            layoutParams = LayoutParams(glyph, glyph).apply {
                marginEnd = (12 * dp).toInt()
            }
            visibility = GONE
        }
        topRow.addView(iconView)

        labelView = TextView(context).apply {
            NexusTypeScale.body.bindTo(this, currentTokens.textPrimary)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * dp).toInt()
            }
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isSingleLine = true
            maxLines = 1
        }
        topRow.addView(labelView)

        badgeBackground.cornerRadius = 999f * dp
        badgeBackground.setColor(currentTokens.accentMuted)
        badgeView = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, currentTokens.accent)
            textSize = 10f
            isAllCaps = true
            letterSpacing = 0.06f
            background = badgeBackground
            setPadding((7 * dp).toInt(), (2 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt())
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (12 * dp).toInt()
            }
            visibility = GONE
        }
        topRow.addView(badgeView)

        toggle = NexusElasticSwitch(context).apply {
            onCheckedChange = { checked ->
                onCheckedChanged?.invoke(checked)
            }
        }
        topRow.addView(toggle)
        addView(topRow)

        subtitleView = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, currentTokens.textSecondary)
            textSize = 12f
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (-2 * dp).toInt()
            }
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isSingleLine = true
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = GONE
        }
        addView(subtitleView)

        setOnClickListener {
            toggle.toggle()
        }

        setupThemeBinding()
    }

    fun configure(label: String, checked: Boolean, subtitle: String? = null) {
        labelView.text = label
        setSubtitle(subtitle)
        toggle.setChecked(checked, animate = false)
    }

    fun setSubtitle(subtitle: String?) {
        if (!subtitle.isNullOrEmpty()) {
            subtitleView.text = subtitle
            subtitleView.visibility = VISIBLE
        } else {
            subtitleView.visibility = GONE
        }
    }

    fun setChecked(value: Boolean) {
        toggle.setChecked(value, animate = true)
    }

    /** Small accent pill after the label — e.g. "Beta". Null hides it. */
    /** Shows a leading icon badge, or hides it when [res] is null. */
    fun setIcon(res: Int?) {
        if (res == null) {
            iconView.visibility = GONE
            return
        }
        iconView.setImageResource(res)
        iconView.visibility = VISIBLE
        applyIconTokens(currentTokens)
    }

    private fun applyIconTokens(tokens: NexusColorTokens) {
        iconView.imageTintList = android.content.res.ColorStateList.valueOf(tokens.textPrimary)
    }

    fun setBadge(text: String?) {
        if (text.isNullOrEmpty()) {
            badgeView.visibility = GONE
        } else {
            badgeView.text = text
            badgeView.visibility = VISIBLE
        }
    }

    /** Draws the label like a secondary line: for a row that belongs to the row above it. */
    fun setLabelSubdued(subdued: Boolean) {
        isLabelSubdued = subdued
        if (subdued) {
            com.nexus.launcher.typography.NexusTypeScale.iconLabel.bindTo(labelView, currentTokens.textSecondary)
        } else {
            com.nexus.launcher.typography.NexusTypeScale.body.bindTo(labelView, currentTokens.textPrimary)
        }
    }

    private var isLabelSubdued = false

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        labelView.setTextColor(if (isLabelSubdued) tokens.textSecondary else tokens.textPrimary)
        subtitleView.setTextColor(tokens.textSecondary)
        badgeView.setTextColor(tokens.accent)
        badgeBackground.setColor(tokens.accentMuted)
        applyIconTokens(tokens)
        toggle.applyTokens(tokens)
    }

    fun applyAccentColor(accentArgb: Int) {
        toggle.applyTokens(currentTokens.copy(accent = accentArgb))
    }

    private fun setupThemeBinding() {
        val listener = ThemeAttachListener(this)
        attachListener = listener
        addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(this)) {
            listener.subscribe(this)
        }
    }

    private class ThemeAttachListener(private val row: NexusToggleRow) : OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? NexusToggleRow ?: row)
        }

        override fun onViewDetachedFromWindow(v: View) {
            job?.cancel()
            job = null
        }

        fun subscribe(target: NexusToggleRow) {
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
