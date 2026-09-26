@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.slider.Slider
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/** Sliders and controls for wallpaper blur and color tint treatments. */
object WallpaperTreatmentControls {

    fun build(
        context: Context,
        density: Float,
        previewView: WallpaperPreviewView,
        tokens: NexusColorTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark },
        onTreatmentChanged: (blur: Float, tintHue: Float, tintStrength: Float) -> Unit
    ): View {
        val innerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false
        }

        (previewView.parent as? ViewGroup)?.removeView(previewView)

        val dimSlider = buildVerticalSlider(context, density, context.getString(com.nexus.launcher.R.string.wallpaper_dim), tokens)
        val tintContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val colorPicker = CircularColorPicker(context, density)
        val tintSlider = buildVerticalSlider(context, density, context.getString(com.nexus.launcher.R.string.wallpaper_tint_strength), tokens)

        tintContainer.addView(colorPicker, LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt()).apply {
            bottomMargin = (16 * density).toInt()
        })
        tintContainer.addView(tintSlider.first)

        val dimContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val dimSpacer = View(context).apply { visibility = View.INVISIBLE }
        dimContainer.addView(dimSpacer, LinearLayout.LayoutParams(
            (32 * density).toInt(), (32 * density).toInt()
        ).apply { bottomMargin = (16 * density).toInt() })
        dimContainer.addView(dimSlider.first)

        var currentDim = 0f
        var currentHue = 0f
        var currentStrength = 0f

        fun notifyChange() {
            onTreatmentChanged(currentDim, currentHue, currentStrength)
        }

        colorPicker.setOnClickListener {
            var host: ViewGroup = root
            var p: android.view.ViewParent? = root.parent
            while (p is ViewGroup) {
                host = p
                if (p is WallpaperSheet) break
                p = p.parent
            }
            val expanded = WallpaperColorPickerExpanded(context, density, currentHue, { hue ->
                currentHue = hue
                colorPicker.setHue(hue)
                notifyChange()
            }, {
                host.findViewWithTag<View>("ExpandedColorPicker")?.let { overlay ->
                    host.removeView(overlay)
                }
            })
            expanded.tag = "ExpandedColorPicker"
            host.addView(expanded, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        dimSlider.second.addOnChangeListener { _, value, _ ->
            val normalized = value / 100f
            currentDim = (normalized * normalized) * 100f
            notifyChange()
        }
        tintSlider.second.addOnChangeListener { _, value, _ ->
            val normalized = value / 100f
            currentStrength = (normalized * normalized)
            notifyChange()
        }

        root.tag = object : TreatmentController {
            override fun setTreatment(blur: Float, hue: Float, strength: Float) {
                currentDim = blur
                currentHue = hue
                currentStrength = strength

                dimSlider.second.value = Math.sqrt((blur / 100f).toDouble()).toFloat() * 100f
                tintSlider.second.value = Math.sqrt(strength.toDouble()).toFloat() * 100f
                colorPicker.setHue(hue)
            }
            override fun setEnabled(enabled: Boolean) {
                dimSlider.first.alpha = if (enabled) 1f else 0.3f
                dimSlider.first.isEnabled = enabled
                dimSlider.second.isEnabled = enabled
                tintContainer.alpha = if (enabled) 1f else 0.3f
                tintContainer.isEnabled = enabled
                tintSlider.second.isEnabled = enabled
                colorPicker.isEnabled = enabled
            }
        }

        innerLayout.addView(dimContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        innerLayout.addView(previewView)
        innerLayout.addView(tintContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(innerLayout)
        return root
    }

    interface TreatmentController {
        fun setTreatment(blur: Float, hue: Float, strength: Float)
        fun setEnabled(enabled: Boolean)
    }

    private fun buildVerticalSlider(
        context: Context,
        density: Float,
        labelText: String,
        tokens: NexusColorTokens
    ): Pair<View, Slider> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val label = TextView(context).apply {
            text = labelText
            NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val sliderWidth = (240 * density).toInt()
        val slider = android.view.LayoutInflater.from(context)
            .inflate(R.layout.nexus_slider, null, false) as Slider

        slider.apply {
            trackHeight = (20 * density).toInt()
            thumbRadius = (10 * density).toInt()
            thumbElevation = 0f
            isTickVisible = false
            setPadding(0, 0, 0, 0)
            valueFrom = 0f
            valueTo = 100f
            value = 0f

            trackActiveTintList = ColorStateList.valueOf(tokens.textPrimary)
            trackInactiveTintList = ColorStateList.valueOf(tokens.surfaceRaised)
            thumbTintList = ColorStateList.valueOf(tokens.textPrimary)

            rotation = 270f
        }

        slider.addOnChangeListener { _, _, fromUser ->
            if (fromUser) {
                slider.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
        }

        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), sliderWidth)
            addView(slider, FrameLayout.LayoutParams(sliderWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            })
        }

        val valueLabel = TextView(context).apply {
            text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(0, context)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * density).toInt()
            }
        }

        slider.addOnChangeListener { _, value, _ ->
            valueLabel.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(value.toInt(), context)
        }

        container.addView(label)
        container.addView(frame)
        container.addView(valueLabel)

        return Pair(container, slider)
    }
}
