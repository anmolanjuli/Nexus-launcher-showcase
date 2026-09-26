package com.nexus.launcher.ui

/**
 * Sizes shared by every context menu (app, folder, widget, mosaic) — one source so the menus
 * stay consistent. Compact to match the 15sp [com.nexus.launcher.typography.NexusTypeScale.menuItem].
 */
object ContextMenuMetrics {
    /** Action row height. Menus may go below the 48dp touch-target guideline; 44dp keeps close. */
    const val ROW_HEIGHT_DP = 44f
    const val ROW_PADDING_H_DP = 14f
    const val ICON_DP = 18f
    const val ICON_TEXT_GAP_DP = 10f
    /** Widget / mosaic / folder menu card width. */
    const val MENU_WIDTH_DP = 216f
    /** App menu max width (its header also shows the app name). */
    const val APP_MENU_MAX_WIDTH_DP = 236f
}
