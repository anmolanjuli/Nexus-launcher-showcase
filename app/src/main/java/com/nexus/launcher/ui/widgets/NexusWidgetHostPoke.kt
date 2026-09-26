package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.ViewGroup

/**
 * Re-posts the host view's real pixel size as widget options so providers
 * rebuild RemoteViews. Needed after permission grants: same-layout ImageView
 * bitmaps often stay stale until options change (what resize does).
 *
 * Default [nexusOnly] = true: third-party hosts must not be options-poked on every
 * launcher return — that forces a full RemoteViews rebuild and is the main amplifier
 * of home-return flicker and collection view resets (e.g. Calendar showing "Nothing planned").
 */
object NexusWidgetHostPoke {
    fun poke(overlay: ViewGroup, nexusOnly: Boolean = true) {
        val manager = AppWidgetManager.getInstance(overlay.context)
        val density = overlay.resources.displayMetrics.density
        val launcherPackage = overlay.context.packageName
        walk(overlay) { host ->
            if (host.width <= 0 || host.height <= 0) return@walk
            val info = manager.getAppWidgetInfo(host.appWidgetId)
            val isFirstParty = info?.provider?.packageName == launcherPackage
            if (nexusOnly && !isFirstParty) return@walk

            val dpW = (host.width / density).toInt().coerceAtLeast(1)
            val dpH = (host.height / density).toInt().coerceAtLeast(1)
            val options = manager.getAppWidgetOptions(host.appWidgetId) ?: Bundle()
            val oldMinW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val oldMinH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val oldMaxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val oldMaxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            // For non-first-party widgets during a forced full poke, skip if options already match
            if (!isFirstParty && oldMinW == dpW && oldMinH == dpH && oldMaxW == dpW && oldMaxH == dpH) {
                return@walk
            }

            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dpW)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dpH)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dpW)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dpH)
            if (isFirstParty) {
                options.putLong("nexus_host_poke_at", System.currentTimeMillis())
            }
            manager.updateAppWidgetOptions(host.appWidgetId, options)
        }
    }

    /**
     * Tells [host]'s provider its new size ([widthPx] x [heightPx]) when its box is resized in
     * place, e.g. by a rotation. Providers that draw their content as a fixed-size image
     * otherwise keep showing the old image stretched into the new box. Uses the new box size,
     * not the view's current size, which only catches up on the next layout pass.
     */
    fun sendSize(host: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        if (host.appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || widthPx <= 0 || heightPx <= 0) return
        val manager = AppWidgetManager.getInstance(host.context)
        val density = host.resources.displayMetrics.density
        val dpW = (widthPx / density).toInt().coerceAtLeast(1)
        val dpH = (heightPx / density).toInt().coerceAtLeast(1)
        val options = manager.getAppWidgetOptions(host.appWidgetId) ?: Bundle()
        if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) == dpW &&
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) == dpH
        ) return
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dpW)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dpH)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dpW)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dpH)
        manager.updateAppWidgetOptions(host.appWidgetId, options)
    }

    private fun walk(group: ViewGroup, onHost: (AppWidgetHostView) -> Unit) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is AppWidgetHostView -> onHost(child)
                is ViewGroup -> walk(child, onHost)
            }
        }
    }
}

