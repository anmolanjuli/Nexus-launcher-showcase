package com.nexus.launcher.ui.drawercategories


data class CategoriesDrawerModel(
    val categories: List<CategoryGroup>,
    val untaggedFolders: List<CategoryApp>,
)
