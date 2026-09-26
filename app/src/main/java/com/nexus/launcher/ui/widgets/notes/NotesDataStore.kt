package com.nexus.launcher.ui.widgets.notes

import android.content.Context

/**
 * SharedPreferences storage for Notes widget instances.
 */
object NotesDataStore {

    private const val PREFS_NAME = "notes_data_prefs"
    private const val KEY_TITLE = "note_title_"
    private const val KEY_BODY = "note_body_"
    private const val KEY_URGENCY = "note_urgency_"
    private const val KEY_STATUS = "note_status_"
    private const val KEY_PROGRESS = "note_progress_"
    private const val KEY_UPDATED = "note_updated_"

    fun read(context: Context, appWidgetId: Int): NoteData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NoteData(
            title = prefs.getString("$KEY_TITLE$appWidgetId", "") ?: "",
            body = prefs.getString("$KEY_BODY$appWidgetId", "") ?: "",
            urgency = prefs.getString("$KEY_URGENCY$appWidgetId", NoteData.URGENCY_LATER) ?: NoteData.URGENCY_LATER,
            status = prefs.getString("$KEY_STATUS$appWidgetId", NoteData.STATUS_OPEN) ?: NoteData.STATUS_OPEN,
            progress = prefs.getInt("$KEY_PROGRESS$appWidgetId", 0).coerceIn(0, 100),
            updatedAt = prefs.getLong("$KEY_UPDATED$appWidgetId", 0L)
        )
    }

    fun write(context: Context, appWidgetId: Int, note: NoteData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$KEY_TITLE$appWidgetId", note.title.take(NoteData.MAX_TITLE_CHARS))
            .putString("$KEY_BODY$appWidgetId", note.body.take(NoteData.MAX_BODY_CHARS))
            .putString("$KEY_URGENCY$appWidgetId", note.urgency)
            .putString("$KEY_STATUS$appWidgetId", note.status)
            .putInt("$KEY_PROGRESS$appWidgetId", note.progress.coerceIn(0, 100))
            .putLong("$KEY_UPDATED$appWidgetId", if (note.updatedAt > 0L) note.updatedAt else System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        write(
            context,
            appWidgetId,
            NoteData(
                title = "",
                body = "",
                urgency = NoteData.URGENCY_LATER,
                status = NoteData.STATUS_OPEN,
                progress = 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun delete(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("$KEY_TITLE$appWidgetId")
            .remove("$KEY_BODY$appWidgetId")
            .remove("$KEY_URGENCY$appWidgetId")
            .remove("$KEY_STATUS$appWidgetId")
            .remove("$KEY_PROGRESS$appWidgetId")
            .remove("$KEY_UPDATED$appWidgetId")
            .apply()
    }
}
