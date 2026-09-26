package com.nexus.launcher.ui.drawercategories

data class CategoryApp(
    val label: String,
    val color: Int,
    val packageName: String = "",
    val className: String = "",
    val icon: android.graphics.drawable.Drawable? = null,
    val shortcutId: String? = null,
    val folderId: Long? = null,
    val categoryName: String? = null,
)
