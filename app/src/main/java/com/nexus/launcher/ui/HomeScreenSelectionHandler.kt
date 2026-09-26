package com.nexus.launcher.ui

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Selection operations for [HomeScreenViewModel]. */
internal class HomeScreenSelectionHandler(
    private val homeScreenDao: HomeScreenDao,
    private val scope: CoroutineScope,
    private val setSelectionState: (SelectionState) -> Unit,
    private val getSelectionState: () -> SelectionState,
    private val appContext: android.content.Context,
) {

    fun enterSelectionMode(pkg: String, source: SelectionSource) {
        setSelectionState(SelectionState.Selecting(setOf(pkg), emptySet(), source))
    }

    fun enterHomeSelectionMode(id: Int, pkg: String) {
        setSelectionState(SelectionState.Selecting(emptySet(), setOf(id), SelectionSource.HOME_SCREEN))
    }

    fun toggleSelection(pkg: String) {
        val c = getSelectionState() as? SelectionState.Selecting ?: return
        val u = if (pkg in c.selectedPackages) c.selectedPackages - pkg else c.selectedPackages + pkg
        setSelectionState(if (u.isEmpty()) SelectionState.Idle else c.copy(selectedPackages = u))
    }

    fun toggleHomeSelection(id: Int) {
        val c = getSelectionState() as? SelectionState.Selecting ?: return
        val u = if (id in c.selectedIds) c.selectedIds - id else c.selectedIds + id
        setSelectionState(if (u.isEmpty()) SelectionState.Idle else c.copy(selectedIds = u))
    }

    fun clearSelection() {
        setSelectionState(SelectionState.Idle)
    }

    /** Clears the current selection but keeps shrunken selection mode active. */
    fun clearSelectionKeepMode() {
        val c = getSelectionState() as? SelectionState.Selecting ?: return
        setSelectionState(c.copy(selectedIds = emptySet(), selectedPackages = emptySet()))
    }

    fun removeGhostIcons(installedPackages: Set<String>) {
        scope.launch(Dispatchers.IO) {
            val allItems = homeScreenDao.getAllItemsDebug()
            allItems.forEach { item ->
                // Match AppSyncEngine: never purge folders / mosaics / widgets / boxes by package
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.FOLDER) return@forEach
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC) return@forEach
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.WIDGET) return@forEach
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX) return@forEach
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX) return@forEach
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX) return@forEach
                if (item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT) return@forEach
                if (!installedPackages.contains(item.packageName)) {
                    homeScreenDao.removeItemById(item.id)
                }
            }
        }
    }

    fun bulkRemoveFromHomeScreen(state: SelectionState.Selecting) {
        scope.launch(Dispatchers.IO) {
            if (state.source == SelectionSource.HOME_SCREEN) {
                state.selectedIds.forEach { id ->
                    val item = homeScreenDao.getAllItemsDebug().firstOrNull { it.id == id }
                    if (item?.itemType == 1) {
                        homeScreenDao.deleteFolderAndContents(id.toLong())
                    } else {
                        homeScreenDao.removeItemById(id)
                    }
                }
            } else {
                state.selectedPackages.forEach { pkg ->
                    homeScreenDao.findItemIdAnyPage(pkg)
                        ?.let { id -> 
                            val item = homeScreenDao.getAllItemsDebug().firstOrNull { it.id == id }
                            if (item?.itemType == 1) {
                                homeScreenDao.deleteFolderAndContents(id.toLong())
                            } else {
                                homeScreenDao.removeItemById(id)
                            }
                        }
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (state.source == SelectionSource.HOME_SCREEN) {
                    setSelectionState(state.copy(selectedIds = emptySet()))
                } else {
                    setSelectionState(state.copy(selectedPackages = emptySet()))
                }
            }
        }
    }

    fun createDrawerFolderFromSelection(state: SelectionState.Selecting) {
        scope.launch(Dispatchers.IO) {
            val packages = state.selectedPackages.toList()
            if (packages.size < 2) return@launch
            val folderId = com.nexus.launcher.ui.folder.FolderMergeEngine.createDrawerFolder(
                packages, homeScreenDao, appContext
            )
            com.nexus.launcher.ui.drawercategories.CategoriesFolderTag.tagCreated(appContext, folderId)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                clearSelectionKeepMode()
            }
        }
    }

    fun createFolderFromSelection(state: SelectionState.Selecting, page: Int, activity: android.app.Activity) {
        scope.launch(Dispatchers.IO) {
            val allItems = homeScreenDao.getAllItemsDebug()
            val selectedItems = allItems.filter { it.id in state.selectedIds }
            val appsOnly = selectedItems.filter { it.itemType == 0 }
            if (appsOnly.size < 2) return@launch
            
            android.util.Log.d("SelectionFolder", "mergeMultiple called with ${appsOnly.size} apps on page $page")
            
            com.nexus.launcher.ui.folder.FolderMergeEngine.mergeMultiple(appsOnly, page, homeScreenDao, activity.applicationContext)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                clearSelectionKeepMode()
            }
        }
    }
}
