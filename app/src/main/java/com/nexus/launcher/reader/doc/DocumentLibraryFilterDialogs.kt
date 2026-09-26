package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Modal selection dialogs for Document Library: Status, Genre/Collection, and Sort parameters.
 */
object DocumentLibraryFilterDialogs {

    fun showStatusDialog(
        context: Context,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        currentFilter: LibrarySortFilterStore.FilterMode,
        onFilterSelected: (LibrarySortFilterStore.FilterMode) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context = context,
            title = context.getString(R.string.nexus_doc_dropdown_status),
            tokens = tokens,
            dp = dp
        )

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val options = listOf(
            Pair(LibrarySortFilterStore.FilterMode.ALL, context.getString(R.string.nexus_doc_status_all)),
            Pair(LibrarySortFilterStore.FilterMode.IN_PROGRESS, context.getString(R.string.nexus_doc_chip_reading)),
            Pair(LibrarySortFilterStore.FilterMode.FINISHED, context.getString(R.string.nexus_doc_filter_finished)),
            Pair(LibrarySortFilterStore.FilterMode.UNREAD, context.getString(R.string.nexus_doc_filter_unread))
        )

        for ((mode, label) in options) {
            val row = buildRow(
                context = context,
                label = label,
                isSelected = (mode == currentFilter),
                tokens = tokens,
                isEInk = isEInk,
                dp = dp
            ) {
                dialog.dismiss()
                onFilterSelected(mode)
            }
            list.addView(row)
        }

        card.addView(list)
        dialog.show()
    }

    fun showGenreDialog(
        context: Context,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        collections: List<CollectionRecord>,
        currentCollectionId: Long,
        onCollectionSelected: (Long) -> Unit,
        onCreateNewCollection: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context = context,
            title = context.getString(R.string.nexus_doc_dropdown_genre),
            tokens = tokens,
            dp = dp
        )

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (240 * dp).toInt()
            )
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val colList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. "All Genres" option
        val allRow = buildRow(
            context = context,
            label = context.getString(R.string.nexus_doc_genre_all),
            isSelected = (currentCollectionId == LibrarySortFilterStore.COLLECTION_ALL),
            tokens = tokens,
            isEInk = isEInk,
            dp = dp
        ) {
            dialog.dismiss()
            onCollectionSelected(LibrarySortFilterStore.COLLECTION_ALL)
        }
        colList.addView(allRow)

        // 2. Existing Collections/Genres
        for (col in collections) {
            val dotColor = resolveColor(col.colorTag, tokens.accent)
            val row = buildRow(
                context = context,
                label = col.name,
                isSelected = (col.id == currentCollectionId),
                tokens = tokens,
                isEInk = isEInk,
                dp = dp,
                dotColor = dotColor
            ) {
                dialog.dismiss()
                onCollectionSelected(col.id)
            }
            colList.addView(row)
        }

        // 3. Create New Collection/Genre Action
        val addRow = buildRow(
            context = context,
            label = "+ " + context.getString(R.string.nexus_doc_create_genre),
            isSelected = false,
            tokens = tokens,
            isEInk = isEInk,
            dp = dp,
            dotColor = tokens.accent
        ) {
            dialog.dismiss()
            onCreateNewCollection()
        }
        colList.addView(addRow)

        scroll.addView(colList)
        card.addView(scroll)
        dialog.show()
    }

    fun showSortDialog(
        context: Context,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        currentSort: LibrarySortFilterStore.SortMode,
        onSortSelected: (LibrarySortFilterStore.SortMode) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context = context,
            title = context.getString(R.string.nexus_doc_dropdown_sort),
            tokens = tokens,
            dp = dp
        )

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sortOptions = listOf(
            Pair(LibrarySortFilterStore.SortMode.RECENTLY_ADDED, context.getString(R.string.nexus_doc_sort_recently_added)),
            Pair(LibrarySortFilterStore.SortMode.TITLE_AZ, context.getString(R.string.nexus_doc_sort_title_az)),
            Pair(LibrarySortFilterStore.SortMode.TITLE_ZA, context.getString(R.string.nexus_doc_sort_title_za)),
            Pair(LibrarySortFilterStore.SortMode.READING_PROGRESS, context.getString(R.string.nexus_doc_sort_progress)),
            Pair(LibrarySortFilterStore.SortMode.FILE_SIZE, context.getString(R.string.nexus_doc_sort_file_size))
        )

        for ((mode, label) in sortOptions) {
            val row = buildRow(
                context = context,
                label = label,
                isSelected = (mode == currentSort),
                tokens = tokens,
                isEInk = isEInk,
                dp = dp
            ) {
                dialog.dismiss()
                onSortSelected(mode)
            }
            list.addView(row)
        }

        card.addView(list)
        dialog.show()
    }

    private fun buildRow(
        context: Context,
        label: String,
        isSelected: Boolean,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        dp: Float,
        dotColor: Int? = null,
        onClick: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * dp).toInt()
            )
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)

            background = GradientDrawable().apply {
                cornerRadius = if (isEInk) 0f else 10 * dp
                if (isSelected) {
                    setColor(tokens.surfaceRaised)
                    if (isEInk) setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                } else {
                    setColor(Color.TRANSPARENT)
                }
            }

            if (dotColor != null && !isEInk) {
                val dot = View(context).apply {
                    val s = (10 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s).apply {
                        marginEnd = (10 * dp).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dotColor)
                    }
                }
                addView(dot)
            }

            val tv = TextView(context).apply {
                text = label
                textSize = 14f
                typeface = if (isEInk) Typeface.MONOSPACE else if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) tokens.accent else tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(tv)

            if (isSelected) {
                val check = TextView(context).apply {
                    text = "✓"
                    textSize = 15f
                    typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
                    setTextColor(tokens.accent)
                }
                addView(check)
            }

            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    private fun resolveColor(colorHex: String?, defaultColor: Int): Int {
        if (colorHex.isNullOrBlank()) return defaultColor
        return try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            defaultColor
        }
    }
}
