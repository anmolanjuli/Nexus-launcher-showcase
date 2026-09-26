package com.nexus.launcher.reader.doc

import android.content.Context

/**
 * Stores the user's preferred PDF page fit mode: Smart Crop, Fit Width, or Fit Page.
 * Supports global default persistence and per-file override.
 */
object PdfPageFitStore {

    enum class FitMode {
        SMART_CROP,
        FIT_WIDTH,
        FIT_PAGE
    }

    private const val PREFS_NAME = "nexus_pdf_reader_prefs"
    private const val KEY_GLOBAL_FIT = "pdf_global_fit_mode"
    private const val KEY_PER_FILE_PREFIX = "pdf_file_fit_"

    fun getFitMode(context: Context, uriString: String): FitMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Check per-file override
        val perFile = prefs.getString(KEY_PER_FILE_PREFIX + uriString, null)
        if (perFile != null) {
            return try {
                FitMode.valueOf(perFile)
            } catch (_: Exception) {
                FitMode.SMART_CROP
            }
        }

        // 2. Check global setting
        val global = prefs.getString(KEY_GLOBAL_FIT, null)
        if (global != null) {
            return try {
                FitMode.valueOf(global)
            } catch (_: Exception) {
                FitMode.SMART_CROP
            }
        }

        // 3. Recommended Default: SMART_CROP
        return FitMode.SMART_CROP
    }

    fun setFitMode(context: Context, uriString: String, mode: FitMode, applyGlobally: Boolean = true) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_PER_FILE_PREFIX + uriString, mode.name)
        if (applyGlobally || mode == FitMode.SMART_CROP) {
            editor.putString(KEY_GLOBAL_FIT, mode.name)
        }
        editor.apply()
    }
}
