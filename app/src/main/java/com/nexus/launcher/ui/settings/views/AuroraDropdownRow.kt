package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Label + accent value row that opens an Aurora Glass option card on tap. */
class AuroraDropdownRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val labelView: TextView
    private val valueView: TextView
    private val dp = resources.displayMetrics.density
    private var options: List<String> = emptyList()
    private var selectedIndex = 0
    private var overlayHost: android.view.ViewGroup? = null
    private var accentArgb = Color.parseColor("#7EB8D4")

    var onSelectionChanged: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val vPad = (10 * dp).toInt()
        setPadding(0, vPad, 0, vPad)
        isClickable = true
        isFocusable = true

        labelView = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        valueView = TextView(context).apply {
            textSize = 14f
            setTextColor(accentArgb)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(labelView)
        addView(valueView)
        setOnClickListener { openDropdown() }
    }

    fun attachOverlayHost(host: android.view.ViewGroup) {
        overlayHost = host
    }

    fun configure(label: String, optionLabels: List<String>, initialIndex: Int) {
        labelView.text = label
        options = optionLabels
        selectedIndex = initialIndex.coerceIn(0, (optionLabels.size - 1).coerceAtLeast(0))
        valueView.text = options.getOrElse(selectedIndex) { "" }
    }

    fun selectedIndex(): Int = selectedIndex

    fun applyAccentColor(accentArgb: Int) {
        this.accentArgb = accentArgb
        valueView.setTextColor(accentArgb)
    }

    private fun openDropdown() {
        val host = overlayHost ?: return
        AuroraDropdownOverlay.show(
            host = host,
            anchor = valueView,
            options = options,
            selectedIndex = selectedIndex,
            accentArgb = accentArgb
        ) { index ->
            selectedIndex = index
            valueView.text = options[index]
            onSelectionChanged?.invoke(index)
        }
    }
}
