@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/** Segmented type chips tab strip for Wallpaper Sheet. */
object WallpaperTypeChips {
    /**
     * Was hardcoded false ("TEMP for testing"), which hid the entire System chip — and with it,
     * the only entry point to WallpaperFrostCaptureTab (the one-time clean-wallpaper capture
     * needed for frost to work on an external/live wallpaper). Restored.
     */
    private const val SHOW_SYSTEM_CHIP = true

    fun build(
        context: Context,
        container: LinearLayout,
        density: Float,
        selected: String,
        tokens: NexusColorTokens,
        onTypeSelected: (String) -> Unit
    ) {
        container.removeAllViews()
        val types = listOf(
            "system" to context.getString(com.nexus.launcher.R.string.wallpaper_type_system), "gradient" to context.getString(com.nexus.launcher.R.string.wallpaper_type_gradient),
            "gallery" to context.getString(com.nexus.launcher.R.string.edit_sheet_gallery), "others" to context.getString(com.nexus.launcher.R.string.wallpaper_type_others)
        ).filter { (type, _) -> SHOW_SYSTEM_CHIP || type != "system" }
        types.forEach { (type, label) ->
            val isSelected = type == selected
            val chip = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setPaddingRelative(0, (10 * density).toInt(), 0, (10 * density).toInt())
                background = if (isSelected) GradientDrawable().apply {
                    setColor(tokens.surfaceRaised)
                    cornerRadius = 20f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
                } else null
                if (isSelected) {
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                } else {
                    NexusTypeScale.body.bindTo(this, tokens.textSecondary)
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                setOnClickListener {
                    it.performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    onTypeSelected(type)
                }
            }
            container.addView(chip)
        }
    }
}
