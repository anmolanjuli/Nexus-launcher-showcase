package com.nexus.launcher.reader.doc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Bitmap
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import kotlinx.coroutines.launch

/**
 * Handles horizontal page transition slide animations for [NexusPdfHorizontalPageView].
 */
object NexusPdfPageSlideAnimator {

    /**
     * Renders [page] and slides it in — the instant turn a tap zone or a jump asks for, as opposed
     * to the finger-led one in [NexusPdfPageDragTurn].
     */
    fun slideToPage(
        scope: kotlinx.coroutines.CoroutineScope,
        engine: NexusPdfRendererEngine,
        active: ImageView,
        incoming: ImageView,
        page: Int,
        forward: Boolean,
        fitMode: PdfPageFitStore.FitMode,
        dp: Float,
        targetWidth: Int,
        targetHeight: Int,
        onBitmapReady: (Bitmap?) -> Unit,
        onDone: (Bitmap?) -> Unit,
    ) {
        scope.launch {
            val (_, ph) = engine.getPageDimensions(page, targetWidth)
            val raw = engine.renderPage(page, targetWidth, ph.coerceAtMost(targetHeight))
            val bmp = if (raw != null) NexusPdfFitScaler.processFitBitmap(raw, fitMode, dp) else null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onBitmapReady(bmp)
                animateSlide(active, incoming, bmp, active.width.toFloat(), forward) { onDone(bmp) }
            }
        }
    }

    fun animateSlide(
        activeImageView: ImageView,
        incomingImageView: ImageView,
        bmp: Bitmap?,
        slideDistance: Float,
        forward: Boolean,
        onEnd: () -> Unit
    ) {
        if (bmp != null) {
            incomingImageView.setImageBitmap(bmp)
        }
        incomingImageView.visibility = View.VISIBLE

        val startX = if (forward) slideDistance else -slideDistance
        incomingImageView.translationX = startX

        incomingImageView.animate()
            .translationX(0f)
            .setDuration(200)
            .setInterpolator(LinearInterpolator())
            .start()

        activeImageView.animate()
            .translationX(if (forward) -slideDistance else slideDistance)
            .setDuration(200)
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    activeImageView.animate().setListener(null)
                    activeImageView.setImageBitmap(bmp)
                    activeImageView.translationX = 0f
                    incomingImageView.visibility = View.GONE
                    onEnd()
                }
            })
            .start()
    }
}
