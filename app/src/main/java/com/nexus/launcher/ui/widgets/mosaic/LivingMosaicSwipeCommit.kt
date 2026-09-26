package com.nexus.launcher.ui.widgets.mosaic

/**
 * Mosaic internal horizontal settle (focus-mode widget carousel / multi-page mosaic).
 * Extracted from [LivingMosaicView] so that view stays under the 400-line cap.
 */
object LivingMosaicSwipeCommit {
    fun complete(view: LivingMosaicView, deltaX: Float) {
        val threshold = view.swipeAnimatorInternal.thresholdPx()
        val canPages = view.canSwipeMosaicPages()
        val canFocus = view.canSwipeMosaicFocus()
        val direction = when {
            canPages -> when {
                deltaX > threshold && view.currentConfig().pageIndex > 0 -> 1
                deltaX < -threshold &&
                    view.currentConfig().pageIndex < view.currentConfig().pages.lastIndex -> -1
                else -> 0
            }
            canFocus -> when {
                deltaX > threshold -> 1
                deltaX < -threshold -> -1
                else -> 0
            }
            else -> 0
        }
        view.swipeAnimatorInternal.settle(
            direction = direction,
            pageWidthPx = view.width.toFloat().coerceAtLeast(1f),
            onCommit = {
                val live = view.currentConfig()
                when {
                    canPages && direction == 1 -> view.setPage(live.pageIndex - 1)
                    canPages && direction == -1 -> view.setPage(live.pageIndex + 1)
                    canFocus && direction == 1 -> view.applyFocusIndex(live.focusIndex - 1)
                    canFocus && direction == -1 -> view.applyFocusIndex(live.focusIndex + 1)
                }
            },
            onDone = { },
            deepZoom = canFocus
        )
    }
}
