package com.nexus.launcher.ui

import android.util.Log
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.DockCanvasDropHelper
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object DockItemMover {

    private const val TAG = "DockDrop"
    private const val DOCK = HomeScreenViewModel.DOCK_CONTAINER

    /** Serializes dock writes — concurrent home/drawer drops were racing and replacing slots. */
    private val writeMutex = Mutex()

    fun computeOptimisticReorder(
        currentDockItems: List<HomeScreenItem>,
        item: HomeScreenItem,
        requestedSlot: Int,
        maxDockIcons: Int
    ): List<HomeScreenItem> {
        val safeCapacity = maxDockIcons.coerceAtLeast(1)
        val searchEnabled = com.nexus.launcher.ui.dock.DockSearchSlot.enabled
        val searchSlot = com.nexus.launcher.ui.dock.DockSearchSlot.slotIndex.coerceAtLeast(0)
        val maxDbApps = if (searchEnabled) (safeCapacity - 1).coerceAtLeast(1) else safeCapacity

        val resolvedItem = DockCanvasDropHelper.resolveIncomingItem(item)
        val dockList = currentDockItems
            .filter { it.page == DOCK && it.id != resolvedItem.id && it.packageName != resolvedItem.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.column }
            .toMutableList()

        val dockCountExcluding = dockList.size
        val effectiveVisualSlot = requestedSlot.coerceIn(0, safeCapacity - 1)
        val dbInsertIndex = if (searchEnabled) {
            when {
                effectiveVisualSlot <= searchSlot -> effectiveVisualSlot.coerceIn(0, dockCountExcluding)
                else -> (effectiveVisualSlot - 1).coerceIn(0, dockCountExcluding)
            }
        } else {
            effectiveVisualSlot.coerceIn(0, dockCountExcluding)
        }

        dockList.add(dbInsertIndex, resolvedItem.copy(page = DOCK))
        val trimmed = if (dockList.size > maxDbApps) dockList.take(maxDbApps).toMutableList() else dockList
        val slotSpan = 1f / safeCapacity
        return trimmed.mapIndexed { index, dockItem ->
            dockItem.copy(
                page = DOCK,
                column = index,
                row = 0,
                xFraction = (index * slotSpan) + (slotSpan / 2f),
                yFraction = 0.5f
            )
        }
    }

    suspend fun moveItemToDock(
        homeScreenDao: HomeScreenDao,
        @Suppress("UNUSED_PARAMETER") allItems: List<HomeScreenItem>,
        item: HomeScreenItem,
        requestedSlot: Int,
        maxDockIcons: Int,
        onMemoryUpdate: (List<HomeScreenItem>) -> Unit
    ) {
        writeMutex.withLock {
            moveItemToDockLocked(homeScreenDao, item, requestedSlot, maxDockIcons, onMemoryUpdate)
        }
    }

    private suspend fun moveItemToDockLocked(
        homeScreenDao: HomeScreenDao,
        item: HomeScreenItem,
        requestedSlot: Int,
        maxDockIcons: Int,
        onMemoryUpdate: (List<HomeScreenItem>) -> Unit
    ) {
        val safeCapacity = maxDockIcons.coerceAtLeast(1)
        try {
            withContext(NonCancellable) {
                purgeGhostDockRows(homeScreenDao, safeCapacity)

                val searchEnabled = com.nexus.launcher.ui.dock.DockSearchSlot.enabled
                val searchSlot = com.nexus.launcher.ui.dock.DockSearchSlot.slotIndex.coerceAtLeast(0)
                val maxDbApps = if (searchEnabled) (safeCapacity - 1).coerceAtLeast(1) else safeCapacity

                val fresh = homeScreenDao.getAllItemsDebug()
                val resolvedItem = resolveDraggedItem(item, fresh)
                val dockCountExcluding = fresh.count {
                    it.page == DOCK && it.id != resolvedItem.id
                }
                val alreadyInDock = fresh.any {
                    it.page == DOCK && it.packageName == resolvedItem.packageName
                }
                if (dockCountExcluding >= maxDbApps && !alreadyInDock) {
                    Log.w(TAG, "rejected: dock at capacity ($dockCountExcluding >= $maxDbApps)")
                    return@withContext
                }

                val effectiveVisualSlot = requestedSlot.coerceIn(0, safeCapacity - 1)
                val dbInsertIndex = if (searchEnabled) {
                    when {
                        effectiveVisualSlot <= searchSlot -> effectiveVisualSlot.coerceIn(0, dockCountExcluding)
                        else -> (effectiveVisualSlot - 1).coerceIn(0, dockCountExcluding)
                    }
                } else {
                    effectiveVisualSlot.coerceIn(0, dockCountExcluding)
                }

                Log.d(TAG, "moveItemToDock: pkg=${resolvedItem.packageName} id=${resolvedItem.id} " +
                    "visualSlot=$effectiveVisualSlot dbInsertIndex=$dbInsertIndex dockSize=$dockCountExcluding maxDbApps=$maxDbApps")

                var dockList = fresh
                    .filter { it.page == DOCK && it.id != resolvedItem.id }
                    .distinctBy { it.packageName }
                    .sortedBy { it.column }
                    .toMutableList()

                dockList.add(dbInsertIndex, resolvedItem.copy(page = DOCK))

                if (dockList.size > maxDbApps) {
                    dockList = dockList.take(maxDbApps).toMutableList()
                }

                val slotSpan = 1f / safeCapacity
                val reindexed = dockList.mapIndexed { index, dockItem ->
                    dockItem.copy(
                        page = DOCK,
                        column = index,
                        row = 0,
                        xFraction = (index * slotSpan) + (slotSpan / 2f),
                        yFraction = 0.5f
                    )
                }

                val keepIds = mutableSetOf<Int>()
                val committed = mutableListOf<HomeScreenItem>()

                reindexed.forEach { dockItem ->
                    committed.add(persistDockRow(homeScreenDao, dockItem, keepIds))
                }

                val isHomeDrag = item.page >= 0 && item.id > 0
                val afterWrite = homeScreenDao.getAllItemsDebug()
                afterWrite.filter { it.page == DOCK && it.id !in keepIds }
                    .forEach { homeScreenDao.removeItemById(it.id) }
                if (isHomeDrag) {
                    afterWrite.filter { it.page != DOCK && it.id == item.id }
                        .forEach { homeScreenDao.removeItemById(it.id) }
                }

                val commitList = committed.sortedBy { it.column }
                Log.d(TAG, "after renumber: " +
                    commitList.joinToString(",") { "${it.packageName}@${it.column}" })

                trimExcessDockItems(homeScreenDao, maxDbApps, keepIds)

                val workspace = homeScreenDao.getAllItemsDebug()
                    .filter { it.page != DOCK && (!isHomeDrag || it.id != item.id) }

                withContext(Dispatchers.Main) {
                    DockLayoutRenderer.commitItems(commitList)
                }
                onMemoryUpdate(workspace + commitList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "moveItemToDock CRASH: ${e.message}")
        }
    }

    private fun resolveDraggedItem(
        item: HomeScreenItem,
        fresh: List<HomeScreenItem>
    ): HomeScreenItem {
        val fromCanvas = DockCanvasDropHelper.resolveIncomingItem(item)
        if (fromCanvas.id > 0) return fromCanvas

        val inDock = fresh.firstOrNull {
            it.page == DOCK && it.packageName == fromCanvas.packageName
        }
        if (inDock != null) return fromCanvas.copy(id = inDock.id)

        if (fromCanvas.page >= 0 && fromCanvas.id > 0) {
            val onWorkspace = fresh.firstOrNull {
                it.page != DOCK && it.packageName == fromCanvas.packageName
            }
            if (onWorkspace != null) return fromCanvas.copy(id = onWorkspace.id)
        }

        return fromCanvas
    }

    private suspend fun persistDockRow(
        homeScreenDao: HomeScreenDao,
        dockItem: HomeScreenItem,
        keepIds: MutableSet<Int>
    ): HomeScreenItem {
        return if (dockItem.id > 0) {
            homeScreenDao.updateItem(dockItem)
            keepIds.add(dockItem.id)
            Log.d(TAG, "after insert: UPDATED pkg=${dockItem.packageName} " +
                "id=${dockItem.id} column=${dockItem.column}")
            dockItem
        } else {
            val assignedId = homeScreenDao.insertItemAndGetId(dockItem.copy(id = 0)).toInt()
            val saved = dockItem.copy(id = assignedId)
            if (assignedId > 0) keepIds.add(assignedId)
            Log.d(TAG, "after insert: NEW pkg=${saved.packageName} " +
                "id=${saved.id} column=${saved.column}")
            saved
        }
    }

    private suspend fun purgeGhostDockRows(homeScreenDao: HomeScreenDao, safeCapacity: Int) {
        val immunizedMin = safeCapacity * 2
        val ghosts = homeScreenDao.getAllItemsDebug().filter {
            it.page == DOCK && (it.column >= immunizedMin || it.id <= 0)
        }
        ghosts.forEach { homeScreenDao.removeItemById(it.id) }
        if (ghosts.isNotEmpty()) {
            Log.d(TAG, "ghost cleanup: deleted=${ghosts.size} ids=${ghosts.map { it.id }}")
        }
    }

    private suspend fun trimExcessDockItems(
        homeScreenDao: HomeScreenDao,
        safeCapacity: Int,
        protectedIds: Set<Int>
    ): Boolean {
        var dockItems = homeScreenDao.getAllItemsDebug().filter { it.page == DOCK }
        val initialCount = dockItems.size
        if (dockItems.size <= safeCapacity) {
            Log.d(TAG, "safety net: count=${dockItems.size} maxSlots=$safeCapacity trim=false")
            return false
        }
        Log.w(TAG, "safety net: count=${dockItems.size} exceeds $safeCapacity, trimming")
        while (dockItems.size > safeCapacity) {
            val victim = dockItems
                .sortedByDescending { it.column }
                .firstOrNull { it.id !in protectedIds } ?: break
            Log.w(TAG, "safety net removing id=${victim.id} column=${victim.column}")
            homeScreenDao.removeItemById(victim.id)
            dockItems = homeScreenDao.getAllItemsDebug().filter { it.page == DOCK }
        }
        Log.d(TAG, "safety net: count before=$initialCount after=${dockItems.size} trim=true")
        return true
    }

    suspend fun removeItemFromDock(
        homeScreenDao: HomeScreenDao,
        maxDockIcons: Int,
        onMemoryUpdate: ((List<HomeScreenItem>) -> Unit)? = null
    ) {
        writeMutex.withLock {
            withContext(NonCancellable) {
                val safeMax = maxDockIcons.coerceAtLeast(1)
                val searchEnabled = com.nexus.launcher.ui.dock.DockSearchSlot.enabled
                val maxDbApps = if (searchEnabled) (safeMax - 1).coerceAtLeast(1) else safeMax
                val remainingItems = homeScreenDao.getAllItemsDebug()
                    .filter { it.page == DOCK }
                    .sortedBy { it.column }

                remainingItems.drop(maxDbApps).forEach { homeScreenDao.removeItemById(it.id) }

                val capped = remainingItems.take(maxDbApps)
                val slotSpan = 1f / safeMax
                val reindexed = capped.mapIndexed { sequentialIndex, item ->
                    val newFraction = (sequentialIndex * slotSpan) + (slotSpan / 2f)
                    item.copy(
                        page = DOCK,
                        column = sequentialIndex,
                        row = 0,
                        xFraction = newFraction,
                        yFraction = 0.5f
                    )
                }
                reindexed.forEach { item ->
                    val prior = capped.firstOrNull { it.id == item.id } ?: return@forEach
                    if (prior.column != item.column || prior.row != item.row ||
                        prior.xFraction != item.xFraction) {
                        homeScreenDao.updateItem(item)
                    }
                }
                val commitList = reindexed.sortedBy { it.column }
                Log.d(TAG, "removeItemFromDock: after renumber: " +
                    commitList.joinToString(",") { "${it.packageName}@${it.column}" })

                withContext(Dispatchers.Main) {
                    DockLayoutRenderer.commitItems(commitList)
                }
                onMemoryUpdate?.invoke(
                    homeScreenDao.getAllItemsDebug().filter { it.page != DOCK } + commitList
                )
            }
        }
    }

    suspend fun reindexDockCollapse(
        homeScreenDao: HomeScreenDao,
        currentMaxIcons: Int,
        onMemoryUpdate: ((List<HomeScreenItem>) -> Unit)? = null
    ) {
        removeItemFromDock(homeScreenDao, currentMaxIcons, onMemoryUpdate)
    }

    /**
     * Renumbers the stored dock rows to 0..n-1, keeping every row. The fix for a hole left by a
     * removal that did not renumber; see [com.nexus.launcher.ui.dock.DockColumnHealer].
     */
    suspend fun compactDockColumns(homeScreenDao: HomeScreenDao, maxDockIcons: Int) {
        writeMutex.withLock {
            withContext(NonCancellable) {
                val current = homeScreenDao.getAllItemsDebug().filter { it.page == DOCK }
                if (!com.nexus.launcher.ui.dock.DockColumnHealer.hasGaps(current)) return@withContext
                val healed = com.nexus.launcher.ui.dock.DockColumnHealer.contiguous(current, maxDockIcons)
                healed.forEach { item ->
                    if (current.firstOrNull { it.id == item.id }?.column != item.column) homeScreenDao.updateItem(item)
                }
                Log.d(TAG, "compactDockColumns: " + healed.joinToString(",") { "${it.packageName}@${it.column}" })
            }
        }
    }

    suspend fun resetDockToDefaults(homeScreenDao: HomeScreenDao) {
        writeMutex.withLock {
            val currentDockItems = homeScreenDao.getAllItemsDebug().filter { it.page == DOCK }
            currentDockItems.forEach { homeScreenDao.removeItemById(it.id) }

            val defaultPackages = listOf(
                "com.google.android.dialer",
                "com.google.android.contacts",
                "com.google.android.apps.messaging",
                "com.google.android.GoogleCamera"
            )
            // Use exactly 4 for defaults to avoid race conditions with DataStore flows
            val safeMax = 4
            val slotSpan = 1f / safeMax
            defaultPackages.forEachIndexed { index, pkg ->
                homeScreenDao.insertItem(
                    HomeScreenItem(
                        packageName = pkg,
                        page = DOCK,
                        column = index,
                        row = 0,
                        xFraction = (index * slotSpan) + (slotSpan / 2f),
                        yFraction = 0.5f,
                        itemType = 0
                    )
                )
            }
        }
    }
}
