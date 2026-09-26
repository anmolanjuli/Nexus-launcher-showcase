package com.nexus.launcher.ui.dock

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Hardware blur coordinator for [DockLayout] when Home Edit, Manage Pages,
 * or folder backdrops are active.
 */
internal class DockBlurCoordinator(private val dock: DockLayout) {

    private var isBlurred = false
    val isBlurActive: Boolean get() = isBlurred
    private var currentBlurRadius = 0f
    private var blurAnimator: ValueAnimator? = null

    fun setBlurState(blurred: Boolean) {
        if (isBlurred == blurred) return
        isBlurred = blurred
        DockBackgroundRenderer.isSuppressed = blurred
        dock.dockBackgroundInternal.isSuppressed = blurred
        if (blurred) {
            dock.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            dock.outlineProvider = null
            dock.clipToOutline = false
            dock.clipChildren = false
        } else {
            dock.applyCornerRadiusOutline()
            DockBackgroundRenderer.syncBackgroundLayer(dock)
        }
        dock.invalidate()
        val target = if (blurred) com.nexus.launcher.ui.glass.ChromeBackdrop.RADIUS_PX else 0f
        val durationMs = if (blurred) 250L else 150L
        animateBlurTo(target, durationMs)
    }

    private fun animateBlurTo(target: Float, durationMs: Long) {
        blurAnimator?.cancel()
        val start = currentBlurRadius
        if (start == target) return
        blurAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = durationMs
            addUpdateListener { va ->
                applyBlurImmediately(va.animatedValue as Float)
            }
            start()
        }
    }

    private fun applyBlurImmediately(radius: Float) {
        currentBlurRadius = radius.coerceAtLeast(0f)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (currentBlurRadius > 0.5f) {
            if (dock.layerType != View.LAYER_TYPE_HARDWARE) {
                dock.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            dock.setRenderEffect(com.nexus.launcher.ui.glass.ChromeBackdrop.effect(currentBlurRadius))
        } else {
            dock.setRenderEffect(null)
            if (!isBlurred && dock.layerType != View.LAYER_TYPE_NONE) {
                dock.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    fun detach() {
        blurAnimator?.cancel()
        blurAnimator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dock.setRenderEffect(null)
        }
        if (dock.layerType != View.LAYER_TYPE_NONE) {
            dock.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        DockBackgroundRenderer.isSuppressed = false
        dock.dockBackgroundInternal.isSuppressed = false
        dock.applyCornerRadiusOutline()
        DockBackgroundRenderer.syncBackgroundLayer(dock)
    }
}

