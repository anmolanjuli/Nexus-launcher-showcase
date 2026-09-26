package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.ui.NexusDesignSystem

object IconEditSheetComponents {
    fun buildButton(
        context: Context,
        text: String,
        dp: Float,
        isMuted: Boolean,
        iconResId: Int,
        onClick: () -> Unit
    ): View {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        return LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * dp).toInt(), (14 * dp).toInt(),
                (16 * dp).toInt(), (14 * dp).toInt()
            )
            val textColor = if (isMuted) tokens.textSecondary else tokens.textPrimary
            background = GradientDrawable().apply {
                if (isMuted) {
                    setColor(Color.TRANSPARENT)
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                } else {
                    setColor(tokens.surfaceRaised)
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                cornerRadius = 14 * dp
            }
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (24 * dp).toInt(), (24 * dp).toInt()
                ).apply { marginEnd = (16 * dp).toInt() }
                setImageResource(iconResId)
                imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                this.text = text
                com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, textColor)
            })
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(50).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
                }.start()
                onClick()
            }
        }
    }
}
