package com.nexus.launcher.reader.doc

import android.content.Context

/**
 * Stores persistent user sort, filter, and collection view preferences for the Document Library.
 */
object LibrarySortFilterStore {

    enum class SortMode {
        LAST_READ,
        RECENTLY_ADDED,
        TITLE_AZ,
        TITLE_ZA,
        FILE_SIZE,
        READING_PROGRESS
    }

    enum class FilterMode {
        ALL,
        IN_PROGRESS,
        FINISHED,
        UNREAD
    }

    enum class ViewMode {
        LIST,
        GRID
    }

    private const val PREFS_NAME = "nexus_doc_library_prefs"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_FILTER_MODE = "filter_mode"
    private const val KEY_VIEW_MODE = "view_mode"
    private const val KEY_SELECTED_COLLECTION = "selected_collection_id"

    const val COLLECTION_ALL = -1L
    const val COLLECTION_UNCATEGORIZED = 0L

    fun getSortMode(context: Context): SortMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SORT_MODE, SortMode.RECENTLY_ADDED.name)
        return try {
            val mode = SortMode.valueOf(raw ?: SortMode.RECENTLY_ADDED.name)
            if (mode == SortMode.LAST_READ) SortMode.RECENTLY_ADDED else mode
        } catch (_: Exception) {
            SortMode.RECENTLY_ADDED
        }
    }

    fun setSortMode(context: Context, mode: SortMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SORT_MODE, mode.name)
            .apply()
    }

    fun getFilterMode(context: Context): FilterMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FILTER_MODE, FilterMode.ALL.name)
        return try {
            FilterMode.valueOf(raw ?: FilterMode.ALL.name)
        } catch (_: Exception) {
            FilterMode.ALL
        }
    }

    fun setFilterMode(context: Context, mode: FilterMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FILTER_MODE, mode.name)
            .apply()
    }

    fun getSelectedCollection(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SELECTED_COLLECTION, COLLECTION_ALL)
    }

    fun setSelectedCollection(context: Context, collectionId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SELECTED_COLLECTION, collectionId)
            .apply()
    }

    fun getViewMode(context: Context): ViewMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VIEW_MODE, ViewMode.LIST.name)
        return try {
            ViewMode.valueOf(raw ?: ViewMode.LIST.name)
        } catch (_: Exception) {
            ViewMode.LIST
        }
    }

    fun setViewMode(context: Context, mode: ViewMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_VIEW_MODE, mode.name)
            .apply()
    }
}
