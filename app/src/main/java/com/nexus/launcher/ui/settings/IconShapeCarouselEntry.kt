package com.nexus.launcher.ui.settings

/** One row in the icon-shape carousel (section label or selectable shape). */
sealed class IconShapeCarouselEntry {
    data class Section(val title: String) : IconShapeCarouselEntry()
    data class Shape(val id: Int, val title: String) : IconShapeCarouselEntry()
}
