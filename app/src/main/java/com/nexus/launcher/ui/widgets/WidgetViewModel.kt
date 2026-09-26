package com.nexus.launcher.ui.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetViewModel @Inject constructor(
    private val homeScreenDao: HomeScreenDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun updateWidgetPosition(
        item: HomeScreenItem,
        xFraction: Float,
        yFraction: Float,
        context: android.content.Context,
        targetPage: Int = item.page
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsRepository.settingsFlow.first()
            val (newCol, newRow) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                context, xFraction, yFraction, item.spanX, item.spanY,
                item.folderConfigJson, s.homeColumns, s.homeRows
            )
            homeScreenDao.updateItemPosition(item.id, targetPage, xFraction, yFraction, newCol, newRow)
        }
    }

    fun updateWidgetZIndex(item: HomeScreenItem, zIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            homeScreenDao.updateWidgetZIndex(item.id, zIndex)
        }
    }
    fun updateWidgetPadding(item: HomeScreenItem, paddingEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            homeScreenDao.updateItem(item.copy(paddingEnabled = paddingEnabled))
        }
    }
    fun updateWidgetBounds(item: HomeScreenItem, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsRepository.settingsFlow.first()
            val json = try { org.json.JSONObject(item.folderConfigJson) } catch(e: Exception) { org.json.JSONObject() }
            json.remove("wFrac")
            json.remove("hFrac")
            val jsonStr = json.toString()
            val (newCol, newRow) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                context, xFraction, yFraction, spanX, spanY, jsonStr, s.homeColumns, s.homeRows
            )
            val updated = item.copy(
                spanX = spanX,
                spanY = spanY,
                xFraction = xFraction,
                yFraction = yFraction,
                column = newCol,
                row = newRow,
                folderConfigJson = jsonStr
            )
            homeScreenDao.updateItem(updated)
        }
    }

    fun updateWidgetFreeSize(item: HomeScreenItem, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float, wFrac: Float, hFrac: Float, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsRepository.settingsFlow.first()
            val json = try { org.json.JSONObject(item.folderConfigJson) } catch(e: Exception) { org.json.JSONObject() }
            json.put("wFrac", wFrac.toDouble())
            json.put("hFrac", hFrac.toDouble())
            val jsonStr = json.toString()
            val (newCol, newRow) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                context, xFraction, yFraction, spanX, spanY, jsonStr, s.homeColumns, s.homeRows
            )
            val updated = item.copy(
                spanX = spanX,
                spanY = spanY,
                xFraction = xFraction,
                yFraction = yFraction,
                column = newCol,
                row = newRow,
                folderConfigJson = jsonStr
            )
            homeScreenDao.updateItem(updated)
        }
    }
    fun removeWidget(item: HomeScreenItem, appWidgetHost: android.appwidget.AppWidgetHost) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(item.appWidgetId)
            }
            if (item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX || item.itemType == 1) {
                homeScreenDao.deleteFolderAndContents(item.id.toLong())
            } else {
                homeScreenDao.removeItemById(item.id)
            }
        }
    }
}
