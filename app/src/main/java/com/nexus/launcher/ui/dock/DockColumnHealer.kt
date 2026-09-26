package com.nexus.launcher.ui.dock

import com.nexus.launcher.data.HomeScreenItem

/**
 * Keeps the dock's columns contiguous: 0, 1, 2 … with no holes.
 *
 * The dock sizes its pill (and hit-tests) by how many icons it has, but draws each icon at its own
 * column. So a hole — `1, 2, 3` with nothing at 0 — drew a pill three slots wide with the icons
 * shifted a slot right: an empty end on the left, the last icon hanging past the right edge.
 * Removals renumber only on the drag-and-drop path; uninstall sync, the dock's Remove, a deletion
 * and ghost cleanup all delete a row and leave the hole. Healing where the dock receives its items
 * covers every one of those at once.
 *
 * Only renumbers: unlike `DockItemMover.reindexDockCollapse`, nothing past the icon cap is dropped,
 * so a second dock page keeps its icons.
 */
internal object DockColumnHealer {

    fun hasGaps(items: List<HomeScreenItem>): Boolean =
        items.sortedBy { it.column }.withIndex().any { (index, item) -> item.column != index }

    /** [items] renumbered in their current order, with the slot fraction the dock stores. */
    fun contiguous(items: List<HomeScreenItem>, maxDockIcons: Int): List<HomeScreenItem> {
        val span = 1f / maxDockIcons.coerceAtLeast(1)
        return items.sortedBy { it.column }.mapIndexed { index, item ->
            if (item.column == index) item
            else item.copy(column = index, row = 0, xFraction = index * span + span / 2f, yFraction = 0.5f)
        }
    }
}
