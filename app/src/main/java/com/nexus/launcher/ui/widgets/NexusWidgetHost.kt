package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import com.nexus.launcher.ui.widgets.WidgetHostContext.themed
import android.appwidget.AppWidgetManager

open class NexusWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {

    companion object {
        /** Must match every AppWidgetHost used for allocate/delete in this app. */
        const val HOST_ID = 1024
    }

    private val appWidgetManager = AppWidgetManager.getInstance(context)

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        val widgetContext = themed(context)
        Log.d("NexusWidgetHost", "onCreateView: appWidgetId=$appWidgetId provider=${appWidgetInfo?.provider?.flattenToShortString()}")
        return NexusWidgetView(widgetContext, appWidgetManager)
    }
}
