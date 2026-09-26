package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Handles context menu card background, rows, and dividers across the launcher,
 * strictly maintaining monochrome paper styling in E-Ink mode and frosted theme tokens otherwise.
 */
object NexusContextMenuDesignHelper {

    /**
     * The theme's own tokens. Context menus belong to the home screen, not to the Feed, so E-Ink
     * Paper Mode — a Feed and reader setting — does not reach them.
     */
    fun resolveTokens(context: Context): NexusColorTokens {
        return try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    }

    fun applyContextMenuCardBackground(card: View, context: Context, accentArgb: Int = 0) {
        val density = context.resources.displayMetrics.density
        val isEInk = false
        val tokens = resolveTokens(context)

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (isEInk) {
                setColor(tokens.surface)
                cornerRadius = 0f
                setStroke((1f * density).toInt().coerceAtLeast(1), tokens.divider)
            } else {
                val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 16f * density
                setStroke(
                    (1f * density).toInt().coerceAtLeast(1),
                    frostedTokens.border
                )
            }
        }
        card.background = background
        card.clipToOutline = true
        card.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    fun buildContextMenuRow(
        context: Context,
        label: String,
        iconResId: Int? = null,
        isDestructive: Boolean = false,
        accentArgb: Int = 0,
        iconDrawable: Drawable? = null,
        onClick: () -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val isEInk = false
        val tokens = resolveTokens(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (com.nexus.launcher.ui.ContextMenuMetrics.ROW_HEIGHT_DP * density).toInt()
            )
            val paddingH = (com.nexus.launcher.ui.ContextMenuMetrics.ROW_PADDING_H_DP * density).toInt()
            setPadding(paddingH, 0, paddingH, 0)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
        }

        if (iconResId != null || iconDrawable != null) {
            val iconSize = (com.nexus.launcher.ui.ContextMenuMetrics.ICON_DP * density).toInt()
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                if (iconResId != null) {
                    setImageDrawable(ContextCompat.getDrawable(context, iconResId))
                } else {
                    setImageDrawable(iconDrawable)
                }
                val tint = if (isDestructive) tokens.danger else tokens.textSecondary
                imageTintList = ColorStateList.valueOf(tint)
            }
            container.addView(imageView)
        }

        val textView = TextView(context).apply {
            text = label
            val color = if (isDestructive) tokens.danger else tokens.textPrimary
            typeface = if (isEInk) Typeface.MONOSPACE else null
            NexusTypeScale.menuItem.bindTo(this, color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (iconResId != null || iconDrawable != null) {
                    marginStart = (com.nexus.launcher.ui.ContextMenuMetrics.ICON_TEXT_GAP_DP * density).toInt()
                }
            }
        }
        container.addView(textView)

        return container
    }

    fun buildContextMenuDivider(context: Context): View {
        val density = context.resources.displayMetrics.density
        val tokens = resolveTokens(context)
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * density).toInt().coerceAtLeast(1)
            )
            setBackgroundColor(tokens.divider)
        }
    }
}
