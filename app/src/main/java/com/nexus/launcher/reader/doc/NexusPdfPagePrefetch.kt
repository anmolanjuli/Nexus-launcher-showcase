package com.nexus.launcher.reader.doc

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rendering for the page-turn reader: the page being read, and the two either side of it.
 *
 * The neighbours are rendered and fit-processed before they are asked for, because a page turn
 * follows the finger and has to show the next page from its first millimetre. That work used to
 * happen after the swipe was over, which is why a turn could only ever be instant.
 */
internal class NexusPdfPagePrefetch(
    private val scope: CoroutineScope,
    private val engine: NexusPdfRendererEngine,
    private val dp: Float,
) {
    private var prebufferJob: Job? = null

    /**
     * Renders [page] and hands it over on the main thread. [scale] is the zoom the page has to
     * stand up to: it is rendered that many times larger and fitted back into the view, so zooming
     * reveals detail that was rendered rather than magnified screen pixels.
     */
    fun render(
        page: Int,
        width: Int,
        height: Int,
        scale: Float,
        fit: PdfPageFitStore.FitMode,
        onReady: (Bitmap?) -> Unit,
    ) {
        val renderWidth = (width * scale).toInt().coerceAtLeast(1)
        val renderHeight = (height * scale).toInt().coerceAtLeast(1)
        scope.launch {
            val bmp = renderFitted(page, renderWidth, renderHeight, fit)
            withContext(Dispatchers.Main) { onReady(bmp) }
        }
    }

    /** The pages either side of [page], ready for a drag to pick up. */
    fun prebuffer(
        page: Int,
        width: Int,
        height: Int,
        fit: PdfPageFitStore.FitMode,
        onReady: (previous: Bitmap?, next: Bitmap?) -> Unit,
    ) {
        prebufferJob?.cancel()
        prebufferJob = scope.launch(Dispatchers.IO) {
            val next = renderFitted(page + 1, width, height, fit)
            val previous = renderFitted(page - 1, width, height, fit)
            withContext(Dispatchers.Main) { onReady(previous, next) }
        }
    }

    fun cancel() {
        prebufferJob?.cancel()
        prebufferJob = null
    }

    private suspend fun renderFitted(page: Int, width: Int, height: Int, fit: PdfPageFitStore.FitMode): Bitmap? {
        if (page !in 0 until engine.pageCount) return null
        val (_, pageHeight) = engine.getPageDimensions(page, width)
        val bmp = engine.renderPage(page, width, pageHeight.coerceAtMost(height)) ?: return null
        return NexusPdfFitScaler.processFitBitmap(bmp, fit, dp)
    }
}
