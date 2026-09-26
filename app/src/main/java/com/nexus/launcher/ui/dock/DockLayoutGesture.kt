package com.nexus.launcher.ui.dock

import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.ui.dock.settings.DockSettingsDialog
import com.nexus.launcher.ui.folder.FolderDockMenuHelper
import com.nexus.launcher.ui.folder.FolderDockTapLauncher

internal class DockLayoutGesture(
    private val dock: DockLayout,
    private val touchSlop: Float,
    private val flingThreshold: Float,
    private val outboundDrag: DockOutboundDrag,
    private val dockSettingsRepository: () -> com.nexus.launcher.ui.dock.settings.DockSettingsRepository
) : GestureDetector.SimpleOnGestureListener() {

    var flingConsumed: Boolean = false

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val item = dock.itemAtTouch(e)
        if (item != null) {
            if (DockSearchSlot.isSearchItem(item)) {
                dock.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                dock.onSearchSlotClicked?.invoke()
                return true
            }
            if (item.itemType == 1) {
                val scope = dock.findViewTreeLifecycleOwner()?.lifecycleScope
                    ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                FolderDockTapLauncher.openFromDock(dock, item, scope)
                return true
            }
            val launchIntent = dock.launchIntentFor(item)
                ?: dock.context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (launchIntent != null) {
                dock.context.startActivity(launchIntent)
            } else {
                Log.e("NexusDock", "Cannot resolve intent for ${item.packageName}")
            }
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        val item = dock.itemAtTouch(e)
        val activity = DockLayoutFinder.getActivitySafe(dock.context)
        if (item != null) {
            dock.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (DockSearchSlot.isSearchItem(item)) {
                outboundDrag.armPendingSearchDrag(item)
            } else if (item.itemType == 1) {
                FolderDockMenuHelper.showContextMenu(dock, item)
                outboundDrag.armPendingDrag(item)
                outboundDrag.clearDeferredMenuOnLift()
            } else {
                activity?.contextMenuManager?.let { manager ->
                    DockContextMenuLauncher.show(activity, manager, dock, item)
                }
                outboundDrag.armPendingDrag(item)
                outboundDrag.clearDeferredMenuOnLift()
            }
        } else {
            dock.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            dock.onDockSettingsRequested?.invoke() ?: activity?.supportFragmentManager?.let {
                DockSettingsDialog(dockSettingsRepository(), dock).show(it, "DockSettings")
            }
        }
    }

    override fun onScroll(
        e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
    ): Boolean {
        val isVertical = DockAxis.isVertical(dock)
        val distanceMain = DockAxis.main(distanceX, distanceY, isVertical)
        val distanceCross = DockAxis.cross(distanceX, distanceY, isVertical)
        if (dock.pageCountInternal <= 1 || kotlin.math.abs(distanceMain) < touchSlop ||
            kotlin.math.abs(distanceMain) < kotlin.math.abs(distanceCross)) return false
        dock.scrollByDelta(distanceMain)
        return true
    }

    override fun onFling(
        e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
    ): Boolean {
        val isVertical = DockAxis.isVertical(dock)
        val velocityMain = DockAxis.main(velocityX, velocityY, isVertical)
        val velocityCross = DockAxis.cross(velocityX, velocityY, isVertical)
        if (kotlin.math.abs(velocityMain) <= kotlin.math.abs(velocityCross) ||
            kotlin.math.abs(velocityMain) < flingThreshold) return false
        val dir = if (velocityMain < 0) 1 else -1
        dock.animateToPageIndex(
            (dock.currentPage + dir).coerceIn(0, (dock.pageCountInternal - 1).coerceAtLeast(0))
        )
        flingConsumed = true
        return true
    }
}
