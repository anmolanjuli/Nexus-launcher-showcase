package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Display rotation and label color helpers for [LauncherCanvasView].
 * Extracted to maintain file line count invariants.
 */
object CanvasDisplayHelper {

    fun getDisplayRotation(view: android.view.View): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    }

    fun resolveHomeLabelColor(
        context: Context,
        @Suppress("UNUSED_PARAMETER") wallpaperLuminance: Float,
        backgroundLayerMode: com.nexus.launcher.theme.BackgroundLayerMode = com.nexus.launcher.theme.BackgroundLayerMode.WALLPAPER,
        @Suppress("UNUSED_PARAMETER") wallpaperType: String? = null
    ): Int {
        if (backgroundLayerMode == com.nexus.launcher.theme.BackgroundLayerMode.WALLPAPER) {
            // An arbitrary photo sits behind the icons, so white plus the shadow layer
            // HomeScreenRenderer adds for light labels is the only reliably legible choice.
            return Color.WHITE
        }

        // THEME_COLOR: CanvasRenderer paints the theme background and returns before any
        // wallpaper is drawn, so nothing about the wallpaper describes what is actually behind
        // these labels. Both wallpaper inputs are therefore ignored here — consulting them was
        // the bug:
        //   - wallpaperType "system"/"gallery" used to force WHITE through the branch above even
        //     in THEME_COLOR mode, which left light-theme labels nearly invisible on the light
        //     theme background. A leftover type from before the user switched modes was enough
        //     to trigger it.
        //   - wallpaperLuminance drove the same fault in the opposite direction: a bright but
        //     no-longer-drawn wallpaper picked dark labels for a dark theme.
        // The theme's own foreground against the theme's own background is exactly what
        // textPrimary is defined to be, in every token set.
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        return tokens.textPrimary
    }
}
