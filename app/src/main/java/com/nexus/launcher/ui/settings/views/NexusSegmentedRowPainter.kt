package com.nexus.launcher.ui.settings.views

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Paints a [NexusSegmentedRow]'s label, subtitle, badge and card in the current theme.
 *
 * Split out of that view to keep it inside the file-size limit; it holds no state of its own —
 * the row passes in what it has.
 */
internal object NexusSegmentedRowPainter {

    fun applyTokens(
        tokens: NexusColorTokens,
        isEInk: Boolean,
        dp: Float,
        iconView: ImageView,
        labelView: TextView,
        isLabelSubdued: Boolean,
        subtitleView: TextView,
        badgeView: TextView,
        containerBackground: GradientDrawable,
    ) {
        iconView.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        labelView.setTextColor(if (isLabelSubdued) tokens.textSecondary else tokens.textPrimary)
        labelView.typeface = if (isEInk) Typeface.MONOSPACE else null
        subtitleView.setTextColor(tokens.textSecondary)
        subtitleView.typeface = if (isEInk) Typeface.MONOSPACE else null
        badgeView.background = GradientDrawable().apply {
            cornerRadius = if (isEInk) 0f else 3 * dp
            setColor((tokens.textPrimary and 0x00FFFFFF) or (0x24 shl 24))
        }
        badgeView.setTextColor(tokens.textPrimary)
        badgeView.typeface = if (isEInk) Typeface.MONOSPACE else null
        containerBackground.setColor(tokens.surfaceRaised)
        containerBackground.cornerRadius = if (isEInk) 0f else 16 * dp
        if (isEInk) {
            containerBackground.setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        } else {
            containerBackground.setStroke(0, 0)
        }
    }
}
