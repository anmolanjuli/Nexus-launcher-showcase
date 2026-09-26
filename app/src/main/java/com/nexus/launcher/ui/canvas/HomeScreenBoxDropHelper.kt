package com.nexus.launcher.ui.canvas

import android.view.HapticFeedbackConstants
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.widgets.appbox.AppBoxConfig
import com.nexus.launcher.ui.widgets.appbox.AppBoxOperations
import com.nexus.launcher.ui.widgets.appbox.AppBoxRenderer
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxConfig
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxOperations
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxRenderer
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Helper for finding dropped slot and appending items to Shortcut Box / App Box. */
object HomeScreenBoxDropHelper {

    fun findDroppedShortcutSlot(
        view: LauncherCanvasView,
        boxId: Int,
        eventX: Float,
        eventY: Float,
        config: ShortcutBoxConfig
    ): Int? {
        val overlay = (view.context as? MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout ?: return null
        val boxView = overlay.findBox(boxId) ?: return null
        if (boxView.width <= 0 || boxView.height <= 0) return null
        val boxLoc = IntArray(2)
        boxView.getLocationOnScreen(boxLoc)
        val canvasLoc = IntArray(2)
        view.getLocationOnScreen(canvasLoc)
        val localX = (canvasLoc[0] + eventX) - boxLoc[0]
        val localY = (canvasLoc[1] + eventY) - boxLoc[1]
        val renderer = ShortcutBoxRenderer(view.context)
        return renderer.getSlotAt(localX, localY, boxView.width.toFloat(), boxView.height.toFloat(), config)
    }

    fun findDroppedAppSlot(
        view: LauncherCanvasView,
        boxId: Int,
        eventX: Float,
        eventY: Float,
        config: AppBoxConfig
    ): Int? {
        val overlay = (view.context as? MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout ?: return null
        val boxView = overlay.findBox(boxId) ?: return null
        if (boxView.width <= 0 || boxView.height <= 0) return null
        val boxLoc = IntArray(2)
        boxView.getLocationOnScreen(boxLoc)
        val canvasLoc = IntArray(2)
        view.getLocationOnScreen(canvasLoc)
        val localX = (canvasLoc[0] + eventX) - boxLoc[0]
        val localY = (canvasLoc[1] + eventY) - boxLoc[1]
        val renderer = AppBoxRenderer(view.context)
        return renderer.getSlotAt(localX, localY, boxView.width.toFloat(), boxView.height.toFloat(), config)
    }

    fun handleDropOnShortcutBox(
        view: LauncherCanvasView,
        target: HomeScreenItem,
        droppedItem: HomeScreenItem,
        eventX: Float,
        eventY: Float
    ) {
        val boxId = target.id.toLong()
        val config = ShortcutBoxConfig.parse(target.folderConfigJson)
        val targetSlot = findDroppedShortcutSlot(view, target.id, eventX, eventY, config)
        view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(
                view.context.applicationContext,
                DaoEntryPoint::class.java
            ).homeScreenDao()
            val maxSlots = config.gridCols * config.gridRows
            val success = ShortcutBoxOperations.appendItemToBox(dao, boxId, maxSlots, droppedItem, targetSlot)
            withContext(Dispatchers.Main) {
                if (success) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
                (view.context as? MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.triggerBoxDropPulse(boxId.toInt(), success)
                if (!success) {
                    com.nexus.launcher.ui.HomeScreenDropHandler.showWidgetRejection(view.context, view)
                }
            }
        }
    }

    fun handleDropOnAppBox(
        view: LauncherCanvasView,
        target: HomeScreenItem,
        droppedItem: HomeScreenItem,
        eventX: Float,
        eventY: Float
    ) {
        val boxId = target.id.toLong()
        val config = AppBoxConfig.parse(target.folderConfigJson)
        val targetSlot = findDroppedAppSlot(view, target.id, eventX, eventY, config)
        view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(
                view.context.applicationContext,
                DaoEntryPoint::class.java
            ).homeScreenDao()
            val maxSlots = config.gridCols * config.gridRows
            val success = AppBoxOperations.appendItemToBox(dao, boxId, maxSlots, droppedItem, targetSlot)
            withContext(Dispatchers.Main) {
                if (success) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
                (view.context as? MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.triggerBoxDropPulse(boxId.toInt(), success)
                if (!success) {
                    com.nexus.launcher.ui.HomeScreenDropHandler.showWidgetRejection(view.context, view)
                }
            }
        }
    }
}
