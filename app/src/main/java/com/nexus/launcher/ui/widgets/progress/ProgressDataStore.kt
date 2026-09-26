package com.nexus.launcher.ui.widgets.progress

import android.content.Context
import org.json.JSONArray

/**
 * Persists and retrieves ProgressTrack configurations per appWidgetId.
 */
object ProgressDataStore {

    private const val PREFS_NAME = "nexus_progress_prefs"
    private const val KEY_TRACKS_PREFIX = "tracks_"

    fun loadTracks(context: Context, appWidgetId: Int): List<ProgressTrack> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString("$KEY_TRACKS_PREFIX$appWidgetId", null)
        if (rawJson.isNullOrBlank()) {
            return ProgressPresets.createDefaultTracks(context)
        }

        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<ProgressTrack>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(ProgressTrack.fromJson(item))
            }
            if (list.isEmpty()) ProgressPresets.createDefaultTracks(context) else list
        } catch (_: Exception) {
            ProgressPresets.createDefaultTracks(context)
        }
    }

    fun saveTracks(context: Context, appWidgetId: Int, tracks: List<ProgressTrack>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        tracks.take(3).forEach { jsonArray.put(it.toJson()) }

        prefs.edit()
            .putString("$KEY_TRACKS_PREFIX$appWidgetId", jsonArray.toString())
            .apply()
    }

    fun deleteWidget(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_TRACKS_PREFIX$appWidgetId").apply()
    }
}
