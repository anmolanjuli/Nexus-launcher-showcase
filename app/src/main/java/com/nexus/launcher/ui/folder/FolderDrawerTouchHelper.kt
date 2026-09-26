package com.nexus.launcher.ui.folder

import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.GridItem
import com.nexus.launcher.ui.model.SelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Drawer folder: menu on long-press, drag on move after long-press. */
internal object FolderDrawerTouchHelper {

    private var pendingFolderId: Long = -1L
    private var pendingItem: DisplayItem? = null
    private var longPressFired = false
    private var menuShown = false
    private var longPressRunnable: Runnable? = null

    fun isArmingLongPress(): Boolean = pendingFolderId > 0L && !longPressFired

    fun armOnDown(view: LauncherCanvasView, x: Float, y: Float) {
        cancelPending(view)
        resetGesture()
        val gridItem = DrawerFolderGesture.findFolderAt(view, x, y) ?: return
        val folderId = gridItem.intent?.getLongExtra("folderId", -1L) ?: -1L
        if (folderId <= 0L) return
        pendingFolderId = folderId
        pendingItem = displayItemForFolder(view, gridItem, folderId)
        longPressRunnable = Runnable {
            if (view.selectionState is SelectionState.Selecting) return@Runnable
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            longPressFired = true
            menuShown = true
            showMenu(view, folderId)
        }
        view.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
    }

    fun cancel(view: LauncherCanvasView) {
        cancelPending(view)
        resetGesture()
    }

    fun cancelPending(view: LauncherCanvasView) {
        longPressRunnable?.let { view.removeCallbacks(it) }
        longPressRunnable = null
    }

    fun onDrawerScrollIntent(view: LauncherCanvasView, totalDx: Float, totalDy: Float) {
        if (longPressFired || pendingFolderId <= 0L) return
        val slop = view.touchSlop.toFloat() * 5f
        if (kotlin.math.abs(totalDy) > slop && kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) * 1.5f) {
            cancelPending(view)
            pendingFolderId = -1L
            pendingItem = null
        }
    }

    fun onMove(view: LauncherCanvasView, totalDx: Float, totalDy: Float) {
        if (!longPressFired || pendingFolderId <= 0L) return
        val slop = view.touchSlop.toFloat()
        if (kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble()) <= slop) return
        if (menuShown) {
            FolderContextMenuLauncher.dismiss()
            menuShown = false
        }
        val item = pendingItem ?: return
        val rect = folderRectLocal(view, pendingFolderId) ?: return
        if (!view.dragHandler.isIconDragActive) {
            view.dragHandler.onLongPressDetected(item, rect, view)
        }
    }

    fun onUp(view: LauncherCanvasView): Boolean {
        cancelPending(view)
        if (view.dragHandler.isIconDragActive) {
            resetGesture()
            return false
        }
        val consume = longPressFired || menuShown || FolderContextMenuLauncher.isShowing()
        resetGesture()
        return consume
    }

    fun shouldBlockFolderTap(): Boolean =
        longPressFired || menuShown || FolderContextMenuLauncher.isShowing()

    private fun resetGesture() {
        pendingFolderId = -1L
        pendingItem = null
        longPressFired = false
        menuShown = false
    }

    private fun displayItemForFolder(
        view: LauncherCanvasView,
        gridItem: GridItem,
        folderId: Long
    ): DisplayItem {
        return view.rawDrawerApps.firstOrNull { raw ->
            raw.intent?.action == "nexus.folder.OPEN" &&
                raw.intent.getLongExtra("folderId", -1L) == folderId
        } ?: com.nexus.launcher.ui.model.DisplayItem(
            gridItem.label, gridItem.icon, gridItem.intent, gridItem.categoryName
        )
    }

    private fun folderRectLocal(view: LauncherCanvasView, folderId: Long): android.graphics.Rect? {
        val gridItem = view.drawerItems.firstOrNull {
            it.intent?.action == "nexus.folder.OPEN" &&
                it.intent.getLongExtra("folderId", -1L) == folderId
        } ?: return null
        val r = gridItem.drawRect
        return android.graphics.Rect(
            r.left,
            (r.top - view.scrollY + view.drawerTranslationY).toInt(),
            r.right,
            (r.bottom - view.scrollY + view.drawerTranslationY).toInt()
        )
    }

    private fun showMenu(view: LauncherCanvasView, folderId: Long) {
        val activity = view.context as? AppCompatActivity ?: return
        val cachedFolder = view.homeScreenItems.firstOrNull {
            it.id.toLong() == folderId && it.itemType == 1
        }
        val gridItem = view.drawerItems.firstOrNull {
            it.intent?.action == "nexus.folder.OPEN" &&
                it.intent.getLongExtra("folderId", -1L) == folderId
        }
        val anchor = gridItem?.let { DrawerFolderGesture.screenAnchorForItem(view, it, folderId) }
            ?: DrawerFolderGesture.screenAnchorForFolder(view, folderId)
        val density = view.resources.displayMetrics.density
        val iconX = anchor?.iconX ?: (view.width / 2f)
        val iconY = anchor?.iconY ?: (view.height / 2f)
        val iconSize = anchor?.iconSize ?: (48f * density)
        val highlightBounds = anchor?.highlightBounds
        val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
            view.context.applicationContext,
            com.nexus.launcher.di.DaoEntryPoint::class.java
        ).homeScreenDao()

        if (cachedFolder != null) {
            FolderContextMenuLauncher.show(
                context = activity,
                folderItem = cachedFolder,
                dao = dao,
                coroutineScope = activity.lifecycleScope,
                iconX = iconX,
                iconY = iconY,
                iconSize = iconSize,
                highlightBounds = highlightBounds
            )
            return
        }

        activity.lifecycleScope.launch {
            val folderItem = withContext(Dispatchers.IO) { dao.getItemById(folderId.toInt()) }
                ?: return@launch
            FolderContextMenuLauncher.show(
                context = activity,
                folderItem = folderItem,
                dao = dao,
                coroutineScope = activity.lifecycleScope,
                iconX = iconX,
                iconY = iconY,
                iconSize = iconSize,
                highlightBounds = highlightBounds
            )
        }
    }
}
