package com.nexus.launcher.ui.drawercategories

import android.content.Context
import com.nexus.launcher.ui.DrawerCategories

object CategoriesFolderTag {
    var currentCategoryId: Int? = null

    fun tagCreated(context: Context?, folderId: Long?) {
        val id = folderId ?: return
        val catId = currentCategoryId ?: return
        if (catId <= 0 || context == null) return
        val prefs = context.getSharedPreferences(DrawerCategories.PREFS, Context.MODE_PRIVATE)
        val key = id.toString()
        val keys = (prefs.getStringSet("folder_category_keys", emptySet()) ?: emptySet()).toMutableSet()
        keys.add(key)
        prefs.edit()
            .putInt("folder_category_$key", catId)
            .putStringSet("folder_category_keys", keys)
            .apply()
    }
}
