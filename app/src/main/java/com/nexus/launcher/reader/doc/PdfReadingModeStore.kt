package com.nexus.launcher.reader.doc

import android.content.Context

/**
 * Stores the user's preferred PDF reading mode: Continuous Scroll vs Horizontal Page Turn.
 */
object PdfReadingModeStore {

    enum class Mode {
        CONTINUOUS_SCROLL,
        PAGE_TURN
    }

    private const val PREFS_NAME = "nexus_pdf_reader_prefs"
    private const val KEY_READING_MODE = "pdf_reading_mode"

    fun getMode(context: Context): Mode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_READING_MODE, Mode.CONTINUOUS_SCROLL.name)
        return try {
            Mode.valueOf(raw ?: Mode.CONTINUOUS_SCROLL.name)
        } catch (_: Exception) {
            Mode.CONTINUOUS_SCROLL
        }
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_READING_MODE, mode.name)
            .apply()
    }
}
