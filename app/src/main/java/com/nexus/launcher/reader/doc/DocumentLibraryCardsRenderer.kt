package com.nexus.launcher.reader.doc

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Renders document cards into either single-column list or 2-column grid layout.
 */
object DocumentLibraryCardsRenderer {

    fun render(
        context: Context,
        docs: List<DocumentRecord>,
        collections: List<CollectionRecord>,
        viewMode: LibrarySortFilterStore.ViewMode,
        tokens: NexusColorTokens,
        dp: Float,
        isEInk: Boolean,
        isDark: Boolean,
        coverManager: DocumentCoverManager,
        listContainer: LinearLayout,
        emptyView: View,
        onLoadCover: (DocumentRecord, (android.graphics.Bitmap?) -> Unit) -> Unit,
        onDocumentClick: (DocumentRecord) -> Unit,
        onDocumentLongClick: (DocumentRecord) -> Unit
    ) {
        listContainer.removeAllViews()
        if (docs.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            listContainer.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        listContainer.visibility = View.VISIBLE

        if (viewMode == LibrarySortFilterStore.ViewMode.GRID) {
            for (chunk in docs.chunked(2)) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (16 * dp).toInt()
                    }
                }
                val card1 = DocumentLibraryGridCardBinder.buildGridCard(
                    context = context,
                    doc = chunk[0],
                    collections = collections,
                    tokens = tokens,
                    dp = dp,
                    isEInk = isEInk,
                    isDark = isDark,
                    coverManager = coverManager,
                    onLoadCover = onLoadCover,
                    onClick = { onDocumentClick(chunk[0]) },
                    onLongClick = { onDocumentLongClick(chunk[0]) }
                )
                (card1.layoutParams as LinearLayout.LayoutParams).marginEnd = (8 * dp).toInt()
                row.addView(card1)

                if (chunk.size > 1) {
                    val card2 = DocumentLibraryGridCardBinder.buildGridCard(
                        context = context,
                        doc = chunk[1],
                        collections = collections,
                        tokens = tokens,
                        dp = dp,
                        isEInk = isEInk,
                        isDark = isDark,
                        coverManager = coverManager,
                        onLoadCover = onLoadCover,
                        onClick = { onDocumentClick(chunk[1]) },
                        onLongClick = { onDocumentLongClick(chunk[1]) }
                    )
                    (card2.layoutParams as LinearLayout.LayoutParams).marginStart = (8 * dp).toInt()
                    row.addView(card2)
                } else {
                    val spacer = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply {
                            marginStart = (8 * dp).toInt()
                        }
                    }
                    row.addView(spacer)
                }
                listContainer.addView(row)
            }
        } else {
            for (doc in docs) {
                val card = DocumentLibraryCardBinder.buildDocumentCard(
                    context = context,
                    doc = doc,
                    collections = collections,
                    tokens = tokens,
                    dp = dp,
                    isEInk = isEInk,
                    isDark = isDark,
                    coverManager = coverManager,
                    onLoadCover = onLoadCover,
                    onClick = { onDocumentClick(doc) },
                    onLongClick = { onDocumentLongClick(doc) }
                )
                listContainer.addView(card)
            }
        }
    }
}
