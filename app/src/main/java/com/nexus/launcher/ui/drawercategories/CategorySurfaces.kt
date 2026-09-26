package com.nexus.launcher.ui.drawercategories

import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.ui.glass.FrostedGlassEngine

object CategorySurfaces {
    fun card(palette: CategoryPalette, density: Float, radiusDp: Float): GradientDrawable {
        return plate(palette, density, radiusDp, strokeDp = if (palette.isFlat) 1f else 1.5f)
    }

    fun spatialCard(palette: CategoryPalette, density: Float): GradientDrawable {
        return plate(palette, density, radiusDp = 28f, strokeDp = 1.5f)
    }

    fun footer(palette: CategoryPalette, density: Float): GradientDrawable {
        val fill = ColorUtils.setAlphaComponent(palette.surface, if (palette.isLight) 0xE6 else 0xE8)
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 16f * density
        }
    }

    fun nested(
        palette: CategoryPalette,
        density: Float,
        radiusDp: Float,
    ): GradientDrawable? {
        if (palette.isGlass) return null
        return plate(palette, density, radiusDp, strokeDp = 1f)
    }

    fun chip(palette: CategoryPalette, density: Float, selected: Boolean): GradientDrawable {
        return categoryChip(palette, density, selected)
    }

    fun categoryChip(palette: CategoryPalette, density: Float, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 20f * density
            if (selected) {
                setColor(palette.mistBlue)
            } else {
                setColor(if (palette.isGlass) palette.glassSurface else palette.surface)
                setStroke((1f * density).toInt().coerceAtLeast(1), palette.glassBorder)
            }
        }
    }

    private fun plate(
        palette: CategoryPalette,
        density: Float,
        radiusDp: Float,
        strokeDp: Float,
    ): GradientDrawable {
        val fill = if (palette.isGlass) {
            val alpha = (FrostedGlassEngine.sheetFillAlpha() * 255f).toInt().coerceIn(0, 255)
            ColorUtils.setAlphaComponent(palette.surface, alpha)
        } else {
            palette.surface
        }
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusDp * density
            setStroke((strokeDp * density).toInt().coerceAtLeast(1), palette.glassBorder)
        }
    }

    fun swatch(accent: Int, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(accent)
            cornerRadius = 8f * density
        }
    }

    fun colorSwatch(color: Int, density: Float, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 20f * density
            if (selected) {
                setStroke((2f * density).toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(color, 255))
            }
        }
    }
}
