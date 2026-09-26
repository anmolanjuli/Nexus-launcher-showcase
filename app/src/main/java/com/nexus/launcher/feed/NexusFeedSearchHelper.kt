package com.nexus.launcher.feed

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Encapsulates search bar visibility, soft keyboard toggling, and styling for NexusFeedPage.
 */
class NexusFeedSearchHelper {

    var searchQuery: String = ""
        private set

    fun onQueryChanged(query: String) {
        searchQuery = query
    }

    fun toggleSearch(
        context: Context,
        searchBarLayout: View,
        searchInput: EditText,
        onQueryCleared: () -> Unit
    ) {
        if (searchBarLayout.visibility == View.VISIBLE) {
            hideSearch(context, searchBarLayout, searchInput, onQueryCleared)
        } else {
            searchBarLayout.visibility = View.VISIBLE
            searchInput.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideSearch(
        context: Context,
        searchBarLayout: View,
        searchInput: EditText,
        onQueryCleared: () -> Unit
    ) {
        if (searchBarLayout.visibility == View.VISIBLE) {
            searchBarLayout.visibility = View.GONE
            searchInput.setText("")
            searchQuery = ""
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
            onQueryCleared()
        }
    }

    fun applyTokens(
        searchBarLayout: View,
        searchInput: EditText,
        searchClearBtn: ImageView,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        dp: Float,
        chromeFillColor: (Int) -> Int
    ) {
        searchBarLayout.background = GradientDrawable().apply {
            if (isEInk) {
                setColor(tokens.surface)
                cornerRadius = 0f
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            } else {
                setColor(chromeFillColor(tokens.surfaceRaised))
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
        searchInput.setHintTextColor(tokens.textSecondary)
        if (isEInk) {
            searchInput.typeface = NexusFeedEInkStyler.serifRegular
            searchInput.setTextColor(tokens.textPrimary)
        } else {
            NexusTypeScale.body.bindTo(searchInput, tokens.textPrimary)
        }
        searchClearBtn.imageTintList = ColorStateList.valueOf(tokens.textSecondary)
    }

    fun getEmptyText(context: Context, activeTab: NexusFeedBottomBar.Tab, selectedCategory: String): String {
        return when {
            searchQuery.isNotEmpty() -> context.getString(com.nexus.launcher.R.string.feed_empty_search, searchQuery)
            activeTab == NexusFeedBottomBar.Tab.SAVED -> context.getString(com.nexus.launcher.R.string.feed_empty_saved)
            !selectedCategory.equals("All", ignoreCase = true) -> context.getString(com.nexus.launcher.R.string.feed_empty_category)
            activeTab == NexusFeedBottomBar.Tab.FEED -> context.getString(com.nexus.launcher.R.string.feed_empty_recent)
            else -> context.getString(com.nexus.launcher.R.string.feed_empty_default)
        }
    }
}
