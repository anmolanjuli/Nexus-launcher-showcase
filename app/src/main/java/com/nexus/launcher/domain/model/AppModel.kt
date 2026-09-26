package com.nexus.launcher.domain.model

import android.graphics.drawable.Drawable

data class AppModel(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable,
    val installTime: Long,
    val categoryId: Int = 0
)
