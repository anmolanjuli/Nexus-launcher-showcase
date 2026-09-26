package com.nexus.launcher.ui.canvas

import android.content.Intent
import com.nexus.launcher.ui.model.DisplayItem

internal fun dragTapHomeScreen(view: LauncherCanvasView, x: Float, y: Float): Intent? {
    val cols = view.effectiveHomeColumns
    val rows = view.effectiveHomeRows
    val cellWidth = view.gridAreaWidth.toFloat() / cols
    val availableHeight = view.viewHeight - view.topInset -
        view.dockBottomReserve
    val cellHeight = availableHeight.toFloat() / rows

    val col = ((x - view.gridAreaLeft) / cellWidth).toInt().coerceIn(0, cols - 1)
    val row = ((y - view.topInset) / cellHeight).toInt().coerceIn(0, rows - 1)

    if (y < view.topInset || y > view.topInset + availableHeight) return null

    val item = view.homeScreenItems.firstOrNull {
        val pos = view.fractionDerivedPositions[it.id] ?: Triple(it.page, it.column, it.row)
        if (pos.first != view.currentPage) return@firstOrNull false
        val endCol = pos.second + it.spanX - 1
        val endRow = pos.third + it.spanY - 1
        col in pos.second..endCol && row in pos.third..endRow
    } ?: return null

    if (item.itemType == 2 && !item.folderConfigJson.isNullOrEmpty()) {
        try {
            val shortcutId = org.json.JSONObject(item.folderConfigJson).optString("shortcutId")
            return Intent("nexus.shortcut.START").apply {
                putExtra("packageName", item.packageName)
                putExtra("shortcutId", shortcutId)
            }
        } catch (e: Exception) {}
    }
    return view.context.packageManager.getLaunchIntentForPackage(item.packageName)
}

internal fun getDragItemAt(view: LauncherCanvasView, x: Float, y: Float): DisplayItem? {
    val xInt = x.toInt()
    val yInt = y.toInt()

    val adjustedY = yInt + view.scrollY - view.drawerTranslationY
    val gridItem = view.drawerItems.firstOrNull {
        it.hitRect.contains(xInt, adjustedY.toInt())
    } ?: return null
    val folderId = gridItem.intent?.takeIf { it.action == "nexus.folder.OPEN" }
        ?.getLongExtra("folderId", -1L) ?: -1L
    if (folderId > 0L) {
        return view.rawDrawerApps.firstOrNull { raw ->
            raw.intent?.action == "nexus.folder.OPEN" &&
                raw.intent.getLongExtra("folderId", -1L) == folderId
        } ?: DisplayItem(
            gridItem.label, gridItem.icon, gridItem.intent, gridItem.categoryName
        )
    }
    return view.rawDrawerApps.firstOrNull { raw -> raw.label == gridItem.label }
        ?: DisplayItem(
            gridItem.label, gridItem.icon, gridItem.intent, gridItem.categoryName
        )
}
