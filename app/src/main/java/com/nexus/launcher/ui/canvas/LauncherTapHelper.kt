package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.folder.FolderHomeTouchHelper
import com.nexus.launcher.ui.folder.FolderDrawerTouchHelper

object LauncherTapHelper {
    fun handleTap(view: LauncherCanvasView, event: MotionEvent, dragTouchHandler: DragTouchHandler): Boolean {
        val tappedItem = if (view.uiState == LauncherState.HOME) CanvasHitTestHelper.getItemAt(view, event.x, event.y) else null
        if (tappedItem != null) {
            if (tappedItem.itemType == 1) {
                if (FolderHomeTouchHelper.shouldBlockFolderTap()) return true
                FolderHomeTouchHelper.openFolderOnTap(view, tappedItem)
                return true
            }
            if (tappedItem.itemType == 0 || tappedItem.itemType == 2) {
                val intent = if (tappedItem.itemType == 2 && tappedItem.folderConfigJson.isNotBlank()) {
                    try {
                        val shortcutId = org.json.JSONObject(tappedItem.folderConfigJson).optString("shortcutId")
                        android.content.Intent("nexus.shortcut.START").apply {
                            putExtra("packageName", tappedItem.packageName)
                            putExtra("shortcutId", shortcutId)
                        }
                    } catch (_: Exception) {
                        view.context.packageManager.getLaunchIntentForPackage(tappedItem.packageName)
                    }
                } else {
                    view.context.packageManager.getLaunchIntentForPackage(tappedItem.packageName)
                }
                if (intent != null) {
                    view.onIntentSelected?.invoke(intent)
                    return true
                }
            }
        }

        val intent = if (view.uiState == LauncherState.HOME) {
            dragTouchHandler.tapHomeScreen(event.x, event.y) ?: dragTouchHandler.tap(event.x, event.y)
        } else {
            dragTouchHandler.tap(event.x, event.y)
        }
        if (intent != null) {
            if (intent.action == "nexus.folder.OPEN") {
                FolderDrawerTouchHelper.cancel(view)
                if (FolderDrawerTouchHelper.shouldBlockFolderTap()) {
                    return true
                }
            }
            view.onIntentSelected?.invoke(intent)
            return true
        }
        return false
    }
}
