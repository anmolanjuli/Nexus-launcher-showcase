package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem

object FolderEditIconCache {

    fun load(context: Context, items: List<HomeScreenItem>): Map<String, Drawable> {
        val pm = context.packageManager
        val map = LinkedHashMap<String, Drawable>()
        for (item in items) {
            if (map.containsKey(item.packageName)) continue
            try {
                val iconResolver = dagger.hilt.android.EntryPointAccessors.fromApplication(context, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java).iconResolver()
                val icon = iconResolver.getIcon(item.packageName) ?: pm.defaultActivityIcon
                map[item.packageName] = icon.mutate()
            } catch (_: Exception) {
                // skip missing packages
            }
        }
        return map
    }
}
