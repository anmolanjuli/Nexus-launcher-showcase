package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.model.LauncherState

/**
 * Drawer slide fraction: 0 = home, 1 = drawer fully open.
 *
 * Drives dock fading, floating chrome visibility, window blur, and drawer background opacity.
 * Evaluates strictly from the physical translation and display height once measured.
 */
object DrawerProgress {
    fun of(view: LauncherCanvasView): Float {
        if (view.viewHeight <= 0) return if (view.uiState == LauncherState.DRAWER) 1f else 0f
        val ty = view.drawerTranslationY
        if (!ty.isFinite() || ty < 0f) {
            return if (view.uiState == LauncherState.DRAWER) 1f else 0f
        }
        return (1f - ty / view.viewHeight.toFloat()).coerceIn(0f, 1f)
    }
}
