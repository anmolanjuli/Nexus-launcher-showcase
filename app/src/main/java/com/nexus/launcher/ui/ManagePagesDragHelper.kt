package com.nexus.launcher.ui

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import android.widget.GridLayout

/**
 * Long-press drag-and-drop reordering of Manage Pages thumbnail cards.
 * Add-card is always last and is never a drop target.
 */
object ManagePagesDragHelper {

    fun wireCard(
        card: View,
        pagesGrid: GridLayout,
        pageCount: () -> Int,
        onReorder: (from: Int, to: Int) -> Unit,
        onDragSettled: () -> Unit = {}
    ) {
        card.setOnLongClickListener { v ->
            val from = pagesGrid.indexOfChild(v)
            if (from < 0 || from >= pageCount()) return@setOnLongClickListener false
            v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).start()
            v.startDragAndDrop(
                ClipData.newPlainText("page", from.toString()),
                View.DragShadowBuilder(v),
                from,
                0
            )
            v.alpha = 0.4f
            true
        }

        card.setOnDragListener { target, event ->
            val count = pageCount()
            val targetIdx = pagesGrid.indexOfChild(target)
            if (targetIdx < 0 || targetIdx >= count) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    target.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80).start()
                    target.alpha = 0.7f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    target.alpha = 1f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    target.alpha = 1f
                    val from = event.localState as? Int ?: return@setOnDragListener false
                    if (from in 0 until count && from != targetIdx) {
                        onReorder(from, targetIdx)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    target.alpha = 1f
                    val from = event.localState as? Int
                    if (from != null && pagesGrid.indexOfChild(target) == from) {
                        onDragSettled()
                    }
                    true
                }
                else -> true
            }
        }
    }
}
