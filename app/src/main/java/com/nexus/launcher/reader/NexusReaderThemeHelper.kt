package com.nexus.launcher.reader

import android.app.Activity
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nexus.launcher.feed.NexusFeedEInkCoordinator
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Handles reader theme resolution, background gradients, E-Ink paper palette, and window system bars.
 */
object NexusReaderThemeHelper {

    enum class ReaderDisplayMode {
        READER,
        ORIGINAL,
        EINK
    }

    data class ReaderPalette(
        val bg: Int,
        val bgTop: Int? = null,
        val bgBottom: Int? = null,
        val surface: Int,
        val surfaceRaised: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val divider: Int,
        val isEInk: Boolean,
        val isDarkPaper: Boolean = false
    )

    fun resolvePalette(
        activity: Activity,
        tokens: NexusColorTokens,
        forcedEInk: Boolean
    ): ReaderPalette {
        val isEInk = forcedEInk || NexusFeedEInkCoordinator.isEInkMode(activity)
        if (isEInk) {
            val isDark = NexusFeedEInkCoordinator.isEInkDark(activity)
            return if (isDark) {
                ReaderPalette(
                    bg = 0xFF1C1C1C.toInt(),
                    surface = 0xFF1C1C1C.toInt(),
                    surfaceRaised = 0xFF2A2A2A.toInt(),
                    textPrimary = 0xFFE8E5DF.toInt(),
                    textSecondary = 0xFF8A8782.toInt(),
                    divider = 0xFF2A2A2A.toInt(),
                    isEInk = true,
                    isDarkPaper = true
                )
            } else {
                ReaderPalette(
                    bg = 0xFFFAF9F6.toInt(),
                    surface = 0xFFFAF9F6.toInt(),
                    surfaceRaised = 0xFFF0EFEA.toInt(),
                    textPrimary = 0xFF2A2A2A.toInt(),
                    textSecondary = 0xFF7A7A7A.toInt(),
                    divider = 0xFFE0DDD8.toInt(),
                    isEInk = true,
                    isDarkPaper = false
                )
            }
        }

        return if (tokens.bgTop != null && tokens.bgBottom != null) {
            ReaderPalette(
                bg = tokens.bg,
                bgTop = tokens.bgTop,
                bgBottom = tokens.bgBottom,
                surface = tokens.surface,
                surfaceRaised = tokens.surfaceRaised,
                textPrimary = tokens.textPrimary,
                textSecondary = tokens.textSecondary,
                divider = tokens.divider,
                isEInk = false
            )
        } else {
            ReaderPalette(
                bg = tokens.bg,
                surface = tokens.surface,
                surfaceRaised = tokens.surfaceRaised,
                textPrimary = tokens.textPrimary,
                textSecondary = tokens.textSecondary,
                divider = tokens.divider,
                isEInk = false
            )
        }
    }

    fun applyBackground(view: View, palette: ReaderPalette) {
        if (palette.isEInk) {
            view.background = ColorDrawable(palette.bg)
            return
        }
        if (palette.bgTop != null && palette.bgBottom != null) {
            view.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette.bgTop, palette.bgBottom)
            )
        } else {
            view.background = ColorDrawable(palette.bg)
        }
    }

    @Suppress("DEPRECATION")
    fun applyWindowChrome(activity: Activity, window: Window, palette: ReaderPalette) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val insets = WindowInsetsControllerCompat(window, window.decorView)
        val isLightBg = isColorLight(palette.bg)
        insets.isAppearanceLightStatusBars = isLightBg
        insets.isAppearanceLightNavigationBars = isLightBg
    }

    /**
     * Drops any layer on the reader's root. E-Ink used to desaturate the whole reader through a
     * full-screen hardware layer, re-rendered in full on every frame of a scroll. Each thing that
     * can carry colour now handles its own: the palette for text and chrome, a grayscale filter on
     * article images and PDF pages, and grayscale CSS inside EPUB chapters.
     */
    fun applyEInkSaturationLayer(view: View, @Suppress("UNUSED_PARAMETER") isEInk: Boolean) {
        if (view.layerType != View.LAYER_TYPE_NONE) view.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun isColorLight(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.5
    }
}
