package com.nexus.launcher.feed

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Styling engine for E-Ink Paper Mode in Nexus Feed.
 *
 * Implements paper physics:
 * - Headlines: Bold serif font with 1.2x line spacing and tightened letter spacing.
 * - Body: Regular serif font with 1.6x line spacing.
 * - Metadata: Monospace uppercase font with 0.05em letter spacing.
 * - Flat paper surfaces with sharp square edges (0dp corner radius), no shadows, and 1dp divider borders.
 * - Grayscale image filtering with sharp corners and a subtle 10% opacity ink border.
 */
object NexusFeedEInkStyler {

    val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val serifRegular: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    val monospaceFont: Typeface = Typeface.MONOSPACE

    fun applyHeadline(textView: TextView, textColor: Int, isEInk: Boolean) {
        if (isEInk) {
            textView.typeface = serifBold
            textView.setTextColor(textColor)
            textView.setLineSpacing(0f, 1.2f)
            textView.letterSpacing = -0.01f
        } else {
            textView.typeface = Typeface.DEFAULT
            textView.setLineSpacing(0f, 1.15f)
            textView.letterSpacing = 0f
            NexusTypeScale.bodyStrong.bindTo(textView, textColor)
        }
    }

    fun applyHeroTitle(textView: TextView, textColor: Int, isEInk: Boolean) {
        if (isEInk) {
            textView.typeface = serifBold
            textView.setTextColor(textColor)
            textView.setLineSpacing(0f, 1.2f)
            textView.letterSpacing = -0.01f
        } else {
            textView.typeface = Typeface.DEFAULT
            textView.setLineSpacing(0f, 1.15f)
            textView.letterSpacing = 0f
            NexusTypeScale.title.bindTo(textView, textColor)
        }
    }

    fun applyBody(textView: TextView, textColor: Int, isEInk: Boolean) {
        if (isEInk) {
            textView.typeface = serifRegular
            textView.setTextColor(textColor)
            textView.setLineSpacing(0f, 1.6f)
            textView.letterSpacing = -0.01f
        } else {
            textView.typeface = Typeface.DEFAULT
            textView.setLineSpacing(0f, 1.0f)
            textView.letterSpacing = 0f
            NexusTypeScale.body.bindTo(textView, textColor)
        }
    }

    fun applyMeta(textView: TextView, textColor: Int, isEInk: Boolean) {
        if (isEInk) {
            textView.typeface = monospaceFont
            textView.setTextColor(textColor)
            textView.letterSpacing = 0.05f
        } else {
            textView.typeface = Typeface.DEFAULT
            textView.letterSpacing = 0.03f
            NexusTypeScale.caption.bindTo(textView, textColor)
        }
    }

    fun applyMonogram(textView: TextView, textColor: Int, surfaceColor: Int, isEInk: Boolean, dp: Float) {
        if (isEInk) {
            textView.typeface = monospaceFont
            textView.setTextColor(textColor)
            textView.background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = 0f // Sharp square monogram in E-Ink
                setStroke((1 * dp).toInt().coerceAtLeast(1), textColor and 0x33FFFFFF)
            }
        } else {
            textView.typeface = Typeface.DEFAULT
            NexusTypeScale.labelSmall.bindTo(textView, textColor)
            textView.background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = 9999f
            }
        }
    }

    fun createCardBackground(
        isEInk: Boolean,
        isDark: Boolean,
        tokens: NexusColorTokens,
        dp: Float,
        standardCornerRadiusDp: Float = 24f
    ): GradientDrawable {
        return GradientDrawable().apply {
            if (isEInk) {
                val bg = if (isDark) NexusFeedEInkCoordinator.COLOR_DARK_BG else NexusFeedEInkCoordinator.COLOR_LIGHT_BG
                val divider = if (isDark) NexusFeedEInkCoordinator.COLOR_DARK_DIVIDER else NexusFeedEInkCoordinator.COLOR_LIGHT_DIVIDER
                setColor(bg)
                cornerRadius = 0f // Paper physics: sharp square corners
                setStroke((1 * dp).toInt().coerceAtLeast(1), divider)
            } else {
                setColor(tokens.surface)
                cornerRadius = standardCornerRadiusDp * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }

    fun createImageContainerBackground(
        isEInk: Boolean,
        isDark: Boolean,
        fallbackColor: Int,
        dp: Float,
        standardCornerRadiusDp: Float = 16f
    ): GradientDrawable {
        return GradientDrawable().apply {
            if (isEInk) {
                val strokeColor = if (isDark) {
                    NexusFeedEInkCoordinator.COLOR_DARK_IMAGE_BORDER
                } else {
                    NexusFeedEInkCoordinator.COLOR_LIGHT_IMAGE_BORDER
                }
                setColor(if (isDark) NexusFeedEInkCoordinator.COLOR_DARK_SURFACE_RAISED else NexusFeedEInkCoordinator.COLOR_LIGHT_SURFACE_RAISED)
                cornerRadius = 0f // Square corners
                setStroke((1 * dp).toInt().coerceAtLeast(1), strokeColor)
            } else {
                setColor(fallbackColor)
                cornerRadius = standardCornerRadiusDp * dp
            }
        }
    }

    fun applyImageGrayscale(imageView: ImageView, isEInk: Boolean) {
        if (isEInk) {
            imageView.colorFilter = NexusFeedEInkCoordinator.grayscaleColorFilter
        } else {
            imageView.colorFilter = null
        }
    }
}
