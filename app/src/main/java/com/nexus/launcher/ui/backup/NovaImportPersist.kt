package com.nexus.launcher.ui.backup

import android.content.Context
import androidx.room.withTransaction
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.room.withTransaction

/** Room writes for Nova restore — single transaction so Flow emits once. */
internal object NovaImportPersist {

    data class NovaRow(
        val _id: Int,
        val title: String,
        val packageName: String,
        val container: Int,
        val screen: Int,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val mappedItemType: Int,
        val providerCls: String?,
        val folderKey: Int?
    )

    suspend fun persist(
        context: Context,
        dao: HomeScreenDao,
        rows: List<NovaRow>,
        ourCols: Int,
        ourRows: Int
    ) {
        val prefs = context.applicationContext.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
        val prefsEd = prefs.edit()
        prefs.all.keys.filter { it.startsWith("page_meta_") }.forEach { prefsEd.remove(it) }
        prefsEd.apply()

        val maxDock = com.nexus.launcher.ui.HomeScreenViewModel.maxDockIcons.coerceIn(1, 10)
        val dockColByNovaId = rows.filter { it.container == -101 }
            .sortedWith(compareBy({ it.cellX }, { it._id }))
            .take(maxDock)
            .mapIndexed { index, r -> r._id to index }
            .toMap()

        val roomDb = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DaoEntryPoint::class.java
        ).launcherDatabase()

        roomDb.withTransaction {
            dao.deleteAll()
            val folderKeyToRoomId = mutableMapOf<Int, Long>()
            val placedDesktop = mutableListOf<HomeScreenItem>()
            rows.forEach { r ->
                persistRoot(context, dao, r, ourCols, ourRows, maxDock, dockColByNovaId,
                    folderKeyToRoomId, placedDesktop)
            }
            rows.forEach { r ->
                val newParentId = folderKeyToRoomId[r.container] ?: return@forEach
                dao.insertItem(
                    HomeScreenItem(
                        packageName = r.packageName,
                        page = -1,
                        column = r.cellX,
                        row = r.cellY,
                        xFraction = 0f,
                        yFraction = 0f,
                        itemType = r.mappedItemType,
                        appWidgetId = -1,
                        spanX = Math.max(1, r.spanX),
                        spanY = Math.max(1, r.spanY),
                        zIndex = 0,
                        paddingEnabled = true,
                        containerId = newParentId,
                        folderTitle = "",
                        folderConfigJson = com.nexus.launcher.ui.folder.FolderConfigCodec.toJson(com.nexus.launcher.data.FolderConfig()),
                        providerClassName = r.providerCls
                    )
                )
            }
        }
    }

    private suspend fun persistRoot(
        context: Context,
        dao: HomeScreenDao,
        r: NovaRow,
        ourCols: Int,
        ourRows: Int,
        maxDock: Int,
        dockColByNovaId: Map<Int, Int>,
        folderKeyToRoomId: MutableMap<Int, Long>,
        placedDesktop: MutableList<HomeScreenItem>
    ) {
        if (r.container != -100 && r.container != -101) return
        val isDock = r.container == -101
        if (isDock && r._id !in dockColByNovaId) return

        val spanX = if (isDock) 1 else Math.max(1, r.spanX).coerceAtMost(ourCols)
        val spanY = if (isDock) 1 else Math.max(1, r.spanY).coerceAtMost(ourRows)
        val page = if (isDock) -101 else r.screen
        var col = if (isDock) dockColByNovaId.getValue(r._id)
        else r.cellX.coerceIn(0, (ourCols - spanX).coerceAtLeast(0))
        var row = if (isDock) 0 else r.cellY.coerceIn(0, (ourRows - spanY).coerceAtLeast(0))

        if (!isDock) {
            val pageItems = placedDesktop.filter { it.page == page }
            val collision = com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                context, pageItems, page, col, row, emptyMap(), emptyList(), spanX, spanY
            )
            if (collision != null) {
                val free = com.nexus.launcher.ui.canvas.GridOccupancyHelper.findNearestEmptySlot(
                    context = context,
                    desiredCol = col,
                    desiredRow = row,
                    spanX = spanX,
                    spanY = spanY,
                    maxCols = ourCols,
                    maxRows = ourRows,
                    items = pageItems,
                    visualPositions = emptyMap(),
                    page = page
                )
                if (free != null) {
                    col = free.first
                    row = free.second
                }
            }
        }

        val (xF, yF) = if (isDock) {
            Pair((col + 0.5f) / maxDock, 0.5f)
        } else {
            com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                col, row, ourCols, ourRows, context, spanX, spanY
            )
        }

        val item = HomeScreenItem(
            packageName = r.packageName,
            page = page,
            column = col,
            row = row,
            xFraction = xF,
            yFraction = yF,
            itemType = r.mappedItemType,
            appWidgetId = -1,
            spanX = spanX,
            spanY = spanY,
            zIndex = 0,
            paddingEnabled = true,
            containerId = -1L,
            folderTitle = if (r.mappedItemType == 1) r.title else "",
            folderConfigJson = com.nexus.launcher.ui.folder.FolderConfigCodec.toJson(com.nexus.launcher.data.FolderConfig()),
            providerClassName = r.providerCls
        )

        if (r.mappedItemType == 1) {
            val newId = dao.insertItemAndGetId(item)
            r.folderKey?.let { folderKeyToRoomId[it] = newId }
            if (!isDock) placedDesktop.add(item.copy(id = newId.toInt()))
        } else {
            dao.insertItem(item)
            if (!isDock) placedDesktop.add(item)
        }
    }
}
