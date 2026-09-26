package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.nexus.launcher.ui.NexusDesignSystem

/**
 * Dark charcoal glass for folder sheets — same dim aesthetic as the prior UI
 * (#CC0D1117 family), slightly open so live workspace blur can still read through.
 */
object FolderPremiumGlassBuilder {

    // Was 0x2E (blue wash). Restored near original frosted-card opacity.
    private const val FILL_ALPHA = 0xB8 // ~72% base — dim, not blue-tinted
    private const val CORNER_DP = 28f

    fun sheetPanel(context: Context, roundTopOnly: Boolean = true): Drawable {
        val density = context.resources.displayMetrics.density
        val cornerPx = CORNER_DP * density
        val base = Color.parseColor(NexusDesignSystem.COLOR_BASE)
        val fill = Color.argb(
            FILL_ALPHA,
            Color.red(base),
            Color.green(base),
            Color.blue(base)
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadii = cornerRadii(cornerPx, roundTopOnly)
            setStroke(
                (1 * density).toInt().coerceAtLeast(1),
                Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
            )
        }
    }

    private fun cornerRadii(cornerPx: Float, roundTopOnly: Boolean): FloatArray {
        return if (roundTopOnly) {
            floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx, 0f, 0f, 0f, 0f)
        } else {
            floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx, cornerPx, cornerPx, cornerPx, cornerPx)
        }
    }
}
