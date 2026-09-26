package com.nexus.launcher.ui.dock

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import com.nexus.launcher.ui.canvas.Drawer3DViewTransform
import com.nexus.launcher.ui.canvas.DrawerProgress
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/** Keeps [DockLayout] alpha/translation/blur in sync with the canvas drawer slide. */
class DockDrawerSync(private val view: LauncherCanvasView) {

    private val animatorListener = ValueAnimator.AnimatorUpdateListener { sync() }
    private var cachedDock: DockLayout? = null

    /** 0 = drawer closed (home), 1 = drawer fully open — mirrors LauncherDrawEngine progress. */
    fun drawerSlideProgress(): Float =
        DrawerProgress.of(view)

    fun sync() {
        val dock = cachedDock ?: DockLayout.findFrom(view)?.also { cachedDock = it } ?: return
        if (dock.freezeTranslation) {
            dock.alpha = 1f
            dock.visibility = View.VISIBLE
            dock.clipBounds = null
            Drawer3DViewTransform.clear(dock)
            return
        }

        if (isDragActive()) {
            dock.alpha = 1f
            dock.translationY = 0f
            dock.visibility = View.VISIBLE
            dock.clipBounds = null
            Drawer3DViewTransform.clear(dock)
            if (!dock.isBlurActive && dock.layerType != View.LAYER_TYPE_NONE) {
                dock.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            if (!dock.isBlurActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dock.setRenderEffect(null)
            }
            return
        }

        val progress = drawerSlideProgress()

        dock.visibility = if (progress >= 1f) View.GONE else View.VISIBLE
        dock.clipBounds = null
        dock.alpha = (1f - progress).coerceIn(0f, 1f)
        dock.translationY = 0f
        // The dock is home: it follows home's 3D transition, not only its fade.
        Drawer3DViewTransform.applyHome(dock, view, progress)

        if (!dock.isBlurActive) {
            val isAnimating = progress > 0f && progress < 1f
            val targetLayer = if (isAnimating) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
            if (dock.layerType != targetLayer) dock.setLayerType(targetLayer, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (progress <= 0f) {
                    dock.setRenderEffect(null)
                } else {
                    val blurStep = ((progress * 60f) / 3f).toInt()
                    val blurRadius = blurStep * 3f
                    dock.setRenderEffect(
                        com.nexus.launcher.ui.glass.ChromeBackdrop.effect(blurRadius)
                    )
                }
            }
        }
    }

    private fun isDragActive(): Boolean =
        view.draggedItem != null || view.dragHandler.isIconDragActive || view.isNativeDragActive

    fun attachToDrawerAnimator() {
        cachedDock = DockLayout.findFrom(view)
        sync()
        view.animator?.removeUpdateListener(animatorListener)
        view.animator?.addUpdateListener(animatorListener)
    }
}
