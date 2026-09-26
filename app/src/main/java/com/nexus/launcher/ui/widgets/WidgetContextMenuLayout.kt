package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView

/**
 * Positions the standard widget context-menu drawer above or below [widgetRect].
 * (Parity with [com.nexus.launcher.ui.widgets.mosaic.LivingMosaicContextMenuLayout]).
 */
object WidgetContextMenuLayout {

    fun attachDrawer(
        context: Context,
        host: FrameLayout,
        drawerContainer: FrameLayout,
        drawerContent: View,
        widgetRect: Rect,
        density: Float
    ): Boolean {
        val lp = FrameLayout.LayoutParams((com.nexus.launcher.ui.ContextMenuMetrics.MENU_WIDTH_DP * density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT)
        val screenHeight = context.resources.displayMetrics.heightPixels
        val statusBarHeight = (24f * density).toInt()
        val estimatedMenuHeight = (320f * density).toInt()
        val availableBelow = screenHeight - widgetRect.bottom - (16f * density).toInt()
        val availableAbove = widgetRect.top - statusBarHeight - (16f * density).toInt()
        val beside = SideMenuPlacement.placeBeside(
            context, widgetRect, lp.width, availableAbove, availableBelow, estimatedMenuHeight, density
        )
        val isNearBottom = beside == null &&
            (availableBelow < estimatedMenuHeight || availableBelow < availableAbove)

        if (beside != null) {
            lp.height = beside.height
            lp.topMargin = beside.top
        } else if (isNearBottom) {
            val maxH = availableAbove.coerceAtLeast((120f * density).toInt())
            lp.height = maxH
            lp.topMargin = (widgetRect.top - maxH - (8f * density).toInt()).coerceAtLeast(statusBarHeight)
        } else {
            val maxH = availableBelow.coerceAtLeast((120f * density).toInt())
            lp.height = maxH
            lp.topMargin = widgetRect.bottom + (16f * density).toInt()
        }
        val screenWidth = context.resources.displayMetrics.widthPixels
        lp.leftMargin = beside?.left ?: (widgetRect.centerX() - (com.nexus.launcher.ui.ContextMenuMetrics.MENU_WIDTH_DP / 2f * density).toInt()).coerceIn(
            (16f * density).toInt(),
            screenWidth - (com.nexus.launcher.ui.ContextMenuMetrics.MENU_WIDTH_DP * density).toInt() - (16f * density).toInt()
        )
        drawerContainer.layoutParams = lp
        drawerContainer.clipChildren = true
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { if (isNearBottom) it.gravity = Gravity.BOTTOM }
            addView(drawerContent)
        }
        drawerContainer.addView(scrollView)
        host.addView(drawerContainer)
        return isNearBottom
    }

    fun reposition(
        context: Context,
        drawerContainer: FrameLayout,
        newRect: Rect,
        density: Float
    ): Boolean {
        val lp = drawerContainer.layoutParams as? FrameLayout.LayoutParams ?: return false
        lp.gravity = Gravity.TOP or Gravity.LEFT
        val screenHeight = context.resources.displayMetrics.heightPixels
        val statusBarHeight = (24f * density).toInt()
        val estimatedMenuHeight = (320f * density).toInt()
        val availableBelow = screenHeight - newRect.bottom - (16f * density).toInt()
        val availableAbove = newRect.top - statusBarHeight - (16f * density).toInt()
        val beside = SideMenuPlacement.placeBeside(
            context, newRect, lp.width, availableAbove, availableBelow, estimatedMenuHeight, density
        )
        val isNearBottom = beside == null &&
            (availableBelow < estimatedMenuHeight || availableBelow < availableAbove)

        lp.topMargin = if (beside != null) {
            beside.top
        } else if (isNearBottom) {
            (newRect.top - (drawerContainer.height + 16f * density)).toInt()
        } else {
            newRect.bottom + (16f * density).toInt()
        }
        val screenWidth = context.resources.displayMetrics.widthPixels
        if (beside != null) lp.height = beside.height
        lp.leftMargin = beside?.left ?: (newRect.centerX() - (com.nexus.launcher.ui.ContextMenuMetrics.MENU_WIDTH_DP / 2f * density).toInt()).coerceIn(
            (16f * density).toInt(),
            screenWidth - (com.nexus.launcher.ui.ContextMenuMetrics.MENU_WIDTH_DP * density).toInt() - (16f * density).toInt()
        )
        drawerContainer.layoutParams = lp
        return isNearBottom
    }
}
