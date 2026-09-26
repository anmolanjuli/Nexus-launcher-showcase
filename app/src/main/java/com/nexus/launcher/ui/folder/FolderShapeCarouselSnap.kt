package com.nexus.launcher.ui.folder

import android.view.View
import android.widget.LinearLayout
import kotlin.math.abs

/** Center-snap and focus scaling for [FolderShapeThumbnailRow]. */
object FolderShapeCarouselSnap {

    fun updateSideSpacers(
        startSpacer: View,
        endSpacer: View,
        viewportWidth: Int,
        tileWidth: Int,
        tileMargin: Int
    ) {
        val side = ((viewportWidth - tileWidth) / 2 - tileMargin).coerceAtLeast(0)
        (startSpacer.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            if (lp.width != side) {
                lp.width = side
                startSpacer.layoutParams = lp
            }
        }
        (endSpacer.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            if (lp.width != side) {
                lp.width = side
                endSpacer.layoutParams = lp
            }
        }
    }

    fun scrollXToCenter(tile: View, viewportWidth: Int): Int {
        val tileCenter = tile.left + tile.width / 2f
        return (tileCenter - viewportWidth / 2f).toInt().coerceAtLeast(0)
    }

    fun nearestShapeTile(shapeTiles: List<View>, scrollX: Int, viewportWidth: Int): View? {
        if (shapeTiles.isEmpty() || viewportWidth <= 0) return null
        val focusX = scrollX + viewportWidth / 2f
        return shapeTiles.minByOrNull { tile ->
            val center = tile.left + tile.width / 2f
            abs(center - focusX)
        }
    }

    fun applyFocusScaling(shapeTiles: List<View>, scrollX: Int, viewportWidth: Int) {
        if (viewportWidth <= 0) return
        val focusX = scrollX + viewportWidth / 2f
        val maxDist = viewportWidth / 2f
        shapeTiles.forEach { tile ->
            val center = tile.left + tile.width / 2f
            val dist = abs(focusX - center)
            val fraction = (1f - (dist / maxDist) * 0.35f).coerceIn(0.72f, 1f)
            tile.alpha = if (fraction > 0.95f) 1f else 0.78f + (fraction - 0.72f) * 0.95f
            tile.scaleX = fraction
            tile.scaleY = fraction
        }
    }
}
