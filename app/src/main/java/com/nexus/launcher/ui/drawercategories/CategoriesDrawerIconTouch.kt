package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.model.DisplayItem
import kotlin.math.abs

object CategoriesDrawerIconTouch {

    fun bind(
        tile: View,
        app: CategoryApp,
        onClick: ((CategoryApp) -> Unit)?,
        onLongPress: ((CategoryApp, View) -> Unit)?,
    ) {
        val actionable = app.packageName.isNotEmpty() || app.folderId != null
        if (!actionable) return
        if (onLongPress == null || app.shortcutId != null) {
            if (onClick != null) tile.setOnClickListener { noteFolderAnchor(app, tile); onClick(app) }
            return
        }
        val slop = ViewConfiguration.get(tile.context).scaledTouchSlop
        val timeout = ViewConfiguration.getLongPressTimeout().toLong()
        var downRawX = 0f
        var downRawY = 0f
        var longPressed = false
        var longRunnable: Runnable? = null
        tile.setOnTouchListener { view, event ->
            val canvas = activityOf(view.context)?.canvasView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    longPressed = false
                    longRunnable = Runnable {
                        longPressed = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        onLongPress(app, view)
                    }
                    view.postDelayed(longRunnable!!, timeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.rawX - downRawX)
                    val dy = abs(event.rawY - downRawY)
                    if (!longPressed && (dx > slop || dy > slop)) {
                        longRunnable?.let { view.removeCallbacks(it) }
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    if (longPressed && canvas != null) {
                        val loc = IntArray(2)
                        canvas.getLocationOnScreen(loc)
                        val wasDragging = canvas.dragHandler.isIconDragActive
                        canvas.dragHandler.onTouchMove(
                            event.rawX - loc[0],
                            event.rawY - loc[1],
                            downRawX - loc[0],
                            downRawY - loc[1],
                            slop,
                        )
                        if (canvas.dragHandler.isIconDragActive && !wasDragging) {
                            CategoriesDrawerIconTouch.restoreSourceTile()
                            canvas.onDismissContextMenu?.invoke()
                            com.nexus.launcher.ui.folder.FolderContextMenuLauncher.dismiss()
                            var parent = view.parent
                            while (parent is View) {
                                if (parent is CategoriesDrawerHost) {
                                    parent.visibility = View.INVISIBLE
                                    break
                                }
                                parent = parent.parent
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longRunnable?.let { view.removeCallbacks(it) }
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longRunnable?.let { view.removeCallbacks(it) }
                    val dragging = canvas?.dragHandler?.isIconDragActive == true
                    if (dragging && canvas != null) {
                        val loc = IntArray(2)
                        canvas.getLocationOnScreen(loc)
                        canvas.dragHandler.onTouchUp(event.rawX - loc[0], event.rawY - loc[1])
                    } else if (longPressed) {
                        // Held, then let go without moving: that is the menu.
                        canvas?.dragHandler?.onTouchCancel()
                        CategoriesDrawerMenu.show(app, view)
                    } else if (!longPressed) {
                        val dx = abs(event.rawX - downRawX)
                        val dy = abs(event.rawY - downRawY)
                        if (dx <= slop && dy <= slop) {
                            noteFolderAnchor(app, view)
                            onClick?.invoke(app)
                        }
                    }
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * In Categories mode the canvas has no item for a folder, so the folder window would open
     * from the middle of the screen. Hand the tapped tile's rect over for the opening animation.
     */
    private fun noteFolderAnchor(app: CategoryApp, tile: View) {
        val id = app.folderId ?: return
        com.nexus.launcher.ui.canvas.FolderOpenAnchor.setPendingTileAnchor(id, iconScreenRect(tile))
    }

    fun canvasRect(tile: View, canvas: View): Rect {
        val screen = iconScreenRect(tile)
        val canvasLoc = IntArray(2)
        canvas.getLocationOnScreen(canvasLoc)
        return Rect(
            screen.left - canvasLoc[0],
            screen.top - canvasLoc[1],
            screen.right - canvasLoc[0],
            screen.bottom - canvasLoc[1],
        )
    }

    fun iconScreenRect(tile: View): Rect {
        val tileLoc = IntArray(2)
        tile.getLocationOnScreen(tileLoc)
        val size = if (tile is com.nexus.launcher.ui.drawercategories.CategoryAppTile) {
            tile.iconSizePx()
        } else {
            tile.width.coerceAtMost(tile.height)
        }
        val left = tileLoc[0] + (tile.width - size) / 2
        val top = tileLoc[1]
        return Rect(left, top, left + size, top + size)
    }

    fun hideSourceTile(tile: View) {
        restoreSourceTile()
        hiddenSourceTile = tile
        tile.alpha = 0f
    }

    fun restoreSourceTile() {
        hiddenSourceTile?.alpha = 1f
        hiddenSourceTile = null
    }

    private var hiddenSourceTile: View? = null

    fun toDisplayItem(context: Context, app: CategoryApp): DisplayItem {
        val intent = if (app.folderId != null) {
            android.content.Intent("nexus.folder.OPEN").apply {
                putExtra("folderId", app.folderId)
                putExtra("folderTitle", app.label)
            }
        } else if (app.className.isNotEmpty()) {
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(app.packageName, app.className)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(app.packageName)
        }
        return DisplayItem(app.label, app.icon, intent, app.categoryName)
    }

    private fun activityOf(context: Context): MainActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is MainActivity) return current
            current = current.baseContext
        }
        return current as? MainActivity
    }
}
