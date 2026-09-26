@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.slider.Slider
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.typography.NexusTypeScale
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NexusSliderRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val labelView: TextView
    private val subtitleView: TextView
    private val valueView: TextView
    private val slider: Slider
    private var formatValue: (Int) -> String = Int::toString
    private var attachListener: ThemeAttachListener? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    var onValueChanged: ((Int) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val dp = context.resources.displayMetrics.density
        minimumHeight = (44 * dp).toInt()
        val hPad = (16 * dp).toInt()
        val vPad = (4 * dp).toInt()
        setPadding(hPad, vPad, hPad, vPad)

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        labelView = TextView(context).apply {
            NexusTypeScale.body.bindTo(this, currentTokens.textPrimary)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isSingleLine = true
            maxLines = 1
            includeFontPadding = false
        }

        slider = LayoutInflater.from(context)
            .inflate(R.layout.nexus_slider, this, false) as Slider

        slider.apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * dp).toInt()
                marginEnd = (6 * dp).toInt()
            }
            trackHeight = (18 * dp).toInt()
            thumbRadius = (9 * dp).toInt()
            thumbElevation = 0f
            isTickVisible = true
            tickActiveTintList = ColorStateList.valueOf(Color.parseColor("#80000000"))
            tickInactiveTintList = ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
            setPadding(0, 0, 0, 0)
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = 0f
            addOnChangeListener { _, value, fromUser ->
                valueView.text = formatValue(value.toInt())
                if (fromUser) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    onValueChanged?.invoke(value.toInt())
                }
            }
        }

        valueView = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, currentTokens.textSecondary)
            textSize = 10f
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            minWidth = (32 * dp).toInt()
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            includeFontPadding = false
            isSingleLine = true
            maxLines = 1
        }

        topRow.addView(labelView)
        topRow.addView(slider)
        topRow.addView(valueView)
        addView(topRow)

        subtitleView = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, currentTokens.textSecondary)
            textSize = 12f
            includeFontPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (-7 * dp).toInt()
            }
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isSingleLine = true
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = GONE
        }
        addView(subtitleView)

        applyAccentColor(currentTokens.accent)
        setupThemeBinding()
    }

    fun configure(
        label: String,
        min: Int,
        max: Int,
        value: Int,
        stepSize: Float = 1f,
        subtitle: String? = null,
        formatValue: (Int) -> String = Int::toString
    ) {
        this.formatValue = formatValue
        labelView.text = label
        setSubtitle(subtitle)
        val safeMin = min
        val safeMax = max.coerceAtLeast(safeMin + 1)

        slider.stepSize = 0f
        slider.valueFrom = safeMin.toFloat()
        slider.valueTo = safeMax.toFloat()

        val range = (safeMax - safeMin).toFloat()
        val effectiveStep = if (stepSize > 0f) {
            if (range % stepSize == 0f) stepSize else 1f
        } else {
            1f
        }

        slider.stepSize = effectiveStep
        slider.isTickVisible = true

        val raw = value.toFloat().coerceIn(safeMin.toFloat(), safeMax.toFloat())
        val steps = Math.round((raw - safeMin) / effectiveStep)
        val coerced = (safeMin + steps * effectiveStep).coerceIn(safeMin.toFloat(), safeMax.toFloat())
        slider.value = coerced
        valueView.text = formatValue(coerced.toInt())
    }

    fun configure(
        label: String,
        min: Int,
        max: Int,
        value: Int,
        formatValue: (Int) -> String
    ) = configure(label, min, max, value, 1f, null, formatValue)

    fun setSubtitle(subtitle: String?) {
        if (!subtitle.isNullOrEmpty()) {
            subtitleView.text = subtitle
            subtitleView.visibility = VISIBLE
        } else {
            subtitleView.visibility = GONE
        }
    }

    fun setValue(value: Int) {
        val raw = value.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        val step = if (slider.stepSize > 0f) slider.stepSize else 1f
        val steps = Math.round((raw - slider.valueFrom) / step)
        val coerced = (slider.valueFrom + steps * step).coerceIn(slider.valueFrom, slider.valueTo)
        slider.value = coerced
        valueView.text = formatValue(coerced.toInt())
    }

    fun applyAccentColor(accentArgb: Int) {
        val activeTint = ColorStateList.valueOf(currentTokens.textPrimary)
        val inactiveTint = ColorStateList.valueOf(currentTokens.surfaceRaised)
        slider.trackActiveTintList = activeTint
        slider.trackInactiveTintList = inactiveTint
        slider.thumbTintList = ColorStateList.valueOf(currentTokens.textPrimary)
        valueView.setTextColor(currentTokens.textSecondary)
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
        valueView.setTextColor(tokens.textSecondary)
        slider.trackActiveTintList = ColorStateList.valueOf(tokens.textPrimary)
        slider.trackInactiveTintList = ColorStateList.valueOf(tokens.surfaceRaised)
        slider.thumbTintList = ColorStateList.valueOf(tokens.textPrimary)

        val isLight = tokens.bg == NexusColorTokens.Light.bg ||
                androidx.core.graphics.ColorUtils.calculateLuminance(tokens.bg) > 0.5
        val activeDots = if (isLight) Color.argb(0x99, 0xFF, 0xFF, 0xFF) else Color.argb(0x80, 0x00, 0x00, 0x00)
        val inactiveDots = if (isLight) Color.argb(0x66, 0x00, 0x00, 0x00) else Color.argb(0x66, 0xFF, 0xFF, 0xFF)
        slider.tickActiveTintList = ColorStateList.valueOf(activeDots)
        slider.tickInactiveTintList = ColorStateList.valueOf(inactiveDots)
    }

    private fun setupThemeBinding() {
        val listener = ThemeAttachListener(this)
        attachListener = listener
        addOnAttachStateChangeListener(listener)
        if (isAttachedToWindow) {
            listener.subscribe(this)
        }
    }

    private class ThemeAttachListener(private val row: NexusSliderRow) : OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? NexusSliderRow ?: row)
        }

        override fun onViewDetachedFromWindow(v: View) {
            job?.cancel()
            job = null
        }

        fun subscribe(target: NexusSliderRow) {
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
