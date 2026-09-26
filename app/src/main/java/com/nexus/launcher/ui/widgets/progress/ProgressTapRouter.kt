package com.nexus.launcher.ui.widgets.progress

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Creates PendingIntents for Progress Bar widget slot editing and settings.
 */
object ProgressTapRouter {

    const val ACTION_PROGRESS_TAP_SLOT = "com.nexus.launcher.ACTION_PROGRESS_TAP_SLOT"
    const val ACTION_PROGRESS_TAP_BG = "com.nexus.launcher.ACTION_PROGRESS_TAP_BG"

    const val EXTRA_APP_WIDGET_ID = "appWidgetId"
    const val EXTRA_SLOT_INDEX = "slotIndex"

    fun createSlotPendingIntent(context: Context, appWidgetId: Int, slotIndex: Int): PendingIntent {
        val intent = Intent(context, NexusProgressWidgetProvider::class.java).apply {
            action = ACTION_PROGRESS_TAP_SLOT
            putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
            putExtra(EXTRA_SLOT_INDEX, slotIndex)
        }
        val reqCode = (appWidgetId * 10) + slotIndex + 1
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createBgPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NexusProgressWidgetProvider::class.java).apply {
            action = ACTION_PROGRESS_TAP_BG
            putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
        }
        val reqCode = (appWidgetId * 10)
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
