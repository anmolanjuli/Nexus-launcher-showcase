package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View

/**
 * Chrome for the wallpaper sheet: a real blurred ambient glow behind the
 * card. Unlike the dock frost (which must be hard-contained), this view is
 * deliberately NOT clipToOutline and NOT bounded by a card-sized hardware
 * layer — the light is supposed to spill softly outward past the card edges.
 */
object WallpaperSheetChrome {

    const val CORNER_DP = 30f

}
