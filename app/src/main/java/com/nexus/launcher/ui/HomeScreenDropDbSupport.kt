package com.nexus.launcher.ui

import android.util.Log
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Shared IO helpers for [HomeScreenDropHandler] (keeps that file under 400 lines). */
internal class HomeScreenDropDbSupport(
    private val homeScreenDao: HomeScreenDao,
    private val dockSettingsRepository: DockSettingsRepository,
    private val updateAllItems: (List<HomeScreenItem>) -> Unit
) {
    /**
     * Removes a leftover duplicate row of the item that just moved ([exceptId]),
     * left behind at that item's previous origin cell.
     *
     * Not a general occupancy cleaner: sharing a cell with the mover is not
     * enough. Widgets / mosaics / any span>1 mover never delete via this path.
     */
    suspend fun clearGhostCell(page: Int, col: Int, row: Int, exceptId: Int) {
        val all = homeScreenDao.getAllItemsDebug()
        val except = all.firstOrNull { it.id == exceptId }
        val atCell = all.filter {
            it.id != exceptId &&
                it.page == page &&
                it.column == col &&
                it.row == row &&
                it.containerId == -1L
        }
        val skipReason = ghostClearSkipReason(except)
        val ghosts = if (skipReason != null || except == null) {
            emptyList()
        } else {
            atCell.filter { occupant ->
                occupant.itemType == except.itemType &&
                    occupant.packageName == except.packageName
            }
        }
        // TEMPORARY — HomeGridDropDiag; remove with the diag object.
        Log.d(
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.TAG,
            "GHOST_SCAN exceptId=$exceptId exceptType=${except?.itemType} " +
                "exceptPkg=${except?.packageName} exceptSpan=${except?.spanX}x${except?.spanY} " +
                "cell=$page,$col,$row skip=$skipReason " +
                "allAtCell=${atCell.joinToString(" ") { com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(it) }.ifBlank { "none" }} " +
                "willDelete=${ghosts.size}"
        )
        ghosts.forEach { ghost ->
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDelete(
                source = "clearGhostCell",
                id = ghost.id,
                extra = "trigger=samePkgTypeLeftover exceptId=$exceptId " +
                    "exceptType=${except?.itemType} cell=$page,$col,$row " +
                    com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(ghost),
                includeStack = true
            )
            homeScreenDao.removeItemById(ghost.id)
            Log.d(
                "FolderDrop",
                "cleared leftover duplicate at page=$page col=$col row=$row"
            )
        }
    }

    private fun ghostClearSkipReason(except: HomeScreenItem?): String? {
        if (except == null) return "moverMissing"
        if (except.itemType == HomeItemTypes.WIDGET ||
            except.itemType == HomeItemTypes.MOSAIC ||
            except.itemType == HomeItemTypes.SHORTCUT_BOX ||
            except.itemType == HomeItemTypes.APP_BOX ||
            except.itemType == HomeItemTypes.LIVE_APP_BOX
        ) {
            return "moverWidgetOrMosaic"
        }
        if (except.spanX > 1 || except.spanY > 1) return "multiCellMover"
        if (except.itemType != HomeItemTypes.APP &&
            except.itemType != HomeItemTypes.SHORTCUT
        ) {
            return "moverNotAppOrShortcut"
        }
        if (except.packageName.isBlank()) return "blankPackage"
        return null
    }

    suspend fun refreshAllItemsFromDb() {
        val fresh = homeScreenDao.getAllItemsDebug()
        withContext(Dispatchers.Main) {
            updateAllItems(fresh)
        }
    }

    suspend fun reindexDockCollapse() {
        DockItemMover.reindexDockCollapse(homeScreenDao, dockSettingsRepository.maxIcons.value) {
            merged -> updateAllItems(merged)
        }
    }
}
