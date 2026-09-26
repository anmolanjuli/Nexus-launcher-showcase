package com.nexus.launcher.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.DragSource
import com.nexus.launcher.ui.model.DragState
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val homeScreenDao: HomeScreenDao,
    private val itemPositionDao: com.nexus.launcher.data.ItemPositionDao,
    private val dockSettingsRepository: DockSettingsRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _dragState = MutableStateFlow<DragState>(DragState.Idle)
    val dragState: StateFlow<DragState> = _dragState.asStateFlow()

    private val _selectionState = MutableStateFlow<SelectionState>(SelectionState.Idle)

    suspend fun getAllItemsSnapshot(): List<HomeScreenItem> =
        withContext(Dispatchers.IO) { homeScreenDao.getAllItemsDebug() }
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    private val _folderGlowEvent = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Long, Boolean>>()
    val folderGlowEvent = _folderGlowEvent.asSharedFlow()

    // Raw backing stream of every persisted item (workspace + dock).
    private val _allHomeScreenItems = MutableStateFlow<List<HomeScreenItem>>(emptyList())

    // Workspace icons only. Dock items (page == DOCK_CONTAINER) are split out here so they can
    // never accidentally render on the main canvas — the canvas only ever sees this filtered list.
    val homeScreenItems: StateFlow<List<HomeScreenItem>> = _allHomeScreenItems
        .map { items -> items.filter { it.page != DOCK_CONTAINER } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Dock-only stream consumed by the independent DockLayout. This schema has no dedicated
    // `container` column, so the AOSP hotseat id (-101) is carried on the existing `page` field.
    // `column` carries the slot index, so sorting by it keeps dropped slots in left-to-right order.
    val dockItems: StateFlow<List<HomeScreenItem>> = _allHomeScreenItems
        .map { items -> items.filter { it.page == DOCK_CONTAINER }.sortedBy { it.column } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folderContents: StateFlow<Map<Long, List<HomeScreenItem>>> =
        homeScreenDao.getAllFolderContents()
            .map { items -> items.groupBy { it.containerId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val dropHandler = HomeScreenDropHandler(
        homeScreenDao = homeScreenDao,
        dockSettingsRepository = dockSettingsRepository,
        scope = viewModelScope,
        updateAllItems = { _allHomeScreenItems.value = it },
        getAllItems = { _allHomeScreenItems.value },
        dragState = _dragState,
        cancelDrag = ::cancelDrag,
        context = appContext,
        triggerFolderGlow = ::triggerFolderGlow
    )

    private val selectionHandler = HomeScreenSelectionHandler(
        homeScreenDao = homeScreenDao,
        scope = viewModelScope,
        setSelectionState = { _selectionState.value = it },
        getSelectionState = { _selectionState.value },
        appContext = appContext,
    )

    init {
        HomeScreenIntegrityOps.runStartupIntegrityChecks(viewModelScope, homeScreenDao, appContext, itemPositionDao)
        viewModelScope.launch {
            homeScreenDao.getAllItems().collect { items ->
                _allHomeScreenItems.value = items
            }
        }

        viewModelScope.launch {
            var lastCols = -1
            var lastRows = -1
            settingsRepository.settingsFlow.collect { settings ->
                val newCols = settings.homeColumns
                val newRows = settings.homeRows
                if (lastCols != -1 && (newCols != lastCols || newRows != lastRows)) {
                    HomeGridResizeRemapper.remap(
                        homeScreenDao, appContext, lastCols, lastRows, newCols, newRows
                    )
                }
                lastCols = newCols
                lastRows = newRows
            }
        }
    }

    fun startDrag(item: DisplayItem, x: Float, y: Float, src: DragSource) {
        _dragState.value = DragState.Dragging(item, x, y, src)
    }

    fun updateDragPosition(x: Float, y: Float) {
        (_dragState.value as? DragState.Dragging)?.let {
            _dragState.value = it.copy(fingerX = x, fingerY = y)
        }
    }

    fun cancelDrag() {
        com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
        _dragState.value = DragState.Idle
    }

    fun dropItem(page: Int, col: Int, row: Int) = dropHandler.dropItem(page, col, row)

    fun dropItemAtFraction(page: Int, xFraction: Float, yFraction: Float, col: Int, row: Int) =
        dropHandler.dropItemAtFraction(page, xFraction, yFraction, col, row)

    fun addAppToHomeScreen(item: DisplayItem, page: Int, xFraction: Float, yFraction: Float, visualPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap()) {
        val pkg = item.intent?.component?.packageName ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val live = settingsRepository.settingsFlow.first()
            // homeScreenDao positions are in the active shape's grid; search that grid.
            val base = com.nexus.launcher.data.LayoutShapeState.isBase
            val maxCols = if (base) live.homeColumns else com.nexus.launcher.data.LayoutShapeState.liveColumns
            val maxRows = if (base) live.homeRows else com.nexus.launcher.data.LayoutShapeState.liveRows
            val emptySlot = HomeScreenOpsHelper.findEmptySpace(appContext, homeScreenDao.getAllItemsDebug(), page, maxCols, maxRows, visualPositions)
            if (emptySlot != null) {
                val (xF, yF) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                    emptySlot.first, emptySlot.second, maxCols, maxRows, appContext, 1, 1
                )
                homeScreenDao.insertItem(
                    HomeScreenItem(
                        packageName = pkg, page = page, column = emptySlot.first, row = emptySlot.second,
                        xFraction = xF, yFraction = yF
                    )
                )
            }
        }
    }

    fun addWidgetToHomeScreen(
        appWidgetId: Int,
        providerPackage: String,
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float,
        providerClassName: String? = null
    ) = HomeScreenOpsHelper.addWidgetToHomeScreen(
        viewModelScope, homeScreenDao, appContext, appWidgetId, providerPackage, page, spanX, spanY, xFraction, yFraction, providerClassName
    )

    suspend fun updateItemImmediately(item: HomeScreenItem) {
        withContext(Dispatchers.IO) {
            homeScreenDao.updateItem(item)
        }
    }

    fun addMosaicToHomeScreen(
        page: Int, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float
    ) = HomeScreenMosaicOps.addMosaic(
        viewModelScope, homeScreenDao, appContext, page, spanX, spanY, xFraction, yFraction
    )

    fun updateMosaicConfig(item: HomeScreenItem, config: com.nexus.launcher.ui.widgets.mosaic.MosaicConfig) =
        HomeScreenMosaicOps.updateConfig(viewModelScope, homeScreenDao, item, config)

    fun updateMosaicFreeSize(
        id: Int, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float, wFrac: Float, hFrac: Float
    ) = HomeScreenMosaicOps.updateFreeSize(
        viewModelScope, homeScreenDao, appContext, id, spanX, spanY, xFraction, yFraction, wFrac, hFrac
    )

    fun addWidgetToMosaic(
        mosaic: HomeScreenItem, appWidgetId: Int, providerPackage: String, providerClassName: String,
        targetSlot: Int? = null
    ) = HomeScreenMosaicOps.addWidget(
        viewModelScope, homeScreenDao, mosaic, appWidgetId, providerPackage, providerClassName, targetSlot
    )

    fun removeWidgetFromMosaic(
        mosaic: HomeScreenItem, childIndex: Int, appWidgetHost: android.appwidget.AppWidgetHost
    ) = HomeScreenMosaicOps.removeWidget(viewModelScope, homeScreenDao, mosaic, childIndex, appWidgetHost)

    fun canRestoreMosaicWidget(mosaic: HomeScreenItem, spanX: Int = 2, spanY: Int = 2): Boolean =
        HomeScreenMosaicOps.canRestoreWidget(appContext, homeScreenItems.value, mosaic, spanX, spanY)

    fun restoreMosaicWidgetToHome(mosaic: HomeScreenItem, child: com.nexus.launcher.ui.widgets.mosaic.MosaicChild, nextConfig: com.nexus.launcher.ui.widgets.mosaic.MosaicConfig) =
        HomeScreenMosaicOps.restoreWidgetToHome(viewModelScope, homeScreenDao, appContext, mosaic, child, nextConfig)

    fun removeMosaic(item: HomeScreenItem, appWidgetHost: android.appwidget.AppWidgetHost) =
        HomeScreenMosaicOps.removeMosaic(viewModelScope, homeScreenDao, item, appWidgetHost)

    fun absorbWidgetIntoMosaic(mosaic: HomeScreenItem, widget: HomeScreenItem) =
        HomeScreenMosaicOps.absorbHomeWidget(viewModelScope, homeScreenDao, mosaic, widget)

    fun addShortcutBoxToHomeScreen(
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ) = com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxOperations.addBox(
        viewModelScope, homeScreenDao, appContext, page, spanX, spanY, xFraction, yFraction
    )

    fun addAppBoxToHomeScreen(
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ) = com.nexus.launcher.ui.widgets.appbox.AppBoxOperations.addBox(
        viewModelScope, homeScreenDao, appContext, page, spanX, spanY, xFraction, yFraction
    )

    fun addLiveAppBoxToHomeScreen(
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ) = com.nexus.launcher.ui.widgets.liveapp.LiveAppOperations.addBox(
        viewModelScope, homeScreenDao, appContext, page, spanX, spanY, xFraction, yFraction
    )

    fun triggerFolderGlow(folderId: Long, success: Boolean) {
        viewModelScope.launch {
            _folderGlowEvent.emit(Pair(folderId, success))
        }
    }

    fun moveItemToDock(item: HomeScreenItem, requestedSlot: Int, dockPage: Int = 0) =
        dropHandler.moveItemToDock(item, requestedSlot, dockPage)

    fun moveItemToDock(item: DisplayItem, slotIndex: Int, dockPage: Int = 0) =
        dropHandler.moveItemToDock(item, slotIndex, dockPage)

    fun bridgeCanvasHomeToDockDrop(
        item: HomeScreenItem,
        dock: com.nexus.launcher.ui.dock.DockLayout,
        dropXLocal: Float
    ) = dropHandler.bridgeCanvasHomeToDockDrop(item, dock, dropXLocal)

    fun deleteItem(item: HomeScreenItem) = dropHandler.deleteItem(item)

    fun removeFromHomeScreenById(id: Int) = dropHandler.removeFromHomeScreenById(id)

    fun updateItemPosition(id: Int, page: Int, xFraction: Float, yFraction: Float, col: Int, row: Int) =
        dropHandler.updateItemPosition(id, page, xFraction, yFraction, col, row)

    fun mergeAppsAtHoverTarget(draggedItem: HomeScreenItem, targetApp: HomeScreenItem) =
        dropHandler.mergeAppsAtHoverTarget(draggedItem, targetApp)

    fun updateWidgetBounds(id: Int, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float) =
        dropHandler.updateWidgetBounds(id, spanX, spanY, xFraction, yFraction)

    suspend fun attemptResizeFolder(
        item: HomeScreenItem, newSpanX: Int, newSpanY: Int, newCol: Int, newRow: Int,
        effCols: Int, effRows: Int, visualPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap()
    ): Boolean = HomeScreenResizeHelper.attemptResizeFolder(
        homeScreenDao, appContext, item, newSpanX, newSpanY, newCol, newRow,
        effCols, effRows, visualPositions
    )

    suspend fun attemptResizeIcon(
        item: HomeScreenItem, newSpanX: Int, newSpanY: Int, newCol: Int, newRow: Int,
        effCols: Int, effRows: Int, visualPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap()
    ): Boolean = HomeScreenResizeHelper.attemptResizeIcon(
        homeScreenDao, appContext, item, newSpanX, newSpanY, newCol, newRow,
        effCols, effRows, visualPositions
    )

    fun pinToHome(packageName: String, page: Int, col: Int, row: Int) =
        dropHandler.pinToHome(packageName, page, col, row)

    fun removeFromHomeScreen(packageName: String, page: Int) =
        dropHandler.removeFromHomeScreen(packageName, page)

    fun deletePage(page: Int, totalPages: Int, onComplete: () -> Unit) =
        dropHandler.deletePage(page, totalPages, onComplete)

    fun reorderPages(oldToNew: IntArray, onComplete: () -> Unit) =
        HomeScreenOpsHelper.reorderPages(viewModelScope, homeScreenDao, oldToNew, { _allHomeScreenItems.value = it }, onComplete)

    fun enterSelectionMode(pkg: String, source: SelectionSource) =
        selectionHandler.enterSelectionMode(pkg, source)

    fun enterHomeSelectionMode(id: Int, pkg: String) =
        selectionHandler.enterHomeSelectionMode(id, pkg)

    fun toggleSelection(pkg: String) =
        selectionHandler.toggleSelection(pkg)

    fun toggleHomeSelection(id: Int) =
        selectionHandler.toggleHomeSelection(id)

    fun clearSelection() =
        selectionHandler.clearSelection()

    fun clearSelectionKeepMode() =
        selectionHandler.clearSelectionKeepMode()

    fun removeGhostIcons(installedPackages: Set<String>) =
        selectionHandler.removeGhostIcons(installedPackages)

    fun bulkRemoveFromHomeScreen(state: SelectionState.Selecting) =
        selectionHandler.bulkRemoveFromHomeScreen(state)

    fun createFolderFromSelection(state: SelectionState.Selecting, page: Int, activity: Activity) =
        selectionHandler.createFolderFromSelection(state, page, activity)

    fun createDrawerFolderFromSelection(state: SelectionState.Selecting) =
        selectionHandler.createDrawerFolderFromSelection(state)

    companion object { const val DOCK_CONTAINER = -101; var maxDockIcons = 5 }
}
