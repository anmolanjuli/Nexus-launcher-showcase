package com.nexus.launcher.reader.doc

import android.content.Context

/**
 * Stores the user's preferred PDF reading theme: Original, Paper Tint, or Invert Colors.
 * Supports both global default persistence and per-file override.
 */
object PdfThemeModeStore {

    enum class ThemeMode {
        ORIGINAL,
        PAPER_TINT,
        INVERT_COLORS
    }

    private const val PREFS_NAME = "nexus_pdf_reader_prefs"
    private const val KEY_GLOBAL_THEME = "pdf_global_theme_mode"
    private const val KEY_PER_FILE_PREFIX = "pdf_file_theme_"

    fun getTheme(context: Context, uriString: String, isEInkDarkPaper: Boolean): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Check per-file override
        val perFile = prefs.getString(KEY_PER_FILE_PREFIX + uriString, null)
        if (perFile != null) {
            return try {
                ThemeMode.valueOf(perFile)
            } catch (_: Exception) {
                ThemeMode.ORIGINAL
            }
        }

        // 2. Check global setting
        val global = prefs.getString(KEY_GLOBAL_THEME, null)
        if (global != null) {
            return try {
                ThemeMode.valueOf(global)
            } catch (_: Exception) {
                ThemeMode.ORIGINAL
            }
        }

        // 3. Default: Paper Tint if E-Ink Dark Paper is active, else Original
        return if (isEInkDarkPaper) ThemeMode.PAPER_TINT else ThemeMode.ORIGINAL
    }

    fun setTheme(context: Context, uriString: String, mode: ThemeMode, applyGlobally: Boolean = true) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_PER_FILE_PREFIX + uriString, mode.name)
        if (applyGlobally) {
            editor.putString(KEY_GLOBAL_THEME, mode.name)
        }
        editor.apply()
    }
}
