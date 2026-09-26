package com.nexus.launcher.ui

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.ShapeAwareHomeScreenDao

/** Page-index permutation math + safe DB remapping for Manage Pages drag-reorder. */
object PageReorderHelper {

    /**
     * Moving page [from] to index [to] yields [oldToNew] where
     * `oldToNew[oldIndex] = newIndex`.
     */
    fun oldToNew(from: Int, to: Int, total: Int): IntArray {
        require(from in 0 until total && to in 0 until total)
        val order = (0 until total).toMutableList()
        val moved = order.removeAt(from)
        order.add(to, moved)
        val map = IntArray(total)
        order.forEachIndexed { newIdx, oldIdx -> map[oldIdx] = newIdx }
        return map
    }

    /**
     * Remap workspace item pages with a two-phase write to avoid unique collisions
     * when swapping indices (e.g. 0↔1).
     */
    suspend fun remapItemPages(shapeDao: HomeScreenDao, oldToNew: IntArray) {
        val total = oldToNew.size
        // Pages are shared by every layout shape: move base rows and per-shape positions alike.
        ShapeAwareHomeScreenDao.positionsOf(shapeDao)?.let { positions ->
            val moved = positions.getAllPositionsDebug()
                .filter { it.page in 0 until total }
                .map { it.copy(page = oldToNew[it.page]) }
            if (moved.isNotEmpty()) positions.upsertPositions(moved)
        }
        val dao = ShapeAwareHomeScreenDao.baseOf(shapeDao)
        val workspace = dao.getAllItemsDebug().filter {
            it.page in 0 until total && it.containerId == -1L
        }
        // Phase 1: park at temporary negative pages keyed by old index
        for (item in workspace) {
            dao.updateItemPage(item.id, tempPage(item.page))
        }
        // Phase 2: write final indices
        for (item in workspace) {
            dao.updateItemPage(item.id, oldToNew[item.page])
        }
    }

    fun remapInMemory(items: List<HomeScreenItem>, oldToNew: IntArray): List<HomeScreenItem> {
        val total = oldToNew.size
        return items.map { item ->
            if (item.page in 0 until total && item.containerId == -1L) {
                item.copy(page = oldToNew[item.page])
            } else {
                item
            }
        }
    }

    /**
     * After deleting [deletedPage], remap [defaultPage] the same way item pages shift:
     * indices above the hole decrement; the deleted index itself clamps to the last
     * remaining page. Always coerced into `0 .. newCount-1`.
     */
    fun defaultPageAfterDelete(defaultPage: Int, deletedPage: Int, newCount: Int): Int {
        val last = (newCount - 1).coerceAtLeast(0)
        val remapped = when {
            defaultPage > deletedPage -> defaultPage - 1
            defaultPage == deletedPage -> deletedPage.coerceAtMost(last)
            else -> defaultPage
        }
        return remapped.coerceIn(0, last)
    }

    private fun tempPage(oldPage: Int): Int = -10_000 - oldPage
}
