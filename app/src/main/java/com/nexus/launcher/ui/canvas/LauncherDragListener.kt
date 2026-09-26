package com.nexus.launcher.ui.canvas

import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.model.DisplayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherDragListener(private val view: LauncherCanvasView) : View.OnDragListener {
    private var lastNativeDragCell: Pair<Int, Int>? = null

    override fun onDrag(v: View, e: DragEvent): Boolean {
        if (e.action == DragEvent.ACTION_DRAG_LOCATION) {
            val cell = CanvasHitTestHelper.getCellAtDrop(view, e.x, e.y)
            if (cell != lastNativeDragCell && cell != null) {
                lastNativeDragCell = cell
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }

            val targetItem = CanvasHitTestHelper.getItemAt(view, e.x, e.y)
            if (targetItem != null && targetItem.itemType == 1) {
                if (view.hoveredMergeTarget?.id != targetItem.id) {
                    view.hoveredMergeTarget = targetItem
                    view.invalidate()
                }
            } else if (view.hoveredMergeTarget != null) {
                view.hoveredMergeTarget = null
                view.invalidate()
            }
        } else if (e.action == DragEvent.ACTION_DRAG_EXITED || e.action == DragEvent.ACTION_DRAG_ENDED) {
            lastNativeDragCell = null
            if (view.hoveredMergeTarget != null) {
                view.hoveredMergeTarget = null
                view.invalidate()
            }
        } else if (e.action == DragEvent.ACTION_DROP) {
            lastNativeDragCell = null
            val droppedPackage = (e.localState as? DisplayItem)?.intent?.component?.packageName

            if (view.hoveredMergeTarget != null && droppedPackage != null) {
                val target = view.hoveredMergeTarget!!
                if (target.itemType == 1) {
                    val folderId = target.id.toLong()
                    val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        view.context.applicationContext,
                        com.nexus.launcher.di.DaoEntryPoint::class.java
                    ).homeScreenDao()

                    val newAppItem = HomeScreenItem(
                        packageName = droppedPackage,
                        page = -1, row = 0, column = 0, itemType = 0, containerId = folderId, folderTitle = ""
                    )

                    view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val success = com.nexus.launcher.ui.folder.FolderAppendEngine.appendAppToFolder(newAppItem, folderId, dao, view.context)
                        withContext(Dispatchers.Main) {
                            if (!success) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                            view.folderGlowManager.triggerFolderGlow(folderId, success)
                        }
                    }

                    view.hoveredMergeTarget = null
                    view.invalidate()
                } else if (target.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX) {
                    val boxId = target.id.toLong()
                    val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        view.context.applicationContext,
                        com.nexus.launcher.di.DaoEntryPoint::class.java
                    ).homeScreenDao()
                    val config = com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxConfig.parse(target.folderConfigJson)
                    val maxSlots = config.gridCols * config.gridRows
                    val newAppItem = HomeScreenItem(
                        packageName = droppedPackage,
                        page = -1, row = 0, column = 0, itemType = 0, containerId = boxId, folderTitle = ""
                    )

                    view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val success = com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxOperations.appendItemToBox(dao, boxId, maxSlots, newAppItem)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                            (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.triggerBoxDropPulse(boxId.toInt(), success)
                        }
                    }

                    view.hoveredMergeTarget = null
                    view.invalidate()
                } else if (target.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX) {
                    val boxId = target.id.toLong()
                    val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        view.context.applicationContext,
                        com.nexus.launcher.di.DaoEntryPoint::class.java
                    ).homeScreenDao()
                    val config = com.nexus.launcher.ui.widgets.appbox.AppBoxConfig.parse(target.folderConfigJson)
                    val maxSlots = config.gridCols * config.gridRows
                    val newAppItem = HomeScreenItem(
                        packageName = droppedPackage,
                        page = -1, row = 0, column = 0, itemType = 0, containerId = boxId, folderTitle = ""
                    )

                    view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val success = com.nexus.launcher.ui.widgets.appbox.AppBoxOperations.appendItemToBox(dao, boxId, maxSlots, newAppItem)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                            (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.triggerBoxDropPulse(boxId.toInt(), success)
                        }
                    }

                    view.hoveredMergeTarget = null
                    view.invalidate()
                }
            } else {
                (e.localState as? DisplayItem)?.let { item -> CanvasHitTestHelper.getCellAtDrop(view, e.x, e.y)?.let { cell ->
                    val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        view.context.applicationContext,
                        com.nexus.launcher.di.DaoEntryPoint::class.java
                    ).homeScreenDao()

                    view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val items = dao.getAllItemsDebug()
                        val occupied = com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                            view.context, items, view.currentPage, cell.first, cell.second, emptyMap()
                        )

                        withContext(Dispatchers.Main) {
                            if (occupied != null && (occupied.itemType == 3 || occupied.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC)) {
                                com.nexus.launcher.ui.HomeScreenDropHandler.showWidgetRejection(view.context, view)
                                return@withContext
                            }

                            val p = view.context.getSharedPreferences("nexus_prefs", 0)
                            val pageCountKey = "home_page_count"
                            if (view.currentPage + 1 > p.getInt(pageCountKey, 1)) p.edit().putInt(pageCountKey, view.currentPage + 1).apply()
                            view.onNativeDrop?.invoke(item, view.currentPage, (cell.first + 0.5f) / view.effectiveHomeColumns, (cell.second + 0.5f) / view.effectiveHomeRows)
                        }
                    }
                } }
                view.hoveredMergeTarget = null
                view.invalidate()
            }
        }
        return true
    }

    /** Defer rejection feedback until after WindowManager finishes drag teardown. */
    private fun postWidgetRejection() {
        view.post {
            com.nexus.launcher.ui.HomeScreenDropHandler.showWidgetRejection(view.context, view)
        }
    }
}
