package com.nexus.launcher.ui.widgets

import kotlin.math.roundToInt

/**
 * Single source for free-size (wFrac/hFrac) pixel bounds.
 * wFrac is relative to page/view width; hFrac to overlay height (same basis as yFraction).
 */
object WidgetFreeSize {

    const val KEY_W = "wFrac"
    const val KEY_H = "hFrac"

    fun readFracs(folderConfigJson: String?): Pair<Float, Float> {
        val json = try {
            org.json.JSONObject(folderConfigJson ?: "{}")
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        return json.optDouble(KEY_W, -1.0).toFloat() to
            json.optDouble(KEY_H, -1.0).toFloat()
    }

    /**
     * Pages whose free-sized widgets have to be capped in the short orientation, set by
     * [com.nexus.launcher.ui.ShapeLayoutDeriver] when a page cannot hold what it is asked to.
     */
    @Volatile
    var cappedPages: Set<Int> = emptySet()

    /** The most of the screen's height one free-sized widget may take on a capped page. */
    const val CAP_FRACTION = 0.45f

    fun pixelSize(
        folderConfigJson: String?,
        spanX: Int,
        spanY: Int,
        pageWidth: Float,
        overlayHeight: Float,
        cellW: Float,
        cellH: Float,
        isLandscape: Boolean = false,
        page: Int = -1,
    ): Pair<Int, Int> {
        val (wFrac, hFrac) = readFracs(folderConfigJson)
        // Free sizes are fractions of the PORTRAIT screen (width of the short side, height of
        // the long side), in every orientation. In landscape the page width and overlay height
        // simply swap roles, so this gives a free-sized widget exactly its portrait pixel size —
        // no size change on rotation, so providers never re-render it.
        val portraitWidth = minOf(pageWidth, overlayHeight)
        val portraitHeight = maxOf(pageWidth, overlayHeight)
        var width = if (wFrac > 0f) {
            (wFrac * portraitWidth).roundToInt()
        } else {
            (spanX * cellW).roundToInt()
        }
        var height = if (hFrac > 0f) {
            (hFrac * portraitHeight).roundToInt()
        } else {
            (spanY * cellH).roundToInt()
        }
        // Keeping the portrait size is right until it stops being possible. A 529px and a 494px
        // widget need 1023px of a 1080px landscape screen, so they cannot both be on it at their
        // portrait size, whatever the packing does. On a page that has been found not to fit, a
        // free-sized widget gives up what it has to — and only there, so a widget that has room
        // still keeps its exact size and its provider still never re-renders on rotation.
        val free = wFrac > 0f || hFrac > 0f
        if (free && page in cappedPages && (isLandscape || pageWidth > overlayHeight)) {
            if (hFrac > 0f) height = minOf(height, (overlayHeight * CAP_FRACTION).roundToInt())
            // And it is snapped to whole cells, so what it is drawn at is exactly what was
            // reserved for it. A 615px widget on a 180px pitch covers 3.4 columns: three are
            // reserved and the other four tenths are taken out of its neighbour.
            width = snapped(width, cellW)
            height = snapped(height, cellH)
        }
        return width.coerceAtLeast(1) to height.coerceAtLeast(1)
    }

    /** The nearest whole number of cells, never larger than what was asked for. */
    private fun snapped(size: Int, cell: Float): Int {
        if (cell <= 0f) return size
        val cells = (size / cell).roundToInt().coerceAtLeast(1)
        return minOf(size, (cells * cell).roundToInt())
    }
}
