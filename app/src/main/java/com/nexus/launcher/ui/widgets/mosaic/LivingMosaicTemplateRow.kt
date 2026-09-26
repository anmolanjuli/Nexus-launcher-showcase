package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/** Horizontal selector for Living Mosaic fixed collage templates with dynamic tokens. */
class LivingMosaicTemplateRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    var onValueChanged: ((String?) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val track = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = (2 * dp).toInt()
        setPadding(pad, pad, pad, pad)
    }

    private val tiles = mutableMapOf<String?, LinearLayout>()
    private var selectedValue: String? = null
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(track, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        val vPad = (8 * dp).toInt()
        setPadding(0, vPad, 0, vPad)

        addTemplateTile(null, context.getString(com.nexus.launcher.R.string.mosaic_tpl_freeform))
        addTemplateTile("hero_left_2_stacked", context.getString(com.nexus.launcher.R.string.mosaic_tpl_hero_l2))
        addTemplateTile("even_2x2", context.getString(com.nexus.launcher.R.string.mosaic_tpl_even_2x2))
        addTemplateTile("hero_top_3_row", context.getString(com.nexus.launcher.R.string.mosaic_tpl_hero_t3))
        addTemplateTile("3_even_columns", context.getString(com.nexus.launcher.R.string.mosaic_tpl_3_cols))
        addTemplateTile("hero_left_2_cols_stacked", context.getString(com.nexus.launcher.R.string.mosaic_tpl_hero_l4))
        addTemplateTile("3_even_rows", context.getString(com.nexus.launcher.R.string.mosaic_tpl_3_rows))
        addTemplateTile("even_3x3", context.getString(com.nexus.launcher.R.string.mosaic_tpl_even_3x3))
    }

    private fun addTemplateTile(id: String?, label: String) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padH = (12 * dp).toInt()
            val padV = (8 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = 10f * dp
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { setValue(id, userInitiated = true) }
        }

        val textView = TextView(context).apply {
            text = label
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
        }

        container.addView(textView)
        track.addView(container)
        tiles[id] = container
    }

    fun setValue(id: String?, userInitiated: Boolean = false) {
        if (selectedValue == id && userInitiated) return
        selectedValue = id

        tiles.forEach { (key, view) ->
            val bg = view.background as GradientDrawable
            val label = view.getChildAt(0) as? TextView
            if (key == id) {
                bg.setColor(tokens.surfaceRaised)
                bg.setStroke((1.5f * dp).toInt().coerceAtLeast(1), tokens.textPrimary)
                label?.let { NexusTypeScale.caption.bindTo(it, tokens.textPrimary) }
            } else {
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke(0, Color.TRANSPARENT)
                label?.let { NexusTypeScale.caption.bindTo(it, tokens.textSecondary) }
            }
        }

        if (userInitiated) {
            onValueChanged?.invoke(id)
        }
    }
}
