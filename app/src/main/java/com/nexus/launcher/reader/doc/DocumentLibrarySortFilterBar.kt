package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Modern dropdown filter and sort bar for the Document Library.
 * Houses three independent dropdowns:
 * 1. Status ("All", "Reading", "Finished", "Unread")
 * 2. Genre / Collection (Active collection + "+ Create Genre")
 * 3. Sort (Recent Activity, Recently Added, A-Z, Z-A, Progress, File Size)
 */
class DocumentLibrarySortFilterBar(
    context: Context,
    private var isEInk: Boolean,
    private val onCollectionSelected: (Long) -> Unit,
    private val onCreateCollectionClick: () -> Unit,
    private val onFilterSelected: (LibrarySortFilterStore.FilterMode) -> Unit,
    private val onSortSelected: (LibrarySortFilterStore.SortMode) -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density

    private val chipContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((16 * dp).toInt(), (2 * dp).toInt(), (16 * dp).toInt(), (6 * dp).toInt())
    }

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(chipContainer)
        }
        addView(scroll)
    }

    fun bind(
        tokens: NexusColorTokens,
        collections: List<CollectionRecord>,
        selectedCollectionId: Long,
        selectedFilter: LibrarySortFilterStore.FilterMode,
        selectedSort: LibrarySortFilterStore.SortMode,
        eInk: Boolean = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
    ) {
        isEInk = eInk
        chipContainer.removeAllViews()

        // 1. Status Dropdown Pill
        val isStatusActive = (selectedFilter != LibrarySortFilterStore.FilterMode.ALL)
        val statusName = when (selectedFilter) {
            LibrarySortFilterStore.FilterMode.ALL -> context.getString(R.string.nexus_doc_dropdown_status)
            LibrarySortFilterStore.FilterMode.IN_PROGRESS -> context.getString(R.string.nexus_doc_chip_reading)
            LibrarySortFilterStore.FilterMode.FINISHED -> context.getString(R.string.nexus_doc_filter_finished)
            LibrarySortFilterStore.FilterMode.UNREAD -> context.getString(R.string.nexus_doc_filter_unread)
        }
        val statusLabel = "$statusName ▾"

        chipContainer.addView(buildDropdownPill(
            tokens = tokens,
            label = statusLabel,
            isHighlighted = isStatusActive,
            onClick = {
                DocumentLibraryFilterDialogs.showStatusDialog(
                    context = context,
                    tokens = tokens,
                    isEInk = isEInk,
                    currentFilter = selectedFilter,
                    onFilterSelected = onFilterSelected
                )
            }
        ))

        // 2. Genre / Collection Dropdown Pill
        val hasCollection = (selectedCollectionId > 0)
        val activeCol = if (hasCollection) collections.find { it.id == selectedCollectionId } else null
        val genreName = activeCol?.name ?: context.getString(R.string.nexus_doc_dropdown_genre)
        val genreLabel = "$genreName ▾"
        val colDotColor = if (activeCol != null) resolveCollectionColor(activeCol.colorTag, tokens.accent) else null

        chipContainer.addView(buildDropdownPill(
            tokens = tokens,
            label = genreLabel,
            isHighlighted = hasCollection,
            colorDot = colDotColor,
            onClick = {
                DocumentLibraryFilterDialogs.showGenreDialog(
                    context = context,
                    tokens = tokens,
                    isEInk = isEInk,
                    collections = collections,
                    currentCollectionId = selectedCollectionId,
                    onCollectionSelected = onCollectionSelected,
                    onCreateNewCollection = onCreateCollectionClick
                )
            }
        ))

        // 3. Sort Dropdown Pill
        val isSortCustom = (selectedSort != LibrarySortFilterStore.SortMode.RECENTLY_ADDED &&
                            selectedSort != LibrarySortFilterStore.SortMode.LAST_READ)
        val sortShortName = when (selectedSort) {
            LibrarySortFilterStore.SortMode.LAST_READ,
            LibrarySortFilterStore.SortMode.RECENTLY_ADDED -> context.getString(R.string.nexus_doc_sort_recent_short)
            LibrarySortFilterStore.SortMode.TITLE_AZ -> context.getString(R.string.nexus_doc_sort_az_short)
            LibrarySortFilterStore.SortMode.TITLE_ZA -> context.getString(R.string.nexus_doc_sort_za_short)
            LibrarySortFilterStore.SortMode.READING_PROGRESS -> context.getString(R.string.nexus_doc_sort_progress_short)
            LibrarySortFilterStore.SortMode.FILE_SIZE -> context.getString(R.string.nexus_doc_sort_size_short)
        }
        val sortLabel = context.getString(
            R.string.common_label_value_dropdown,
            context.getString(R.string.nexus_doc_dropdown_sort),
            sortShortName
        )

        chipContainer.addView(buildDropdownPill(
            tokens = tokens,
            label = sortLabel,
            isHighlighted = isSortCustom,
            onClick = {
                DocumentLibraryFilterDialogs.showSortDialog(
                    context = context,
                    tokens = tokens,
                    isEInk = isEInk,
                    currentSort = selectedSort,
                    onSortSelected = onSortSelected
                )
            }
        ))
    }

    private fun buildDropdownPill(
        tokens: NexusColorTokens,
        label: String,
        isHighlighted: Boolean,
        colorDot: Int? = null,
        onClick: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val chipHeight = (32 * dp).toInt()
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight).apply {
                marginEnd = (6 * dp).toInt()
            }
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())

            background = GradientDrawable().apply {
                cornerRadius = if (isEInk) 0f else 16 * dp
                if (isHighlighted) {
                    setColor(tokens.surfaceRaised)
                    setStroke((1 * dp).toInt().coerceAtLeast(1), if (isEInk) tokens.textPrimary else tokens.accent)
                } else {
                    setColor(tokens.surface)
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }

            if (colorDot != null && !isEInk) {
                val dot = View(context).apply {
                    val dotSize = (8 * dp).toInt()
                    layoutParams = LayoutParams(dotSize, dotSize).apply {
                        marginStart = (2 * dp).toInt()
                        marginEnd = (5 * dp).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colorDot)
                    }
                }
                addView(dot)
            }

            val textView = TextView(context).apply {
                text = label
                textSize = 12f
                typeface = if (isEInk) Typeface.MONOSPACE else if (isHighlighted) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isHighlighted && !isEInk) tokens.accent else tokens.textPrimary)
                maxLines = 1
            }
            addView(textView)

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    private fun resolveCollectionColor(colorHex: String?, defaultColor: Int): Int {
        if (colorHex.isNullOrBlank()) return defaultColor
        return try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            defaultColor
        }
    }
}
