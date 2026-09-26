package com.nexus.launcher.ui.widgets

/**
 * Keeps a widget's [WidgetGlassLiveBackdropView] sibling visually tracking the widget during an
 * active interactive drag.
 *
 * The widget itself moves via `translationX`/`translationY` while dragging — cheap, no relayout
 * per touch-move frame (see [NexusWidgetView]'s `ACTION_MOVE` handling). The backdrop sibling only
 * gets its `layoutParams` re-synced on drop, via [WidgetOverlayLayoutParams.update] — so without
 * this, it visibly stayed behind at its pre-drag position for the whole gesture (reported as "the
 * frosted section stays in the previous position... merges with the widget when I drop it"), since
 * nothing was moving it in step with the widget's live translation. Mirroring that same
 * translation onto the backdrop keeps the two visually locked together throughout the drag; the
 * final drop still goes through the normal layoutParams sync, so [clearDragTranslation] just needs
 * to zero this temporary translation out first so it doesn't stack on top of the new position.
 */
object WidgetGlassBackdropDragSync {

    fun setDragTranslation(overlay: WidgetOverlayLayout, appWidgetId: Int, dx: Float, dy: Float) {
        findBackdrop(overlay, appWidgetId)?.let {
            it.translationX = dx
            it.translationY = dy
        }
    }

    fun clearDragTranslation(overlay: WidgetOverlayLayout, appWidgetId: Int) {
        findBackdrop(overlay, appWidgetId)?.let {
            it.translationX = 0f
            it.translationY = 0f
        }
    }

    private fun findBackdrop(overlay: WidgetOverlayLayout, appWidgetId: Int): WidgetGlassLiveBackdropView? {
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is WidgetGlassLiveBackdropView && child.appWidgetId == appWidgetId) return child
        }
        return null
    }
}
