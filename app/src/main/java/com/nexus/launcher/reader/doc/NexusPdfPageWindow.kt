package com.nexus.launcher.reader.doc

import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Which pages of a continuously scrolling PDF are holding bitmaps, and which have been measured.
 *
 * A document has one [ImageView] per page, but only the handful near the viewport may hold a
 * rendered page: about 6MB each at phone width, more when zoomed, inside the launcher's own
 * process. Everything else is handed back. The [NexusPdfPageView] says which range is near; this
 * decides what that costs.
 *
 * Page heights start as an estimate taken from one page ([markMeasured]) and are corrected here,
 * lazily, as pages come into view — measuring every page of a 500-page document up front is 500
 * round trips into the renderer before the first frame.
 */
internal class NexusPdfPageWindow(
    private val scope: CoroutineScope,
    private val engine: NexusPdfRendererEngine,
    private val pages: List<ImageView>,
    private val baseHeights: MutableList<Int>,
    private val baseWidth: Int,
    private val dp: Float,
    private val fitMode: () -> PdfPageFitStore.FitMode,
    /** Called on the main thread when a page's real height differs from the estimate. */
    private val heightCorrected: (index: Int, measured: Int) -> Unit,
) {
    private val bound = mutableSetOf<Int>()
    private val measured = mutableSetOf<Int>()
    private val jobs = mutableMapOf<Int, Job>()

    /** The page the estimate was taken from is already its own size. */
    fun markMeasured(index: Int) {
        measured.add(index)
    }

    /**
     * Renders [index] into its view, once, at [zoom].
     *
     * A page already showing its bitmap is left alone: this runs on every scroll frame, and it
     * used to re-run Smart Crop each time — a downscale, a pixel scan and a second full-size copy
     * of the page, per page, per frame.
     */
    fun load(index: Int, zoom: Float) {
        if (index !in pages.indices) return
        if (index in bound) return
        if (jobs[index]?.isActive == true) return
        val view = pages[index]
        val renderZoom = zoom.coerceAtMost(NexusPdfScrollZoom.MAX_RENDER_ZOOM)

        jobs[index] = scope.launch {
            ensureMeasured(index)
            val width = (baseWidth * renderZoom).roundToInt().coerceAtLeast(1)
            val height = (baseHeights.getOrElse(index) { baseWidth } * renderZoom).roundToInt().coerceAtLeast(1)
            val bmp = engine.renderPage(index, width, height)
            val finalBmp = if (bmp != null) NexusPdfFitScaler.processFitBitmap(bmp, fitMode(), dp) else null
            withContext(Dispatchers.Main) {
                if (finalBmp != null) {
                    view.setImageBitmap(finalBmp)
                    bound.add(index)
                }
            }
        }
    }

    /**
     * Gives back every page outside [first]..[last]. An ImageView holds its bitmap until told
     * otherwise, so without this every page scrolled past stayed in memory.
     */
    fun releaseOutside(first: Int, last: Int) {
        if (first < 0 || last < 0) return
        val held = bound.iterator()
        while (held.hasNext()) {
            val index = held.next()
            if (index in first..last) continue
            pages.getOrNull(index)?.setImageBitmap(null)
            held.remove()
        }
        val running = jobs.entries.iterator()
        while (running.hasNext()) {
            val entry = running.next()
            if (entry.key in first..last) continue
            entry.value.cancel()
            running.remove()
        }
    }

    fun releaseAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        bound.forEach { pages.getOrNull(it)?.setImageBitmap(null) }
        bound.clear()
    }

    /**
     * Marks every page as needing a fresh render without taking away what it is showing — for a
     * zoom change, where the pages a reader is looking at should stay visible, stretched, until
     * the sharper renders land.
     */
    fun invalidateRenders() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        bound.clear()
    }

    /** Replaces [index]'s estimated height with its own, the first time it is needed. */
    private suspend fun ensureMeasured(index: Int) {
        if (index in measured || index !in baseHeights.indices) return
        val (_, height) = engine.getPageDimensions(index, baseWidth)
        measured.add(index)
        val estimate = baseHeights.getOrNull(index) ?: return
        if (height <= 0 || height == estimate) return
        withContext(Dispatchers.Main) { heightCorrected(index, height) }
    }
}
