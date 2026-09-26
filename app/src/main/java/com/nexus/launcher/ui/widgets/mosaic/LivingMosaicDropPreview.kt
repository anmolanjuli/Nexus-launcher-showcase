package com.nexus.launcher.ui.widgets.mosaic

import android.view.View
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/** Single visual and haptic acceptance preview for widget drops over a Mosaic. */
object LivingMosaicDropPreview {
    private var active: LivingMosaicView? = null
    private var hovered: LivingMosaicView? = null

    fun updateAtScreenPoint(overlay: WidgetOverlayLayout, screenX: Float, screenY: Float): Boolean {
        val hit = LivingMosaicDropHelper.findMosaicAtScreenPoint(overlay, screenX, screenY)
        hovered = hit?.second
        val target = hit?.takeIf { LivingMosaicDropHelper.canAcceptChild(it.first) }?.second
        updateTarget(target)
        return hit != null
    }

    fun updateForView(overlay: WidgetOverlayLayout, view: View): Boolean {
        val hit = LivingMosaicDropHelper.findMosaicOverlappingView(overlay, view)
        hovered = hit?.second
        val target = hit?.takeIf { LivingMosaicDropHelper.canAcceptChild(it.first) }?.second
        updateTarget(target)
        return hit != null
    }

    private fun updateTarget(target: LivingMosaicView?): Boolean {
        if (target === active) return target != null
        clearActive()
        active = target
        target?.let {
            it.animate().scaleX(1.035f).scaleY(1.035f).translationZ(8f * it.resources.displayMetrics.density)
                .setDuration(140L).start()
            LivingMosaicHaptics.tick(it)
        }
        return target != null
    }

    fun clear() {
        clearActive()
        hovered = null
    }

    private fun clearActive() {
        active?.animate()?.scaleX(1f)?.scaleY(1f)?.translationZ(0f)?.setDuration(140L)?.start()
        active = null
    }

    /** Consume the target recorded during drag before the widget resets its translation. */
    fun consumeHoveredTarget(): Pair<HomeScreenItem, LivingMosaicView>? {
        val target = hovered ?: return null
        val item = target.currentItem() ?: return null
        clear()
        return item to target
    }
}
