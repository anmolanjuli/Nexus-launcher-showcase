package com.nexus.launcher.ui.canvas

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup

class CanvasOverlayManager(private val canvasView: LauncherCanvasView) {
    var showDarkOverlay: Boolean = false
    private var blurBgView: View? = null
    /** True while a blur overlay is requested — used to upgrade solid fallback once wallpaper loads. */
    private var blurWanted: Boolean = false
    /** Gallery/Gradient/Solid blur radius animator — mirrors FolderBlurCoordinator pattern. */
    private var blurAnimator: ValueAnimator? = null
    /** Tracks the current blur radius to smoothly resume interrupted animations. */
    private var currentBlurRadius = 0f

    companion object {
        private val BLUR_RADIUS = com.nexus.launcher.ui.glass.ChromeBackdrop.RADIUS_PX
        private const val BLUR_IN_MS = 250L
        private const val BLUR_OUT_MS = 150L
    }

    /** Cancels in-flight Gallery blur animation so folder/context-menu blur can own RenderEffect. */
    fun cancelBlurAnimator() {
        blurAnimator?.cancel()
        blurAnimator = null
    }

    fun setBlurState(isBlurred: Boolean) {
        if (blurWanted == isBlurred) {
            return
        }
        applyBlurState(isBlurred)
    }

    /**
     * Bypasses the `blurWanted == isBlurred` early-return [setBlurState] normally uses to avoid
     * redundant animation restarts — for the one case where that cached flag can't be trusted:
     * resuming from background after it may have gone stale (e.g. an interrupted animation while
     * this Activity wasn't in the foreground, such as launching a third-party wallpaper app from
     * the Wallpaper sheet's "Others" tab and returning). Also skips the animated transition
     * entirely and jumps straight to the final RenderEffect state, since this is a correctness
     * resync, not a user-facing open/close transition that should read as a smooth blur-in/out.
     */
    fun forceSyncBlurState(isBlurred: Boolean) {
        cancelBlurAnimator()
        currentBlurRadius = if (isBlurred) BLUR_RADIUS else 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canvasView.setRenderEffect(
                com.nexus.launcher.ui.glass.ChromeBackdrop.effect(if (isBlurred) BLUR_RADIUS else 0f)
            )
        }
        applyBlurState(isBlurred, skipAnimation = true)
    }

    private fun applyBlurState(isBlurred: Boolean, skipAnimation: Boolean = false) {
        blurWanted = isBlurred
        canvasView.canvasRenderer.isWorkspaceBlurred = isBlurred
        removeBlurBgView()

        if (!skipAnimation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cancelBlurAnimator()
            animateCanvasBlur(enable = isBlurred)
        }

        // Legacy dark overlay only while a folder is open (avoid double-dim elsewhere)
        showDarkOverlay = isBlurred &&
            (canvasView.openFolderItemId != null || canvasView.isFolderClosing)
        canvasView.invalidate()
    }

    /** ValueAnimator progression for canvas RenderEffect. */
    private fun animateCanvasBlur(enable: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val startRadius = currentBlurRadius
        val endRadius = if (enable) BLUR_RADIUS else 0f
        if (startRadius == endRadius) return
        
        val progressFraction = Math.abs(endRadius - startRadius) / BLUR_RADIUS
        val maxDuration = if (enable) 120L else 100L
        val scaledDuration = (maxDuration * progressFraction).toLong().coerceAtLeast(0L)

        blurAnimator = ValueAnimator.ofFloat(startRadius, endRadius).apply {
            duration = scaledDuration
            addUpdateListener { va ->
                val radius = va.animatedValue as Float
                currentBlurRadius = radius
                com.nexus.launcher.ui.glass.ChromeBackdrop.applyTo(canvasView, radius)
            }
            start()
        }
    }

    fun refreshBlurBackgroundIfNeeded() {
        // Native window.setBackgroundBlurRadius handles wallpaper blur directly without bitmap churn.
    }

    private fun removeBlurBgView() {
        blurBgView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        blurBgView = null
    }

    fun drawOverlayIfNeeded(canvas: Canvas, width: Int, height: Int) {
        if (showDarkOverlay && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val fillPaint = Paint().apply {
                color = Color.parseColor("#33000000")
                style = Paint.Style.FILL
            }
            val strokePaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * canvasView.resources.displayMetrics.density
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), strokePaint)
        }
    }
}
