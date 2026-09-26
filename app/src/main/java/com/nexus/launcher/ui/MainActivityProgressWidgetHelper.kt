package com.nexus.launcher.ui

import android.content.Intent
import com.nexus.launcher.ui.widgets.progress.ProgressSettingsSheet

/**
 * Handles incoming Progress Bar widget settings/slot tap intents.
 */
internal fun MainActivity.consumeProgressWidgetIntent(intent: Intent?): Boolean {
    if (intent?.getBooleanExtra("action_open_progress_settings", false) != true) return false
    val widgetId = intent.getIntExtra("progress_widget_id", -1)
    val slotIndex = intent.getIntExtra("progress_slot_index", -1)
    intent.removeExtra("action_open_progress_settings")
    intent.removeExtra("progress_widget_id")
    intent.removeExtra("progress_slot_index")
    if (widgetId != -1) {
        ProgressSettingsSheet(this, widgetId, slotIndex).show()
        return true
    }
    return false
}
