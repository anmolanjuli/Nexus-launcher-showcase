package com.nexus.launcher.feed

import android.content.Context
import android.content.SharedPreferences

object FeedBookmarkStore {
    private const val PREFS_NAME = "nexus_feed_bookmarks"
    private const val KEY_BOOKMARKS = "saved_links"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBookmarked(context: Context, link: String): Boolean {
        val set = getPrefs(context).getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
        return set.contains(link)
    }

    fun toggleBookmark(context: Context, link: String): Boolean {
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newState = if (current.contains(link)) {
            current.remove(link)
            false
        } else {
            current.add(link)
            true
        }
        prefs.edit().putStringSet(KEY_BOOKMARKS, current).apply()
        return newState
    }

    fun getBookmarkedLinks(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
    }
}
