package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.immersive.ImmersiveStatus

/**
 * How much of the home grid's vertical padding can actually be used.
 *
 * A negative padding is how the grid is pulled tighter, and it stays — except that it may not
 * pull the first row up into the band kept clear at the top for the status row or the camera.
 * When it did, every item centred on those cells was drawn under the row, the clamp pushed the
 * widgets back down, and widget and cell no longer agreed: two boxes sharing one space.
 *
 * Only landscape usually feels it. In portrait the inset is far larger than the reserve, so
 * there is room for a negative padding to work in.
 */
object HomeGridPadding {

    fun effective(setting: Float, topInset: Int, density: Float): Float {
        val reserved = ImmersiveStatus.reservedTopPx
        val floorDp = if (density > 0f) (reserved - topInset) / density else 0f
        return maxOf(setting, floorDp)
    }
}
