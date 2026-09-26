package com.nexus.launcher.ui.model

import android.content.Intent
import android.graphics.drawable.Drawable

data class DisplayItem(
    val label: String,
    val icon: Drawable?,
    val intent: Intent?,
    val categoryName: String? = null
)
