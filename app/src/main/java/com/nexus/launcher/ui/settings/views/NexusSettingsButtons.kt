package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.content.res.ColorStateList

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

private fun hapticTap(view: android.view.View) {
    view.performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
    )
}

/**
 * Shared factory for standardized Reset and Apply buttons matching Settings page design.
 */
object NexusSettingsButtons {

    fun buildResetButton(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        haptic: Boolean = true,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.action_reset)
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textSecondary)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_reset, 0, 0, 0)
            compoundDrawablePadding = (6 * dp).toInt()
            compoundDrawableTintList = ColorStateList.valueOf(tokens.textSecondary)
            // Raised pill in Neumorphism — the sheets' cards are raised, and flat buttons under them
            // read as unfinished. The frosted pill stays the look everywhere else.
            background = if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
                com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 20 * dp)
            } else {
                GradientDrawable().apply {
                    val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                    val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                    setColor((frostedTokens.surfaceRaised and 0x00FFFFFF) or (fillAlpha shl 24))
                    cornerRadius = 20 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
                }
            }
            val padH = (16 * dp).toInt()
            val padV = (8 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            minHeight = (40 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (12 * dp).toInt() }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (haptic) hapticTap(it)
                onClick()
            }
        }
    }

    fun buildApplyButton(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        label: String = context.getString(R.string.action_apply),
        haptic: Boolean = true,
        onClick: () -> Unit
    ): TextView {
        val disabledAlpha = 0x4D
        val disabledText = androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.textPrimary, disabledAlpha)
        val enabledStates = arrayOf(
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled)
        )
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(ColorStateList(enabledStates, intArrayOf(tokens.textPrimary, disabledText)))
            NexusTypeScale.bodyStrong.bindTo(this)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
            compoundDrawablePadding = (6 * dp).toInt()
            compoundDrawableTintList = ColorStateList(enabledStates, intArrayOf(tokens.textPrimary, disabledText))
            // Raised pill in Neumorphism — the sheets' cards are raised, and flat buttons under them
            // read as unfinished. The frosted pill stays the look everywhere else.
            background = if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
                com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 20 * dp)
            } else {
                GradientDrawable().apply {
                    val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                    val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                    setColor((frostedTokens.surfaceRaised and 0x00FFFFFF) or (fillAlpha shl 24))
                    cornerRadius = 20 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
                }
            }
            val padH = (18 * dp).toInt()
            val padV = (8 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            minHeight = (40 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (haptic) hapticTap(it)
                onClick()
            }
        }
    }

    /** Result of [buildFooter] — exposes the buttons so callers can gate Apply on dirty state. */
    class Footer(
        val container: LinearLayout,
        val resetButton: TextView,
        val applyButton: TextView,
    )

    fun buildFooter(
        context: Context,
        dp: Float,
        onReset: () -> Unit,
        onApply: () -> Unit
    ): Footer {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val resetButton = buildResetButton(context, tokens, dp, onClick = onReset)
        val applyButton = buildApplyButton(context, tokens, dp, onClick = onApply)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val padH = (20 * dp).toInt()
            val padV = (12 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            // No separate fill: this footer sits directly on the sheet's own already-frosted
            // panel background — a second opaque strip here broke that continuity and read as
            // a flat, un-frosted bar glued to the bottom of an otherwise glass surface.

            addView(resetButton)
            addView(applyButton)
        }
        return Footer(container, resetButton, applyButton)
    }
}
