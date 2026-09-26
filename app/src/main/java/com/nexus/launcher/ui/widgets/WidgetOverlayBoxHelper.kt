package com.nexus.launcher.ui.widgets

import android.view.View
import com.nexus.launcher.ui.widgets.appbox.AppBoxView
import com.nexus.launcher.ui.widgets.liveapp.LiveAppView
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView

/**
 * Helper for box lookup and hover acceptance states on [WidgetOverlayLayout].
 */
internal object WidgetOverlayBoxHelper {

    fun setBoxAcceptanceHover(overlay: WidgetOverlayLayout, boxId: Int?, active: Boolean) {
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is ShortcutBoxView) {
                val matches = boxId == null || child.currentItem()?.id == boxId
                child.setAcceptanceHover(if (matches) active else false)
            } else if (child is AppBoxView) {
                val matches = boxId == null || child.currentItem()?.id == boxId
                child.setAcceptanceHover(if (matches) active else false)
            }
        }
    }

    fun findBox(overlay: WidgetOverlayLayout, id: Int): View? {
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is ShortcutBoxView && child.currentItem()?.id == id) {
                return child
            }
            if (child is AppBoxView && child.currentItem()?.id == id) {
                return child
            }
            if (child is LiveAppView && child.currentItem()?.id == id) {
                return child
            }
        }
        return null
    }
}
