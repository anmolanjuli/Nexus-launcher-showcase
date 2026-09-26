package com.nexus.launcher.reader.doc

/**
 * Filter and sort engine for Document Library records.
 * Keeps business filtering logic separate from UI layout containers.
 */
object DocumentLibraryFilterEngine {

    fun filterAndSort(
        documents: List<DocumentRecord>,
        collections: List<CollectionRecord>,
        activeCollectionId: Long,
        activeFilter: LibrarySortFilterStore.FilterMode,
        activeSort: LibrarySortFilterStore.SortMode,
        searchQuery: String
    ): List<DocumentRecord> {
        // 1. Filter by collection
        var filtered = when (activeCollectionId) {
            LibrarySortFilterStore.COLLECTION_ALL -> documents
            LibrarySortFilterStore.COLLECTION_UNCATEGORIZED -> documents.filter { it.collectionId == null }
            else -> documents.filter { it.collectionId == activeCollectionId }
        }

        // 2. Filter by progress state
        filtered = when (activeFilter) {
            LibrarySortFilterStore.FilterMode.ALL -> filtered
            LibrarySortFilterStore.FilterMode.IN_PROGRESS -> filtered.filter {
                if (it.fileType == "epub") {
                    val chapter = it.lastReadPosition / 10000
                    it.lastReadPosition > 0 && (it.totalPages <= 0 || chapter + 1 < it.totalPages)
                } else {
                    it.lastReadPosition > 0 && (it.totalPages <= 0 || it.lastReadPosition + 1 < it.totalPages)
                }
            }
            LibrarySortFilterStore.FilterMode.FINISHED -> filtered.filter {
                if (it.fileType == "epub") {
                    val chapter = it.lastReadPosition / 10000
                    it.totalPages > 0 && chapter + 1 >= it.totalPages
                } else {
                    it.totalPages > 0 && it.lastReadPosition + 1 >= it.totalPages
                }
            }
            LibrarySortFilterStore.FilterMode.UNREAD -> filtered.filter { it.lastReadPosition == 0 }
        }

        // 3. Search query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            filtered = filtered.filter { doc ->
                doc.effectiveTitle.lowercase().contains(q) ||
                collections.find { it.id == doc.collectionId }?.name?.lowercase()?.contains(q) == true
            }
        }

        // 4. Sort
        return when (activeSort) {
            LibrarySortFilterStore.SortMode.LAST_READ -> filtered.sortedByDescending { it.lastReadTimestamp }
            LibrarySortFilterStore.SortMode.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedTimestamp }
            LibrarySortFilterStore.SortMode.TITLE_AZ -> filtered.sortedBy { it.effectiveTitle.lowercase() }
            LibrarySortFilterStore.SortMode.TITLE_ZA -> filtered.sortedByDescending { it.effectiveTitle.lowercase() }
            LibrarySortFilterStore.SortMode.FILE_SIZE -> filtered.sortedByDescending { it.fileSize }
            LibrarySortFilterStore.SortMode.READING_PROGRESS -> filtered.sortedByDescending {
                if (it.totalPages > 0) {
                    if (it.fileType == "epub") {
                        val chapterIndex = it.lastReadPosition / 10000
                        val scrollY = it.lastReadPosition % 10000
                        val scrollFraction = (scrollY.toFloat() / 3000f).coerceIn(0f, 0.95f)
                        (chapterIndex.toFloat() + scrollFraction) / it.totalPages.toFloat()
                    } else {
                        (it.lastReadPosition + 1).toFloat() / it.totalPages
                    }
                } else 0f
            }
        }
    }
}
