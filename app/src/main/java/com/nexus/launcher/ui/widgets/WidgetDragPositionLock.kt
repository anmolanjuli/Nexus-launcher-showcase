package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.graphics.Matrix
import android.view.View

/** Keeps a lifted child's centre fixed while its parent scrolls, scales or rebases pages. */
internal class WidgetDragPositionLock {
    private val before = FloatArray(2)
    private val after = FloatArray(2)
    private val inverse = Matrix()
    private var captured: View? = null

    fun capture(overlay: WidgetOverlayLayout) {
        val child = overlay.draggingWidgetView
        captured = child
        if (child == null) return
        centre(child, before)
        before[0] += child.left - overlay.scrollX
        before[1] += child.top - overlay.scrollY
        overlay.matrix.mapPoints(before)
    }

    fun restore(overlay: WidgetOverlayLayout) {
        val child = captured ?: return
        captured = null
        if (child !== overlay.draggingWidgetView || !overlay.matrix.invert(inverse)) return
        inverse.mapPoints(before)
        centre(child, after)
        child.translationX += before[0] - (child.left - overlay.scrollX + after[0])
        child.translationY += before[1] - (child.top - overlay.scrollY + after[1])
        if (child is AppWidgetHostView) {
            WidgetGlassBackdropDragSync.setDragTranslation(
                overlay, child.appWidgetId, child.translationX, child.translationY
            )
        }
    }

    private fun centre(child: View, point: FloatArray) {
        point[0] = child.width / 2f
        point[1] = child.height / 2f
        child.matrix.mapPoints(point)
    }
}
