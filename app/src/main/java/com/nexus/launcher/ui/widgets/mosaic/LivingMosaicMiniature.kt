package com.nexus.launcher.ui.widgets.mosaic

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Mosaic child sizing — always matches standalone host behavior:
 * host bounds = tile, AppWidget options = tile dp, no View scale-down.
 * RemoteViews reflow to the cell (packed or Focus), same as a free-sized home widget.
 */
object LivingMosaicMiniature {

    private val lastOptionsPx = WeakHashMap<View, Long>()

    data class Placement(
        val naturalW: Int,
        val naturalH: Int,
        val scale: Float,
        val left: Int,
        val top: Int
    )

    fun placement(
        tileLeft: Int,
        tileTop: Int,
        tileW: Int,
        tileH: Int
    ): Placement = Placement(tileW, tileH, 1f, tileLeft, tileTop)

    fun apply(
        view: View,
        density: Float,
        tileLeft: Int,
        tileTop: Int,
        tileW: Int,
        tileH: Int,
        opacity: Float,
        extraScale: Float,
        immediate: Boolean
    ) {
        val p = placement(tileLeft, tileTop, tileW, tileH)
        val finalScale = extraScale.coerceIn(0.05f, 2f)
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.width = p.naturalW
        lp.height = p.naturalH
        lp.leftMargin = p.left
        lp.topMargin = p.top
        view.layoutParams = lp
        view.pivotX = p.naturalW / 2f
        view.pivotY = p.naturalH / 2f
        view.scaleX = finalScale
        view.scaleY = finalScale
        view.alpha = opacity
        if (view is AppWidgetHostView) {
            view.setPadding(0, 0, 0, 0)
            notifyOptions(view, density, p.naturalW, p.naturalH)
        }
    }

    private fun notifyOptions(
        view: AppWidgetHostView,
        density: Float,
        widthPx: Int,
        heightPx: Int
    ) {
        val dens = density.coerceAtLeast(0.01f)
        val w = (widthPx / dens).toInt().coerceAtLeast(1)
        val h = (heightPx / dens).toInt().coerceAtLeast(1)
        val key = (w.toLong() shl 32) or (h.toLong() and 0xFFFFFFFFL)
        if (lastOptionsPx[view] == key) return
        lastOptionsPx[view] = key
        view.post {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            }
            try {
                AppWidgetManager.getInstance(view.context)
                    .updateAppWidgetOptions(view.appWidgetId, options)
            } catch (_: Exception) { }
        }
    }
}
