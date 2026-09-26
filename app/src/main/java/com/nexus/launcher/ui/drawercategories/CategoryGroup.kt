package com.nexus.launcher.ui.drawercategories

data class CategoryGroup(
    val name: String,
    val subtitle: String,
    var accent: Int,
    val apps: List<CategoryApp>,
    val quickActions: List<CategoryApp>,
    val recents: List<CategoryApp>,
    val id: Int = 0,
    val key: String = "",
    val badgeIcon: Int = 0,
)
