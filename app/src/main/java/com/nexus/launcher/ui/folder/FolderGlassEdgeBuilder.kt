package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import com.nexus.launcher.R

/** Bevel, rim, specular, and depth edge overlays for premium glass folders. */
object FolderGlassEdgeBuilder {

    private const val CORNER_DP = 20f

    fun cornerRadiusPx(context: Context): Float =
        CORNER_DP * context.resources.displayMetrics.density

    /** Softer overlays for open folder card so frosted gradient stays visible. */
    fun cardOverlayLayers(context: Context, isExpressive: Boolean = true): Array<Drawable> {
        val density = context.resources.displayMetrics.density
        val cornerPx = CORNER_DP * density

        val bevelTop = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            colors = intArrayOf(Color.argb(20, 255, 255, 255), Color.argb(0, 255, 255, 255))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        return arrayOf(bevelTop)
    }

    fun overlayLayers(context: Context): Array<Drawable> {
        val density = context.resources.displayMetrics.density
        val cornerPx = CORNER_DP * density
        val accentArgb = Color.argb(0x32, 255, 255, 255)
        val tintArgb = accentArgb

        val innerGlow = ContextCompat.getDrawable(context, R.drawable.bg_folder_inner_highlight)?.mutate()
        val bevelTop = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            colors = intArrayOf(Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        val specular = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            colors = intArrayOf(Color.argb(55, 255, 255, 255), Color.argb(0, 255, 255, 255))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        }
        val accentDrawable = ColorDrawable(tintArgb)
        return arrayOf(
            innerGlow ?: ColorDrawable(Color.TRANSPARENT),
            bevelTop,
            specular,
            accentDrawable
        )
    }

    fun edge3dLayers(context: Context): Array<Drawable> {
        val density = context.resources.displayMetrics.density
        val cornerPx = CORNER_DP * density
        val strokeW = (1.5f * density).toInt().coerceAtLeast(1)

        val bottomDepth = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            colors = intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(60, 0, 0, 0))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        val sideSheen = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            colors = intArrayOf(
                Color.argb(24, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Color.argb(36, 0, 0, 0)
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        }
        val topRim = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setStroke(strokeW, Color.argb(80, 255, 255, 255))
            setColor(Color.TRANSPARENT)
        }
        val innerRim = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx * 0.92f
            setStroke((0.75f * density).toInt().coerceAtLeast(1), Color.argb(30, 0, 0, 0))
            setColor(Color.TRANSPARENT)
        }
        val outerStroke = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setColor(Color.TRANSPARENT)
            setStroke(
                (1.25f * density).toInt().coerceAtLeast(1),
                ContextCompat.getColor(context, R.color.nexus_glass_border)
            )
        }
        return arrayOf(bottomDepth, sideSheen, innerRim, topRim, outerStroke)
    }
}
