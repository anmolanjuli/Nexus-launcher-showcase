package com.nexus.launcher.ui.widgets

import android.graphics.Outline
import android.graphics.RectF
import kotlin.math.min

/**
 * Where a widget's plate actually sits for each shape — the one definition the renderer, the host
 * view's clip and the glass backdrop all read.
 *
 * ## Why this exists
 *
 * Shape 0 has long been half-supported as a circle: the content inset handled it and the shape
 * path drew `addOval`. But the renderer drew that oval over the *whole* widget rectangle — an
 * ellipse on anything not square — while the host and backdrop clipped it as a capsule. The three
 * only agreed on a square widget. A circle is now always a real circle: centred, its diameter the
 * widget's shorter side, identical in every layer, with the corners outside it left empty.
 */
object NexusWidgetShapeGeometry {

    const val SHAPE_CIRCLE = 0
    const val SHAPE_SQUIRCLE = 1
    const val SHAPE_SQUARE = 2
    const val SHAPE_PILL = 11

    /**
     * Widgets whose content is built around a centred subject. Circle is offered only to these: a
     * month grid, an agenda or a notes card laid out inside a circle loses most of its content.
     */
    private val circleCapable = setOf(
        "com.nexus.launcher.ui.widgets.battery.NexusBatteryWidgetProvider",
        "com.nexus.launcher.ui.widgets.clock.NexusClockWidgetProvider",
        "com.nexus.launcher.ui.widgets.weather.NexusWeatherWidgetProvider",
    )

    fun supportsCircle(providerClassName: String?): Boolean = providerClassName in circleCapable

    /** The plate within a [w] × [h] widget: a centred square for a circle, the whole widget otherwise. */
    fun plate(shapeStyle: Int, w: Float, h: Float): RectF {
        if (shapeStyle != SHAPE_CIRCLE) return RectF(0f, 0f, w, h)
        val d = min(w, h)
        val l = (w - d) / 2f
        val t = (h - d) / 2f
        return RectF(l, t, l + d, t + d)
    }

    /**
     * Writes the clip outline for a widget occupying [left]..[right] × [top]..[bottom] — the host
     * view and the glass backdrop both come through here, so they cannot disagree with the plate.
     */
    fun outline(outline: Outline, shapeStyle: Int, left: Int, top: Int, right: Int, bottom: Int, cornerPx: Float) {
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return
        when (shapeStyle) {
            SHAPE_CIRCLE -> {
                val d = min(w, h)
                val l = left + (w - d) / 2
                val t = top + (h - d) / 2
                outline.setOval(l, t, l + d, t + d)
            }
            SHAPE_PILL -> outline.setRoundRect(left, top, right, bottom, min(w, h) / 2f)
            SHAPE_SQUARE -> outline.setRoundRect(left, top, right, bottom, 0f)
            else -> outline.setRoundRect(left, top, right, bottom, cornerPx.coerceAtLeast(0f))
        }
    }
}
