package com.nexus.launcher.ui

import android.content.Context

/**
 * Single source of truth for Manage Pages thumbnail / phone-frame aspect.
 * Keep card onMeasure and thumbnail generation on the device's real screen
 * proportions so thumbnails scale 1:1 with zero compression / squashing.
 */
object ManagePagesThumbSpec {
    const val WIDTH_DP = 160f
    const val HEIGHT_DP = 346f

    fun heightOverWidth(context: Context): Float {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(1f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(1f)
        // 20% less tall for balanced presence in manage pages grid
        return (h / w) * 0.80f
    }

    /** height = width * this (default device aspect fallback, 20% less tall) */
    val HEIGHT_OVER_WIDTH: Float = (HEIGHT_DP / WIDTH_DP) * 0.80f
}
