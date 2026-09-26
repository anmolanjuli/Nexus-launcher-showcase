package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenItem

/** Resolves folder child apps using referenceFolderId when present. */
object FolderContentsResolver {

    fun contentsId(item: HomeScreenItem): Long = item.resolveFolderContentsId()

    fun fromMap(
        map: Map<Long, List<HomeScreenItem>>,
        item: HomeScreenItem
    ): List<HomeScreenItem> = map[contentsId(item)] ?: emptyList()

    fun sortedForDisplay(items: List<HomeScreenItem>): List<HomeScreenItem> =
        items.sortedWith(compareBy({ it.row }, { it.column }, { it.id }))
}
