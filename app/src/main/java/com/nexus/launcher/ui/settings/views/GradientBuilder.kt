package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class GradientBuilder @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dp = context.resources.displayMetrics.density

    var onGradientChanged: ((start: String, end: String, direction: String) -> Unit)? = null

    private var currentStartColor = ""
    private var currentAccentColor = "#7EB8D4"

    data class GradientPreset(
        val name: String,
        val start: String,
        val end: String,
        val mid: String? = null
    )

    private val presets = listOf(
        GradientPreset("Winter Woods", "#A43931", "#333333", "#a2ab58"),
        GradientPreset("Peach Sea", "#A8CECF", "#E6AE8C"),
        GradientPreset("Emerald Sea", "#5cdb95", "#05386b"),
        GradientPreset("Blue Red", "#960B33", "#36B1C7"),
        GradientPreset("Mango Papaya", "#2ada53", "#de8a41"),
        GradientPreset("Witching Hour", "#240b36", "#c31432"),
        GradientPreset("Ultra Violet", "#eaafc8", "#654ea3"),
        GradientPreset("Citrus Peel", "#F37335", "#FDC830"),
        GradientPreset("eXpresso", "#3c1053", "#ad5389"),
        GradientPreset("Moon Purple", "#8f94fb", "#4e54c8"),
        GradientPreset("King Yna", "#fdbb2d", "#1a2a6c", "#b21f1f"),
        GradientPreset("Crimson Tide", "#C6426E", "#642B73"),
        GradientPreset("Mello", "#8e44ad", "#c0392b"),
        GradientPreset("Dawn", "#3B4371", "#F3904F"),
        GradientPreset("Dusk", "#FD746C", "#2C3E50"),
        GradientPreset("Sunset", "#F56217", "#0B486B"),
        GradientPreset("Purple Bliss", "#0b8793", "#360033"),
        GradientPreset("Influenza", "#480048", "#C04848"),
        GradientPreset("Peach", "#FFEDBC", "#ED4264"),
        GradientPreset("Steel Gray", "#928DAB", "#1F1C2C"),
        GradientPreset("Kashmir", "#516395", "#614385"),
        GradientPreset("Emerald Water", "#56B4D3", "#348F50"),
        GradientPreset("Nimvelo", "#26a0da", "#314755"),
        GradientPreset("Blue and Orange", "#0085CA", "#FD8112")
    )

    private val cardViews = mutableListOf<LinearLayout>()

    init {
        orientation = VERTICAL
        val rows = presets.chunked(2)
        rows.forEach { rowPresets ->
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * dp).toInt() }
            }
            rowPresets.forEach { preset ->
                val card = buildCard(preset)
                card.layoutParams = LayoutParams(
                    0, (90 * dp).toInt(), 1f
                ).apply { marginEnd = (8 * dp).toInt() }
                cardViews.add(card)
                rowLayout.addView(card)
            }
            if (rowPresets.size == 1) {
                rowLayout.addView(android.view.View(context).apply {
                    layoutParams = LayoutParams(0, (90 * dp).toInt(), 1f)
                })
            }
            addView(rowLayout)
        }
    }

    private fun buildCard(preset: GradientPreset): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            val colors = if (preset.mid != null) {
                intArrayOf(
                    Color.parseColor(preset.start),
                    Color.parseColor(preset.mid),
                    Color.parseColor(preset.end))
            } else {
                intArrayOf(
                    Color.parseColor(preset.start),
                    Color.parseColor(preset.end))
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, colors
            ).apply { cornerRadius = 12 * dp }
            clipToOutline = true

            val nameText = TextView(context).apply {
                text = preset.name
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(
                    (8 * dp).toInt(), 0,
                    (4 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT)
                gravity = android.view.Gravity.BOTTOM or
                        android.view.Gravity.START
            }
            addView(nameText)

            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onGradientChanged?.invoke(
                    preset.start, preset.end, "left_right")
            }
        }
    }

    private fun updateSelection() {
        val accentColor = Color.parseColor(currentAccentColor)
        presets.forEachIndexed { i, preset ->
            val card = cardViews[i]
            val isSelected = preset.start.equals(currentStartColor, ignoreCase = true)
            
            val colors = if (preset.mid != null) {
                intArrayOf(Color.parseColor(preset.start), Color.parseColor(preset.mid), Color.parseColor(preset.end))
            } else {
                intArrayOf(Color.parseColor(preset.start), Color.parseColor(preset.end))
            }

            card.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
                cornerRadius = 12 * dp
                if (isSelected) {
                    setStroke((2 * dp).toInt(), accentColor)
                }
            }
        }
    }

    fun configure(start: String, end: String, dir: String, accentColor: String = "#7EB8D4") {
        currentAccentColor = accentColor
        currentStartColor = start
        val match = presets.find { it.start.equals(start, ignoreCase = true) }
        if (match == null && presets.isNotEmpty()) {
            currentStartColor = presets[0].start
        }
        updateSelection()
    }
}
