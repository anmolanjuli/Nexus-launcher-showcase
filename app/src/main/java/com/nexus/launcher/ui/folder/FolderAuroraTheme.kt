package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/** Aurora Glass color tokens and drawable helpers for folder UI. */
object FolderAuroraTheme {

    const val BASE_BG = "#0D1117"
    const val GLASS_SURFACE = "#1AFFFFFF"
    const val GLASS_BORDER = "#33FFFFFF"
    const val ACCENT = "#7EB8D4"
    const val TEXT_PRIMARY = "#FFFFFF"
    const val TEXT_SECONDARY = "#99FFFFFF"
    const val REMOVE_TINT = "#FF6B6B"
    const val WINDOW_BASE = "#CC0D1117"
    const val MENU_BASE = "#E60D1117"
    const val DIALOG_BASE = "#F00D1117"
    const val SHEET_BASE = "#F00D1117"

    fun glassCard(context: Context, fillArgb: Int, cornerDp: Float, strokeDp: Float = 1f): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(fillArgb)
            cornerRadius = cornerDp * dp
            setStroke((strokeDp * dp).toInt().coerceAtLeast(1), Color.parseColor(GLASS_BORDER))
        }
    }

    fun frostedGradient(context: Context, startRgb: Int, endRgb: Int, cornerDp: Float): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        val alpha = 0xD9
        val start = (alpha shl 24) or (startRgb and 0x00FFFFFF)
        val end = (alpha shl 24) or (endRgb and 0x00FFFFFF)
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = cornerDp * dp
            setStroke((1f * dp).toInt().coerceAtLeast(1), Color.parseColor(GLASS_BORDER))
        }
    }
}
