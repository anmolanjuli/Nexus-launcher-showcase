package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.launch

/**
 * Icon/label cache for home-screen items — maps, cache keys, population, and invalidation.
 * Extracted from [HomeScreenRenderer]; draw logic stays there.
 */
class HomeScreenIconCache(private val context: Context) {
    private val iconResolver = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
    ).iconResolver()

    val iconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()
    val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun overrideLabel(packageName: String, label: String) {
        labelCache[packageName] = label
    }

    fun clearLabelOverride(packageName: String) {
        labelCache.remove(packageName)
    }

    fun clearAllLabels() {
        labelCache.clear()
    }

    fun getCacheKey(item: HomeScreenItem): String {
        return if (item.itemType == 2 && !item.folderConfigJson.isNullOrEmpty()) {
            try {
                val shortcutId = org.json.JSONObject(item.folderConfigJson).optString("shortcutId")
                "shortcut:${item.packageName}:$shortcutId"
            } catch (e: Exception) {
                "shortcut:${item.packageName}:${item.id}"
            }
        } else {
            item.packageName
        }
    }

    /** Pre-populate caches for all items in the list. Safe to call off the main thread. */
    fun updateCache(
        items: List<HomeScreenItem>,
        folderContentsSnapshot: Map<Long, List<HomeScreenItem>>
    ) {
        val appItems = items.filter { it.itemType == 0 || it.itemType == 2 }
        val pm = context.packageManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val densityDpi = context.resources.displayMetrics.densityDpi
        for (item in appItems) {
            val key = getCacheKey(item)
            if (!iconCache.containsKey(key)) {
                val icon = if (item.itemType == 2) {
                    try {
                        val shortcutId = org.json.JSONObject(item.folderConfigJson).optString("shortcutId")
                        if (item.packageName == context.packageName || com.nexus.launcher.ui.widgets.shortcutbox.NexusBuiltinShortcuts.isBuiltin(shortcutId)) {
                            com.nexus.launcher.ui.widgets.shortcutbox.NexusBuiltinShortcuts.getDrawable(context, shortcutId)
                        } else {
                            val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                                setPackage(item.packageName)
                                setShortcutIds(listOf(shortcutId))
                                setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                            }
                            val shortcuts = launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                            shortcuts?.firstOrNull()?.let { launcherApps.getShortcutIconDrawable(it, densityDpi) }
                        }
                    } catch (e: Exception) { null }
                } else {
                    try { iconResolver.getIcon(item.packageName) } catch (e: Exception) { null }
                }
                
                val finalIcon = icon ?: try { pm.defaultActivityIcon } catch (e: Exception) { null }
                if (finalIcon != null) iconCache[key] = finalIcon
            }
            if (!labelCache.containsKey(key)) {
                try {
                    val label = if (item.itemType == 2) {
                        item.folderTitle.takeIf { it.isNotEmpty() } ?: run {
                            val shortcutId = org.json.JSONObject(item.folderConfigJson).optString("shortcutId")
                            val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                                setPackage(item.packageName)
                                setShortcutIds(listOf(shortcutId))
                                setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                            }
                            val shortcuts = launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                            shortcuts?.firstOrNull()?.shortLabel?.toString() ?: item.packageName
                        }
                    } else {
                        val lang = com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context).language
                        val fallback = com.nexus.launcher.data.repository.LocalizedAppNameFallback.getFallbackLabel(item.packageName, lang)
                        if (fallback != null) {
                            fallback
                        } else {
                            val appInfo = pm.getApplicationInfo(item.packageName, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        }
                    }
                    labelCache[key] = label
                } catch (e: Exception) {
                    // Do not cache fallback
                }
            }
        }
        val activeKeys = appItems.map { getCacheKey(it) }.toMutableSet()
        folderContentsSnapshot.values.flatten().forEach { activeKeys.add(getCacheKey(it)) }
        iconCache.keys.retainAll(activeKeys)
        labelCache.keys.retainAll(activeKeys)
    }

    fun precacheFolderChildIcons(map: Map<Long, List<HomeScreenItem>>) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            precacheFolderChildIconsNow(map)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                (context as? com.nexus.launcher.ui.MainActivity)?.canvasView?.invalidate()
            }
        }
    }

    /**
     * Resolves folder children through [com.nexus.launcher.ui.icons.IconResolver] — the only
     * source that knows about icon packs and themed icons — straight into [iconCache].
     *
     * Kept separate from [precacheFolderChildIcons] so [refreshAllCaches] can run it in sequence
     * with [updateCache] rather than racing it. Folder children are not in the `items` list
     * [updateCache] walks (they live in the folder snapshot), but its closing
     * `iconCache.keys.retainAll(activeKeys)` does evict them — so when the two ran concurrently
     * the retain could land after this had written, dropping every folder child icon. The next
     * folder preview then missed the cache and fell through to
     * `FolderIconPreviewDraw`'s PackageManager fallback, which knows nothing about icon packs.
     * That is why home-screen folder previews showed stock icons while drawer folders, whose
     * icons come from the app list, were right.
     */
    private fun precacheFolderChildIconsNow(map: Map<Long, List<HomeScreenItem>>) {
        if (map.isEmpty()) return
        val ctx = context.applicationContext
        try {
            val resolver = dagger.hilt.android.EntryPointAccessors.fromApplication(ctx, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java).iconResolver()
            for (child in map.values.flatten()) {
                if (iconCache.containsKey(child.packageName)) continue
                try {
                    val icon = resolver.getIcon(child.packageName)
                    if (icon != null) {
                        iconCache[child.packageName] = icon
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun refreshAllCaches(
        items: List<HomeScreenItem>,
        folderContentsSnapshot: Map<Long, List<HomeScreenItem>>
    ) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            labelCache.clear()
            updateCache(items, folderContentsSnapshot)
            // After updateCache, never alongside it — its retainAll would otherwise be free to
            // evict what this just wrote.
            precacheFolderChildIconsNow(folderContentsSnapshot)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                (context as? com.nexus.launcher.ui.MainActivity)?.canvasView?.invalidate()
            }
        }
    }
}
