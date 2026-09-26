package com.nexus.launcher.ui.dock

import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.launch

/** Full vs incremental dock item binding to avoid flicker on drop commit. */
internal object DockLayoutBindHelper {

    fun bindFull(dock: DockLayout, newItems: List<HomeScreenItem>, skipRedraw: Boolean) {
        dock.applyItems(newItems, refreshAllIntents = true)
        if (skipRedraw) {
            DockLayoutRenderer.acknowledgeBindSync()
            return
        }
        dock.loadMissingIconsSync(newItems)
        dock.loadMissingIconsAsync { dock.post { dock.invalidate() } }
        dock.invalidate()
        dock.requestLayout()
    }

    fun bindIncremental(dock: DockLayout, newItems: List<HomeScreenItem>, skipRedraw: Boolean) {
        dock.applyItems(newItems, refreshAllIntents = false)
        if (skipRedraw) {
            DockLayoutRenderer.acknowledgeBindSync()
            return
        }
        val missing = newItems.filter { !dock.hasCachedIcon(it.packageName) }
        if (missing.isEmpty()) {
            DockLayoutDropCommit.scheduleRedraw(dock)
        } else {
            dock.loadMissingIconsAsync { DockLayoutDropCommit.scheduleRedraw(dock) }
        }
    }
    fun precacheFolderChildIcons(
        dock: DockLayout,
        scope: kotlinx.coroutines.CoroutineScope,
        context: android.content.Context,
        map: Map<Long, List<HomeScreenItem>>,
        iconCache: java.util.concurrent.ConcurrentHashMap<String, android.graphics.drawable.Drawable>
    ) {
        scope.launch {
            try {
                val resolver = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext, 
                    com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                ).iconResolver()
                var loadedAny = false
                for (child in map.values.flatten()) {
                    if (iconCache.containsKey(child.packageName)) continue
                    try {
                        val icon = resolver.getIcon(child.packageName)
                        if (icon != null) {
                            iconCache[child.packageName] = icon
                            loadedAny = true
                        }
                    } catch (_: Exception) {}
                }
                if (loadedAny) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        dock.invalidate()
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
