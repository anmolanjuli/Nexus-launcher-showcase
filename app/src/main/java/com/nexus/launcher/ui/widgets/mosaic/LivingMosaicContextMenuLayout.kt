package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.ui.widgets.WidgetContextMenuLayout

/**
 * Positions the mosaic context-menu drawer relative to [widgetRect] — above, below, or beside
 * it in landscape. The mosaic menu places exactly like the standard widget menu, so this
 * delegates to [WidgetContextMenuLayout] rather than keeping a second copy of the maths.
 */
object LivingMosaicContextMenuLayout {

    fun attachDrawer(
        context: Context,
        host: FrameLayout,
        drawerContainer: FrameLayout,
        drawerContent: View,
        widgetRect: Rect,
        density: Float
    ) {
        WidgetContextMenuLayout.attachDrawer(
            context, host, drawerContainer, drawerContent, widgetRect, density
        )
    }

    fun reposition(
        context: Context,
        drawerContainer: FrameLayout,
        newRect: Rect,
        density: Float
    ) {
        WidgetContextMenuLayout.reposition(context, drawerContainer, newRect, density)
    }
}
