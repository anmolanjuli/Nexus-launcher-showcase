package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Single-style carousel view displaying the default "Nexus" style card.
 * Used in edit sheets for widgets that offer the unified Nexus style (e.g. Calendar, Agenda, Notes, Search).
 */
class NexusGenericStyleCarouselView(
    context: Context,
    private val tokens: NexusColorTokens,
    var latestConfig: NexusWidgetConfig.InstanceConfig,
    private val providerClassName: String? = null
) : HorizontalScrollView(context) {

    private val dp = resources.displayMetrics.density
    val previewView: ImageView
    private val cardView: LinearLayout

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        clipChildren = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (12 * dp).toInt()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val cardW = (230 * dp).toInt()
        val cardH = (110 * dp).toInt()

        cardView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = (8 * dp).toInt()
                rightMargin = (8 * dp).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 16f * dp
                setColor(tokens.surfaceRaised)
                setStroke((2 * dp).toInt().coerceAtLeast(2), tokens.accent)
            }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        previewView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(cardW - (24 * dp).toInt(), cardH).apply {
                bottomMargin = (8 * dp).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = context.getString(R.string.widget_style_nexus)
        }
        cardView.addView(previewView)

        val titleView = TextView(context).apply {
            text = context.getString(R.string.widget_style_nexus)
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
        }
        cardView.addView(titleView)

        container.addView(cardView)
        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun updateLiveConfig(config: NexusWidgetConfig.InstanceConfig) {
        latestConfig = config
    }
}
