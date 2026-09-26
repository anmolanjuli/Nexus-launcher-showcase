package com.nexus.launcher.reader.doc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.feed.NexusFeedEInkCoordinator
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Library view coordinating document cards with cover thumbnails, squircle progress rings,
 * Collections, Continue Reading row, list/grid toggle, and persistent sort/filter/search.
 */
class DocumentLibraryView(
    context: Context,
    private val scope: CoroutineScope,
    private val onImportClick: () -> Unit,
    private val onDocumentClick: (DocumentRecord) -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val repository = DocumentLibraryRepository(context)
    private val coverManager = DocumentCoverManager(context)
    private var observeJob: Job? = null
    private var tokens = if (NexusFeedEInkCoordinator.isEInkMode(context)) {
        NexusFeedEInkCoordinator.getTokens(context)
    } else {
        ThemeObserver.currentTokens(context)
    }

    private var rawDocuments = listOf<DocumentRecord>()
    private var collections = listOf<CollectionRecord>()
    private var searchQuery = ""

    private var activeSort = LibrarySortFilterStore.getSortMode(context)
    private var activeFilter = LibrarySortFilterStore.getFilterMode(context)
    private var activeCollectionId = LibrarySortFilterStore.getSelectedCollection(context)
    private var activeViewMode = LibrarySortFilterStore.getViewMode(context)

    private val headerComponents: DocumentLibraryHeaderHelper.HeaderComponents
    private val searchBar: DocumentLibrarySearchBar
    private val continueReadingView: DocumentLibraryContinueReadingView
    private val sortFilterBar: DocumentLibrarySortFilterBar
    private val listContainer: LinearLayout
    private val emptyView: LinearLayout

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)

        // 1. Top Action Row (Title + Grid/List Toggle + Add Button) via HeaderHelper
        headerComponents = DocumentLibraryHeaderHelper.buildHeader(
            context = context,
            tokens = tokens,
            isEInk = isEInk,
            activeViewMode = activeViewMode,
            dp = dp,
            onToggleViewMode = {
                activeViewMode = if (activeViewMode == LibrarySortFilterStore.ViewMode.LIST) {
                    LibrarySortFilterStore.ViewMode.GRID
                } else {
                    LibrarySortFilterStore.ViewMode.LIST
                }
                LibrarySortFilterStore.setViewMode(context, activeViewMode)
                DocumentLibraryHeaderHelper.updateHeader(headerComponents, tokens, NexusFeedEInkCoordinator.isEInkMode(context), activeViewMode, dp)
                applyFilterAndRender()
            },
            onImportClick = onImportClick
        )
        addView(headerComponents.rootLayout)

        // 2. Floating Search Pill directly below Library title
        searchBar = DocumentLibrarySearchBar(context, tokens, isEInk) { query ->
            searchQuery = query
            applyFilterAndRender()
        }
        addView(searchBar)

        // 3. Continue Reading Pinned Row
        continueReadingView = DocumentLibraryContinueReadingView(
            context = context,
            isEInk = isEInk,
            onDocumentClick = onDocumentClick,
            onDocumentLongClick = { doc -> showDocumentContextMenu(doc) }
        )
        addView(continueReadingView)

        // 4. Sort & Filter Bar
        sortFilterBar = DocumentLibrarySortFilterBar(
            context = context,
            isEInk = isEInk,
            onCollectionSelected = { colId ->
                activeCollectionId = colId
                LibrarySortFilterStore.setSelectedCollection(context, colId)
                applyFilterAndRender()
            },
            onCreateCollectionClick = {
                DocumentContextMenuHelper.showCreateCollectionDialog(context) { name, color ->
                    scope.launch { repository.createCollection(name, color) }
                }
            },
            onFilterSelected = { filter ->
                activeFilter = filter
                LibrarySortFilterStore.setFilterMode(context, filter)
                applyFilterAndRender()
            },
            onSortSelected = { sort ->
                activeSort = sort
                LibrarySortFilterStore.setSortMode(context, sort)
                applyFilterAndRender()
            }
        )
        addView(sortFilterBar)

        // 5. Document Cards Container
        listContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        addView(listContainer)

        // 6. Empty state
        emptyView = buildEmptyState()
        addView(emptyView)

        startObserving()
    }

    private fun buildEmptyState(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (40 * dp).toInt(), 0, (40 * dp).toInt())
            visibility = GONE

            val icon = ImageView(context).apply {
                setImageResource(R.drawable.ic_library)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                alpha = 0.6f
                layoutParams = LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                    bottomMargin = (10 * dp).toInt()
                }
            }
            addView(icon)

            val title = TextView(context).apply {
                text = context.getString(R.string.nexus_doc_empty_title)
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                gravity = Gravity.CENTER
            }
            addView(title)

            val desc = TextView(context).apply {
                text = context.getString(R.string.nexus_doc_empty_desc)
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * dp).toInt()
                }
            }
            addView(desc)
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = scope.launch {
            combine(repository.documents, repository.collections) { docs, cols ->
                Pair(docs, cols)
            }.collect { (docs, cols) ->
                rawDocuments = docs
                collections = cols
                withContext(Dispatchers.Main) {
                    applyFilterAndRender()
                }
            }
        }
    }

    fun applyTokens(newTokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens
        DocumentLibraryHeaderHelper.updateHeader(headerComponents, tokens, isEInk, activeViewMode, dp)
        searchBar.applyTokens(tokens, isEInk)
        applyFilterAndRender()
    }

    private fun applyFilterAndRender() {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        if (isEInk) {
            tokens = NexusFeedEInkCoordinator.getTokens(context)
        }
        DocumentLibraryHeaderHelper.updateHeader(headerComponents, tokens, isEInk, activeViewMode, dp)
        searchBar.applyTokens(tokens, isEInk)

        // Continue Reading is free: the Library's only limit is how many documents it holds.
        val inProgress = rawDocuments.filter { it.lastReadPosition > 0 }
            .sortedByDescending { it.lastReadTimestamp }
            .take(3)
        continueReadingView.bind(tokens, inProgress, coverManager) { doc, onReady ->
            loadCoverBitmap(doc, onReady)
        }

        // Update Sort & Filter Bar
        sortFilterBar.bind(tokens, collections, activeCollectionId, activeFilter, activeSort, isEInk)

        // Filter and sort via extracted engine
        val sorted = DocumentLibraryFilterEngine.filterAndSort(
            documents = rawDocuments,
            collections = collections,
            activeCollectionId = activeCollectionId,
            activeFilter = activeFilter,
            activeSort = activeSort,
            searchQuery = searchQuery
        )

        renderDocumentCards(sorted, isEInk)
    }

    private fun renderDocumentCards(docs: List<DocumentRecord>, isEInk: Boolean) {
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        DocumentLibraryCardsRenderer.render(
            context = context,
            docs = docs,
            collections = collections,
            viewMode = activeViewMode,
            tokens = tokens,
            dp = dp,
            isEInk = isEInk,
            isDark = isDark,
            coverManager = coverManager,
            listContainer = listContainer,
            emptyView = emptyView,
            onLoadCover = { d, cb -> loadCoverBitmap(d, cb) },
            onDocumentClick = onDocumentClick,
            onDocumentLongClick = { doc -> showDocumentContextMenu(doc) }
        )
    }

    private fun loadCoverBitmap(doc: DocumentRecord, onReady: (android.graphics.Bitmap?) -> Unit) {
        scope.launch {
            val bmp = coverManager.getOrGenerateCover(doc) { path ->
                scope.launch { repository.updateCoverPath(doc.uri, path) }
            }
            withContext(Dispatchers.Main) {
                onReady(bmp)
            }
        }
    }

    private fun showDocumentContextMenu(doc: DocumentRecord) {
        DocumentContextMenuHelper.showDocumentMenu(
            context = context,
            document = doc,
            collections = collections,
            onOpen = { onDocumentClick(doc) },
            onMove = { colId -> scope.launch { repository.updateCollection(doc.uri, colId) } },
            onCreateCollection = { name, color ->
                scope.launch {
                    val id = repository.createCollection(name, color)
                    repository.updateCollection(doc.uri, id)
                }
            },
            onRename = { newName -> scope.launch { repository.updateDisplayName(doc.uri, newName) } },
            onRemove = { scope.launch { repository.removeDocument(doc.uri) } }
        )
    }
}
