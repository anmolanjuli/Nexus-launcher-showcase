package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.data.FolderConfig

/** Resolves folder icon fill from widget-aligned plate colors + opacity slider. */
object FolderIconBallColor {

    fun resolve(context: Context, config: FolderConfig): Int {
        val rgb = FolderIconSurfaceColor.plate(context, config).fill
        val alpha = (config.backgroundOpacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(rgb, alpha)
    }

    /** Same-hue shade that preserves the resolved opacity. */
    fun shade(base: Int, toward: Int, amount: Float): Int {
        val opaque = ColorUtils.setAlphaComponent(base, 255)
        val blended = ColorUtils.blendARGB(opaque, toward, amount)
        return ColorUtils.setAlphaComponent(blended, Color.alpha(base))
    }
}
