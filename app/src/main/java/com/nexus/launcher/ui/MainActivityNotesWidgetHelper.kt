package com.nexus.launcher.ui

import android.content.Intent
import com.nexus.launcher.ui.widgets.notes.NotesEditSheet

/**
 * Handles incoming Notes widget tap intents to display NotesEditSheet.
 */
internal fun MainActivity.consumeNotesWidgetIntent(intent: Intent?): Boolean {
    if (intent?.getBooleanExtra("action_open_notes_edit", false) != true) return false
    val widgetId = intent.getIntExtra("notes_widget_id", -1)
    intent.removeExtra("action_open_notes_edit")
    intent.removeExtra("notes_widget_id")
    if (widgetId != -1) {
        NotesEditSheet(this, widgetId).show()
        return true
    }
    return false
}
