package com.nexus.launcher.ui.dock

import android.content.Context
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object DockIconLoader {

    fun loadMissingIcons(
        scope: CoroutineScope,
        context: Context,
        items: List<HomeScreenItem>,
        iconCache: ConcurrentHashMap<String, Drawable>,
        onAnyLoaded: () -> Unit
    ) {
        DockLayoutRenderer.ensureIconContext(context)
        scope.launch {
            var loadedAny = false
            for (item in items) {
                if (iconCache.containsKey(item.packageName)) continue
                val drawable = try {
                    val iconResolver = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        context, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                    ).iconResolver()
                    iconResolver.getIcon(item.packageName)
                } catch (_: Exception) {
                    try { context.packageManager.getApplicationIcon(item.packageName) } catch (_: Exception) { null }
                }
                if (drawable != null) {
                    iconCache[item.packageName] = drawable
                    android.util.Log.d("DockIconCache", "cached icon for ${item.packageName}, cache size now ${iconCache.size}")
                    loadedAny = true
                }
            }
            if (loadedAny) onAnyLoaded()
        }
    }

    /**
     * Re-resolves [items] and swaps the results into [iconCache], replacing rather than emptying.
     *
     * Used when the icon source changes (icon pack, shape, theming), where the cached drawables
     * are stale but still correct enough to show until their replacements exist. Emptying the
     * cache first leaves the renderer with nothing to draw, and if the reload is interrupted -
     * or launched into a scope that has since been cancelled - the icons never come back.
     */
    fun reloadIcons(
        scope: CoroutineScope,
        context: Context,
        items: List<HomeScreenItem>,
        iconCache: ConcurrentHashMap<String, Drawable>,
        onLoaded: () -> Unit
    ) {
        DockLayoutRenderer.ensureIconContext(context)
        scope.launch {
            val resolved = HashMap<String, Drawable>(items.size)
            for (item in items) {
                val drawable = try {
                    dagger.hilt.android.EntryPointAccessors.fromApplication(
                        context, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                    ).iconResolver().getIcon(item.packageName)
                } catch (_: Exception) {
                    try { context.packageManager.getApplicationIcon(item.packageName) } catch (_: Exception) { null }
                }
                if (drawable != null) resolved[item.packageName] = drawable
            }
            if (resolved.isNotEmpty()) {
                // Overwrite only. Nothing is evicted here: the same cache also holds folder
                // child icons, which are not in [items], so retaining by dock membership would
                // wipe them. Entries for packages the dock no longer shows cost a reference and
                // are never looked up.
                iconCache.putAll(resolved)
                onLoaded()
            }
        }
    }

    fun loadMissingIconsSync(
        context: Context,
        items: List<HomeScreenItem>,
        iconCache: ConcurrentHashMap<String, Drawable>
    ) {
        for (item in items) {
            if (iconCache.containsKey(item.packageName)) continue
            val drawable = try {
                val iconResolver = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                ).iconResolver()
                iconResolver.getIcon(item.packageName)
            } catch (_: Exception) {
                try { context.packageManager.getApplicationIcon(item.packageName) } catch (_: Exception) { null }
            }
            if (drawable != null) {
                iconCache[item.packageName] = drawable
                android.util.Log.d("DockIconCache", "cached icon synchronously for ${item.packageName}, cache size now ${iconCache.size}")
            }
        }
    }
}
