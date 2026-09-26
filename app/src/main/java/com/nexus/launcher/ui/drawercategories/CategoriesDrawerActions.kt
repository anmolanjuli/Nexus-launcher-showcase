package com.nexus.launcher.ui.drawercategories

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.folder.FolderContextMenuLauncher
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CategoriesDrawerActions {

    fun launch(activity: MainActivity, canvas: LauncherCanvasView, app: CategoryApp) {
        if (app.folderId != null) {
            val intent = CategoriesDrawerIconTouch.toDisplayItem(activity, app).intent ?: return
            canvas.onIntentSelected?.invoke(intent)
            return
        }
        if (app.packageName.isEmpty()) return
        val shortcutId = app.shortcutId
        if (shortcutId != null) {
            try {
                val launcherApps = activity.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE)
                    as android.content.pm.LauncherApps
                launcherApps.startShortcut(
                    app.packageName,
                    shortcutId,
                    null,
                    null,
                    android.os.Process.myUserHandle(),
                )
            } catch (_: Exception) {
            }
            return
        }
        val intent = if (app.className.isNotEmpty()) {
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(app.packageName, app.className)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            activity.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Long press only arms a drag, the way the grid and list drawer do. The menu used to open
     * here, and an open menu takes the touch stream — so the finger could never carry the icon
     * out to the home screen. [showMenu] is what runs instead if the finger never moved.
     */
    fun onLongPress(
        activity: MainActivity,
        canvas: LauncherCanvasView,
        app: CategoryApp,
        tile: View,
    ) {
        val item = CategoriesDrawerIconTouch.toDisplayItem(activity, app)
        val rect = CategoriesDrawerIconTouch.canvasRect(tile, canvas)
        canvas.dragHandler.onLongPressDetected(item, rect, canvas)
    }

    /** The app or folder menu, once the press turned out not to be a drag. */
    fun showMenu(
        activity: MainActivity,
        canvas: LauncherCanvasView,
        app: CategoryApp,
        tile: View,
    ) {
        val item = CategoriesDrawerIconTouch.toDisplayItem(activity, app)
        val rect = CategoriesDrawerIconTouch.canvasRect(tile, canvas)
        val folderId = app.folderId
        if (folderId != null) {
            showFolderMenu(activity, canvas, folderId, tile)
            return
        }
        canvas.onItemLongPressed?.invoke(item, rect, canvas)
        CategoriesDrawerIconTouch.hideSourceTile(tile)
        val previousDismiss = activity.contextMenuManager.onMenuDismissed
        activity.contextMenuManager.onMenuDismissed = {
            CategoriesDrawerIconTouch.restoreSourceTile()
            activity.contextMenuManager.onMenuDismissed = previousDismiss
            previousDismiss?.invoke()
        }
    }

    fun overlayVisibility(
        enabled: Boolean,
        progress: Float,
        forwarding: Boolean,
        dragging: Boolean,
    ): Int {
        if (dragging) return View.INVISIBLE
        val want = enabled && (progress > 0.02f || forwarding)
        return if (want) View.VISIBLE else View.GONE
    }

    private fun showFolderMenu(
        activity: MainActivity,
        canvas: LauncherCanvasView,
        folderId: Long,
        tile: View,
    ) {
        val icon = CategoriesDrawerIconTouch.iconScreenRect(tile)
        val iconX = icon.exactCenterX()
        val iconY = icon.exactCenterY()
        val iconSize = icon.width().toFloat().coerceAtLeast(1f)
        val dao = EntryPointAccessors.fromApplication(
            activity.applicationContext,
            DaoEntryPoint::class.java,
        ).homeScreenDao()
        val restore = { CategoriesDrawerIconTouch.restoreSourceTile() }
        val cached = canvas.folderById[folderId]
        if (cached != null) {
            presentFolderMenu(activity, cached, dao, iconX, iconY, iconSize, tile, restore)
            return
        }
        activity.lifecycleScope.launch {
            val folder = withContext(Dispatchers.IO) { dao.getItemById(folderId.toInt()) }
            if (folder == null) return@launch
            presentFolderMenu(activity, folder, dao, iconX, iconY, iconSize, tile, restore)
        }
    }

    private fun presentFolderMenu(
        activity: MainActivity,
        folder: com.nexus.launcher.data.HomeScreenItem,
        dao: com.nexus.launcher.data.HomeScreenDao,
        iconX: Float,
        iconY: Float,
        iconSize: Float,
        tile: View,
        onDismiss: () -> Unit,
    ) {
        val config = com.nexus.launcher.ui.folder.FolderConfigCodec.parse(folder.folderConfigJson)
        val half = iconSize / 2f
        val highlight = com.nexus.launcher.ui.folder.FolderScrimHighlight.boundsForConfig(
            iconX - half, iconY - half, iconX + half, iconY + half, config,
        )
        FolderContextMenuLauncher.show(
            context = activity,
            folderItem = folder,
            dao = dao,
            coroutineScope = activity.lifecycleScope,
            iconX = iconX,
            iconY = iconY,
            iconSize = iconSize,
            highlightBounds = highlight,
            hugHomeCanonical = false,
            onDismiss = onDismiss,
        )
        CategoriesDrawerIconTouch.hideSourceTile(tile)
    }
}
