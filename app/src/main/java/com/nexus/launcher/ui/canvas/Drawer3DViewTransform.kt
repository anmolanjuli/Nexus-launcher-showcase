package com.nexus.launcher.ui.canvas

import android.graphics.Matrix
import android.os.Build
import android.view.View

/**
 * Carries the drawer transition's 3D motion onto the views that sit over the canvas.
 *
 * The canvas draws home and the drawer through [Drawer3DTransitionEngine]; widgets, the dock,
 * the drawer's search pill and overflow button and the Categories overlay are separate views,
 * and without this they only faded while the canvas behind them rolled or tilted away.
 *
 * `View.setAnimationMatrix` takes the engine's full perspective matrix and changes drawing only,
 * never hit-testing, which is right for a motion that ends before the next touch counts. The
 * matrix is applied in the view's parent space relative to its left/top, so the engine's
 * canvas-space matrix is conjugated by the view's offset from the canvas. Needs API 29; below
 * that the views keep fading as before (accepted 2026-09-23).
 */
object Drawer3DViewTransform {

    private val matrix = Matrix()
    private val parentLoc = IntArray(2)
    private val canvasLoc = IntArray(2)

    /** For views that belong to home: widgets, the dock. */
    fun applyHome(target: View, canvas: LauncherCanvasView, progress: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val active = Drawer3DTransitionEngine.homeApplies(progress) &&
            Drawer3DTransitionEngine.homeMatrix(
                canvas.drawerTransition, progress,
                canvas.viewWidth.toFloat(), canvas.viewHeight.toFloat(), matrix,
            )
        set(target, canvas, active)
    }

    /** For views that belong to the drawer: search pill, category bar, overflow, Categories overlay. */
    fun applyDrawer(target: View, canvas: LauncherCanvasView, progress: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val active = Drawer3DTransitionEngine.drawerApplies(progress) &&
            Drawer3DTransitionEngine.drawerMatrix(
                canvas.drawerTransition, progress,
                canvas.viewWidth.toFloat(), canvas.viewHeight.toFloat(), matrix,
            )
        set(target, canvas, active)
    }

    /** Back to flat — for paths that settle a view without going through a progress value. */
    fun clear(target: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        target.animationMatrix = null
    }

    private fun set(target: View, canvas: LauncherCanvasView, active: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!active) {
            target.animationMatrix = null
            return
        }
        val parent = target.parent as? View
        canvas.getLocationInWindow(canvasLoc)
        if (parent != null) parent.getLocationInWindow(parentLoc) else parentLoc.fill(0)
        val ox = (parentLoc[0] + target.left - canvasLoc[0]).toFloat()
        val oy = (parentLoc[1] + target.top - canvasLoc[1]).toFloat()
        // local -> canvas space, the engine's transform, then back to local.
        matrix.preTranslate(ox, oy)
        matrix.postTranslate(-ox, -oy)
        target.animationMatrix = matrix // copied into the view's render node
    }
}
