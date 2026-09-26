package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Canvas
import android.widget.FrameLayout

/**
 * The frame that holds a Living Mosaic's cells. Each cell clips to its outline, so in Neumorphic
 * this frame draws the cells' raised shadows (see [MosaicRaisedShadow]) — all of them before any
 * cell: a shadow drawn between two cells would spill over the one before it and hide the gap that
 * divides them.
 */
internal class MosaicContentFrame(context: Context) : FrameLayout(context) {

    override fun dispatchDraw(canvas: Canvas) {
        for (i in 0 until childCount) MosaicRaisedShadow.drawBehind(canvas, getChildAt(i))
        super.dispatchDraw(canvas)
    }
}
