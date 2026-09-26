package com.nexus.launcher.reader.doc

import android.graphics.Bitmap
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies fit scaling, pan calculations, and Smart Crop bitmap processing for PDF reader views.
 */
object NexusPdfFitScaler {

    suspend fun processFitBitmap(
        source: Bitmap,
        mode: PdfPageFitStore.FitMode,
        dp: Float
    ): Bitmap = withContext(Dispatchers.IO) {
        if (mode == PdfPageFitStore.FitMode.SMART_CROP) {
            PdfSmartCropDetector.cropBitmap(source, dp)
        } else {
            source
        }
    }

    fun applyFitScale(
        view: ImageView,
        bmp: Bitmap?,
        mode: PdfPageFitStore.FitMode,
        viewWidth: Float,
        viewHeight: Float
    ): Float {
        if (bmp == null) {
            view.translationY = 0f
            return 0f
        }
        var maxPanY = 0f
        if (mode == PdfPageFitStore.FitMode.FIT_WIDTH) {
            val vW = viewWidth.coerceAtLeast(1f)
            val vH = viewHeight.coerceAtLeast(1f)
            val aspectBmp = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
            val aspectView = vH / vW
            if (aspectBmp > aspectView) {
                val scale = aspectBmp / aspectView
                view.scaleX = scale
                view.scaleY = scale
                maxPanY = ((scale - 1f) * vH) / 2f
            } else {
                view.scaleX = 1f
                view.scaleY = 1f
            }
        } else {
            view.scaleX = 1f
            view.scaleY = 1f
        }
        view.translationY = 0f
        return maxPanY
    }
}
