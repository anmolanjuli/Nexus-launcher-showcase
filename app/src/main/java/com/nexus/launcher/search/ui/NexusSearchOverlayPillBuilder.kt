package com.nexus.launcher.search.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FrostedGlassEngine

object NexusSearchOverlayPillBuilder {

    fun frostedFillColor(baseColor: Int): Int {
        val alpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255).toInt()
        return (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }

    fun buildPill(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        searchBar: EditText,
        clearBtn: ImageView,
        onBackClicked: () -> Unit,
        onSettingsClicked: () -> Unit
    ): LinearLayout {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Neumorphism: the field is pressed into the surface, like the search widget's bar.
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.sunkenOr(this, tokens, 24 * dp) {
                GradientDrawable().apply {
                    setColor(frostedFillColor(tokens.surface))
                    cornerRadius = 24 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        val logo = ImageView(context).apply {
            setImageResource(R.drawable.ic_back_arrow)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onBackClicked()
            }
        }
        pill.addView(logo)
        pill.addView(searchBar)
        pill.addView(clearBtn)

        val settingsBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onSettingsClicked()
            }
        }
        pill.addView(settingsBtn)

        return pill
    }

    fun buildChipsRow(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * dp).toInt() }
        }
        fun makeChip(textRes: Int) = TextView(context).apply {
            text = context.getString(textRes)
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(this, tokens, 20 * dp) {
                GradientDrawable().apply {
                    setColor(frostedFillColor(tokens.surfaceRaised))
                    cornerRadius = 20 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * dp).toInt() }
        }
        row.addView(makeChip(R.string.search_unit_hint))
        row.addView(makeChip(R.string.search_maps_hint))
        return row
    }
}
