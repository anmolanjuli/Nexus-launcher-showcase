package com.nexus.launcher.ui.widgets.mosaic

import android.graphics.RectF
import android.view.View
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/** Hit-test mosaics for drop-absorb from home widgets / picker. */
object LivingMosaicDropHelper {

    fun findMosaicAtScreenPoint(
        overlay: WidgetOverlayLayout,
        screenX: Float,
        screenY: Float
    ): Pair<HomeScreenItem, LivingMosaicView>? {
        val loc = IntArray(2)
        val density = overlay.resources.displayMetrics.density
        val safeMargin = 12f * density
        for (i in overlay.childCount - 1 downTo 0) {
            val child = overlay.getChildAt(i)
            if (child !is LivingMosaicView) continue
            val item = child.currentItem() ?: continue
            if (item.itemType != HomeItemTypes.MOSAIC) continue
            child.getLocationOnScreen(loc)
            val insetX = safeMargin.coerceAtMost(child.width * 0.2f)
            val insetY = safeMargin.coerceAtMost(child.height * 0.2f)
            if (screenX >= loc[0] + insetX && screenX < loc[0] + child.width - insetX &&
                screenY >= loc[1] + insetY && screenY < loc[1] + child.height - insetY
            ) {
                return item to child
            }
        }
        return null
    }

    fun findMosaicUnderViewCenter(overlay: WidgetOverlayLayout, view: View): Pair<HomeScreenItem, LivingMosaicView>? {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val cx = loc[0] + view.width / 2f
        val cy = loc[1] + view.height / 2f
        return findMosaicAtScreenPoint(overlay, cx, cy)
    }

    /**
     * Hit-tests a dragged widget view against living mosaics on the overlay.
     *
     * To prevent accidental swallowing when hovering nearby or dragging an adjacent/restored widget,
     * requires intentional placement:
     * 1. The dragged widget's center point must be inside the mosaic's safe zone (inset by 16dp).
     * OR
     * 2. The dragged widget's bounding box must substantially overlap the mosaic (at least 40%
     *    of the dragged widget's area) AND its center must be inside the mosaic bounds.
     */
    fun findMosaicOverlappingView(
        overlay: WidgetOverlayLayout,
        view: View
    ): Pair<HomeScreenItem, LivingMosaicView>? {
        if (view.width <= 0 || view.height <= 0) return null
        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val cx = viewLocation[0] + view.width / 2f
        val cy = viewLocation[1] + view.height / 2f
        val draggedBounds = RectF(
            viewLocation[0].toFloat(),
            viewLocation[1].toFloat(),
            (viewLocation[0] + view.width).toFloat(),
            (viewLocation[1] + view.height).toFloat()
        )
        val draggedArea = draggedBounds.width() * draggedBounds.height()
        val density = view.resources.displayMetrics.density
        val safeMargin = 16f * density

        val mosaicLocation = IntArray(2)
        val intersection = RectF()

        for (i in overlay.childCount - 1 downTo 0) {
            val child = overlay.getChildAt(i)
            if (child !is LivingMosaicView) continue
            val item = child.currentItem() ?: continue
            if (item.itemType != HomeItemTypes.MOSAIC) continue
            if (child.width <= 0 || child.height <= 0) continue

            child.getLocationOnScreen(mosaicLocation)
            val ml = mosaicLocation[0].toFloat()
            val mt = mosaicLocation[1].toFloat()
            val mr = ml + child.width
            val mb = mt + child.height

            val safeLeft = ml + safeMargin.coerceAtMost(child.width * 0.25f)
            val safeTop = mt + safeMargin.coerceAtMost(child.height * 0.25f)
            val safeRight = mr - safeMargin.coerceAtMost(child.width * 0.25f)
            val safeBottom = mb - safeMargin.coerceAtMost(child.height * 0.25f)

            val centerInSafeZone = cx in safeLeft..safeRight && cy in safeTop..safeBottom
            val centerInBounds = cx in ml..mr && cy in mt..mb

            if (centerInSafeZone) {
                return item to child
            }

            if (centerInBounds && intersection.setIntersect(draggedBounds, RectF(ml, mt, mr, mb))) {
                val overlapArea = intersection.width() * intersection.height()
                if (draggedArea > 0f && (overlapArea / draggedArea) >= 0.40f) {
                    return item to child
                }
            }
        }
        return null
    }

    fun canAcceptChild(mosaic: HomeScreenItem): Boolean {
        val cfg = MosaicConfig.parse(mosaic.folderConfigJson)
        return cfg.currentChildren().size < MosaicConfig.MAX_CHILDREN_PER_PAGE
    }
}
