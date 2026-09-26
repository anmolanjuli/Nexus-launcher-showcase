package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Rect
import android.view.View
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.widgets.appbox.AppBoxSettingsSheet
import com.nexus.launcher.ui.widgets.appbox.AppBoxView
import com.nexus.launcher.ui.widgets.liveapp.LiveAppSettingsSheet
import com.nexus.launcher.ui.widgets.liveapp.LiveAppView
import com.nexus.launcher.ui.widgets.progress.ProgressSettingsSheet
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxSettingsSheet
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView

/**
 * Resolves and launches the appropriate settings bottom sheet for widgets,
 * App Box, Shortcut Box, and Live App Box.
 */
object WidgetSettingsSheetLauncher {

    fun buildSettingsAction(
        activity: MainActivity,
        item: HomeScreenItem,
        isNexusWidget: Boolean,
        appWidgetInfo: AppWidgetProviderInfo?,
        overlayRect: Rect,
        widgetView: View?
    ): (() -> Unit)? {
        if (isNexusWidget) {
            return {
                if (appWidgetInfo?.provider?.className?.contains("NexusProgressWidgetProvider") == true) {
                    ProgressSettingsSheet(activity, item.appWidgetId).show()
                } else {
                    NexusWidgetSettingsSheet(activity, item.appWidgetId, overlayRect) {
                        appWidgetInfo?.provider?.let { provider ->
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(item.appWidgetId))
                                component = provider
                            }
                            activity.sendBroadcast(intent)
                        }
                    }.show()
                }
            }
        }
        if (item.itemType == HomeItemTypes.SHORTCUT_BOX) {
            return {
                ShortcutBoxSettingsSheet(
                    activity, item, overlayRect, widgetView as? ShortcutBoxView
                ).show()
            }
        }
        if (item.itemType == HomeItemTypes.APP_BOX) {
            return {
                AppBoxSettingsSheet(
                    activity, item, overlayRect, widgetView as? AppBoxView
                ).show()
            }
        }
        if (item.itemType == HomeItemTypes.LIVE_APP_BOX) {
            return {
                LiveAppSettingsSheet(
                    activity, item, overlayRect, widgetView as? LiveAppView
                ).show()
            }
        }
        return null
    }
}
