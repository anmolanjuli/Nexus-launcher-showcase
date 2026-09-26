package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import kotlin.math.min

/**
 * Host-view clip must match the painted widget chrome. A hardcoded 16dp
 * round-rect makes pills and large corner-radius settings look rectangular.
 */
object NexusWidgetHostChrome {

    fun apply(view: View, appWidgetId: Int) {
        // AppWidgetHostView can carry a non-zero default elevation from its own inflation
        // (system/OEM theme), which reads as a soft native drop shadow around the widget's
        // outer bound — zero it defensively so only the deliberate frosted-glass paint
        // treatment shows.
        view.elevation = 0f
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.outlineAmbientShadowColor = android.graphics.Color.TRANSPARENT
            view.outlineSpotShadowColor = android.graphics.Color.TRANSPARENT
        }
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                if (v.width <= 0 || v.height <= 0) return
                val config = NexusWidgetConfig.read(v.context, appWidgetId)
                val density = v.resources.displayMetrics.density
                NexusWidgetShapeGeometry.outline(
                    outline, config.shapeStyle, 0, 0, v.width, v.height, config.cornerRadius * density,
                )
            }
        }
        view.invalidateOutline()
    }

    fun applyDefaultLauncherClip(view: View) {
        view.elevation = 0f
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.outlineAmbientShadowColor = android.graphics.Color.TRANSPARENT
            view.outlineSpotShadowColor = android.graphics.Color.TRANSPARENT
        }
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                if (v.width <= 0 || v.height <= 0) return
                outline.setRoundRect(
                    0, 0, v.width, v.height,
                    16f * v.resources.displayMetrics.density
                )
            }
        }
        view.invalidateOutline()
    }

    fun findHost(overlay: ViewGroup, appWidgetId: Int): AppWidgetHostView? {
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is AppWidgetHostView && child.appWidgetId == appWidgetId) return child
        }
        return null
    }
}
