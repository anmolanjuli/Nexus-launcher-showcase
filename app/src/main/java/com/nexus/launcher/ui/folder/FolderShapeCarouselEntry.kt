package com.nexus.launcher.ui.folder

/** One row in the unified folder-shape carousel (section label or selectable shape). */
sealed class FolderShapeCarouselEntry {
    data class Section(val title: String) : FolderShapeCarouselEntry()
    data class Shape(val id: String, val title: String) : FolderShapeCarouselEntry()
}
