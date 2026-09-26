package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView

/**
 * Collection-widget refresh on attach/visibility. All three call sites are required
 * (see WIDGET_ARCHITECTURE.md); do not collapse them.
 */
object NexusWidgetViewNotify {
    fun onAttachedToWindow(view: NexusWidgetView) {
        Log.d("NexusWidgetView", "onAttachedToWindow id=${view.appWidgetId}")
        refresh(view)
    }

    fun onWindowVisibilityChanged(view: NexusWidgetView, visibility: Int) {
        Log.d(
            "NexusWidgetView",
            "onWindowVisibilityChanged id=${view.appWidgetId} vis=$visibility"
        )
        if (visibility == View.VISIBLE) refresh(view)
    }

    fun onVisibilityAggregated(view: NexusWidgetView, isVisible: Boolean) {
        Log.d(
            "NexusWidgetView",
            "onVisibilityAggregated id=${view.appWidgetId} visible=$isVisible"
        )
        if (isVisible) refresh(view)
    }

    fun refresh(view: NexusWidgetView) {
        val manager = AppWidgetManager.getInstance(view.context)
        notifyCollectionAdapters(view, view, manager)
        view.post {
            view.requestLayout()
            view.invalidate()
        }
    }

    private fun notifyCollectionAdapters(
        host: AppWidgetHostView,
        node: View,
        manager: AppWidgetManager
    ) {
        if (node is AdapterView<*> && node.id != View.NO_ID) {
            val adapter = node.adapter
            if (adapter is android.widget.BaseAdapter) {
                adapter.notifyDataSetChanged()
            }
            try {
                val info = manager.getAppWidgetInfo(host.appWidgetId)
                if (info?.provider?.packageName == host.context.packageName) {
                    manager.notifyAppWidgetViewDataChanged(host.appWidgetId, node.id)
                }
            } catch (_: Exception) {}
        }
        if (node is ViewGroup) {
            for (i in 0 until node.childCount) {
                notifyCollectionAdapters(host, node.getChildAt(i), manager)
            }
        }
    }
}

