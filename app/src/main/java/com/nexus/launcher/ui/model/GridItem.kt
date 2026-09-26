package com.nexus.launcher.ui.model

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable

data class GridItem(
    val label: String,
    val icon: Drawable?,
    val intent: Intent?,
    val drawRect: Rect,
    val categoryName: String? = null,
    // Tap target — defaults to the icon bounds; list layout widens it to the
    // full row so taps on the label area also hit
    val hitRect: Rect = drawRect
)
