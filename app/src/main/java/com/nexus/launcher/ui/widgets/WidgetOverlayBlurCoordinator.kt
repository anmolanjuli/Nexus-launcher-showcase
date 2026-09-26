package com.nexus.launcher.ui.widgets

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Manages blur states and drawer progress fading/blurring for [WidgetOverlayLayout].
 *
 * The per-widget "exclusion" blur (everything blurred except the widget being edited, done by
 * moving the other widget views into a blurred group) was removed on 2026-09-24: widget and
 * Mosaic long-press, move and resize no longer blur the workspace at all.
 */
class WidgetOverlayBlurCoordinator(
    private val overlay: WidgetOverlayLayout
) {
    private var blurAnimator: ValueAnimator? = null
    var drawerProgress = 0f
        private set
    var workspaceBlurred = false
        private set
    private var currentBlurRadius = 0f
    private var lastDrawerBlurStep = -1

    fun setDrawerProgress(progress: Float) {
        drawerProgress = progress.coerceIn(0f, 1f)
        // Widgets are home: they follow home's 3D transition, not only its fade.
        overlay.canvasView?.let {
            com.nexus.launcher.ui.canvas.Drawer3DViewTransform.applyHome(overlay, it, drawerProgress)
        }
        overlay.translationY = 0f
        if (drawerProgress >= 1f) {
            overlay.visibility = View.INVISIBLE
            overlay.clipBounds = null
            return
        }
        overlay.visibility = View.VISIBLE
        overlay.clipBounds = null
        overlay.alpha = (1f - drawerProgress).coerceIn(0f, 1f)
        if (!workspaceBlurred) {
            if (drawerProgress <= 0f) {
                lastDrawerBlurStep = 0
                applyBlurImmediately(0f)
            } else {
                val blurStep = ((drawerProgress * 60f) / 3f).toInt()
                if (blurStep != lastDrawerBlurStep) {
                    lastDrawerBlurStep = blurStep
                    applyBlurImmediately(blurStep * 3f)
                }
            }
        }
    }

    fun setBlurState(isBlurred: Boolean) {
        if (workspaceBlurred == isBlurred) return
        workspaceBlurred = isBlurred
        if (isBlurred) {
            overlay.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        lastDrawerBlurStep = -1
        animateBlurTo(if (isBlurred) 70f else drawerProgress * 60f, if (isBlurred) 250L else 150L)
    }

    fun restoreHomePresentation() {
        drawerProgress = 0f
        lastDrawerBlurStep = -1
        overlay.alpha = 1f
        overlay.translationY = 0f
        overlay.clipBounds = null
        overlay.visibility = View.VISIBLE
        com.nexus.launcher.ui.canvas.Drawer3DViewTransform.clear(overlay)
        overlay.clearSelectionTransform()
        if (!workspaceBlurred) {
            blurAnimator?.cancel()
            applyBlurImmediately(0f)
        }
        if (!workspaceBlurred && overlay.layerType != View.LAYER_TYPE_NONE) {
            overlay.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun animateBlurTo(target: Float, durationMs: Long) {
        blurAnimator?.cancel()
        val start = currentBlurRadius
        if (start == target) return
        blurAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = durationMs
            addUpdateListener { applyBlurImmediately(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyBlurImmediately(radius: Float) {
        currentBlurRadius = radius.coerceAtLeast(0f)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (currentBlurRadius > 0.5f) {
            if (overlay.layerType != View.LAYER_TYPE_HARDWARE) {
                overlay.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            overlay.setRenderEffect(com.nexus.launcher.ui.glass.ChromeBackdrop.effect(currentBlurRadius))
        } else {
            overlay.setRenderEffect(null)
            if (!workspaceBlurred && overlay.layerType != View.LAYER_TYPE_NONE) {
                overlay.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            overlay.invalidate()
            overlay.post {
                if (!workspaceBlurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    overlay.setRenderEffect(null)
                    overlay.invalidate()
                }
            }
        }
    }
}
