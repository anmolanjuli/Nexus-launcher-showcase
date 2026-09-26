package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Applies Room home-item list emissions onto [LauncherCanvasView]
 * (mutating-state guard, icon cache warm, layout + invalidate).
 */
object CanvasHomeItemsApplier {

    fun apply(view: LauncherCanvasView, items: List<HomeScreenItem>) {
        if (view.isDraggingIcon || view.draggedItem != null) {
            view.layoutDirty = true
            return
        }
        if (view.isMutatingState) {
            val elapsed = System.currentTimeMillis() - view.mutatingStateSetAt
            if (elapsed > 500) {
                // Safety net: force reset, something failed to clear this
                android.util.Log.w(
                    "MutatingStateGuard",
                    "Force-reset stuck isMutatingState after ${elapsed}ms"
                )
                view.isMutatingState = false
            } else {
                return
            }
        }
        view.homeScreenItems = items
        view.folderById = items.filter { it.itemType == 1 }.associateBy { it.id.toLong() }
        view.drawerFolderTileCache.clear()
        view.homeDrawGeneration++

        view.draftResizeItem?.let { draft ->
            val matchingReal = items.firstOrNull { it.id == draft.id }
            if (matchingReal != null &&
                matchingReal.spanX == draft.spanX &&
                matchingReal.spanY == draft.spanY
            ) {
                view.draftResizeItem = null
            }
        }

        // Synchronously cache any NEW items immediately so they're never drawn
        // before their icon exists
        val uncachedItems = items.filter {
            (it.itemType == 0 || it.itemType == 2) &&
                !view.homeScreenRenderer.iconCache.containsKey(
                    view.homeScreenRenderer.getCacheKey(it)
                )
        }
        if (uncachedItems.isNotEmpty()) {
            view.homeScreenRenderer.updateCache(items)
        }

        view.recalculateLayout() // recompute visual map → totalPages synced at end
        view.invalidate()

        // Full cache refresh (labels, cleanup, folder contents) still async
        view.cacheRefreshJob?.cancel()
        view.cacheRefreshJob = view.renderScope.launch {
            view.homeScreenRenderer.updateCache(items)
            withContext(Dispatchers.Main) {
                view.invalidate()
            }
        }
    }
}
